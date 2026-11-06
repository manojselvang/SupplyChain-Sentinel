package com.supplychainsentinel.service;

import com.supplychainsentinel.dto.*;
import com.supplychainsentinel.entity.*;
import com.supplychainsentinel.exception.AuthException;
import com.supplychainsentinel.exception.ResourceNotFoundException;
import com.supplychainsentinel.mapper.SecurityFindingMapper;
import com.supplychainsentinel.repository.*;
import com.supplychainsentinel.service.scanner.NormalizedFinding;
import com.supplychainsentinel.service.scanner.ScannerParser;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Security finding ingestion, deduplication, read and status-update logic.
 *
 * <p>Deduplication strategy: each finding is identified by (applicationId, fingerprint).
 * On repeated ingestion:
 * <ul>
 *   <li>lastSeen is updated.</li>
 *   <li>fixedVersion, affectedLocation, remediationUrl, scanner metadata are updated.</li>
 *   <li>firstSeen is preserved.</li>
 *   <li>status is preserved (triage decisions survive re-scans).</li>
 * </ul>
 */
@Service
public class SecurityFindingService {

    private final SecurityFindingRepository findingRepository;
    private final SecurityScanRepository scanRepository;
    private final SecurityScanFindingRepository scanFindingRepository;
    private final ApplicationRepository applicationRepository;
    private final PipelineRunRepository pipelineRunRepository;
    private final Map<String, ScannerParser> parsers;

    public SecurityFindingService(
            SecurityFindingRepository findingRepository,
            SecurityScanRepository scanRepository,
            SecurityScanFindingRepository scanFindingRepository,
            ApplicationRepository applicationRepository,
            PipelineRunRepository pipelineRunRepository,
            List<ScannerParser> parserList
    ) {
        this.findingRepository = findingRepository;
        this.scanRepository = scanRepository;
        this.scanFindingRepository = scanFindingRepository;
        this.applicationRepository = applicationRepository;
        this.pipelineRunRepository = pipelineRunRepository;
        this.parsers = new HashMap<>();
        parserList.forEach(p -> this.parsers.put(p.reportFormat(), p));
    }

    // ── Ingestion ─────────────────────────────────────────────

    @Transactional
    public SecurityScanResponse ingestScan(SecurityScanIngestionRequest request) {
        // Resolve application
        InventoryApplication app = applicationRepository
                .findByApplicationKey(request.applicationKey())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Unknown application key: " + request.applicationKey()));

        // Optionally resolve pipeline run
        PipelineRun pipelineRun = null;
        if (request.pipelineRunId() != null) {
            pipelineRun = pipelineRunRepository.findById(request.pipelineRunId())
                    .orElseThrow(() -> new AuthException(
                            "Unknown pipeline run ID: " + request.pipelineRunId(),
                            HttpStatus.BAD_REQUEST));
            // Ensure the pipeline run belongs to the same application
            if (!pipelineRun.getApplication().getId().equals(app.getId())) {
                throw new AuthException(
                        "Pipeline run does not belong to application " + request.applicationKey(),
                        HttpStatus.BAD_REQUEST);
            }
        }

        // Look up parser
        ScannerParser parser = parsers.get(request.reportFormat());
        if (parser == null) {
            throw new IllegalArgumentException("Unsupported reportFormat: " + request.reportFormat());
        }

        // Parse
        List<NormalizedFinding> normalized = parser.parse(
                request.scanner(), request.report(), app.getId());

        // Persist scan record
        SecurityScan scan = new SecurityScan();
        scan.setApplication(app);
        scan.setPipelineRun(pipelineRun);
        scan.setScanner(request.scanner());
        scan.setReportFormat(request.reportFormat());
        scan.setArtifactDigest(request.artifactDigest());
        scan.setScanTimestamp(request.scanTimestamp());
        scanRepository.save(scan);

        // Upsert findings
        int newCount = 0;
        int updatedCount = 0;
        List<SecurityFindingResponse> responses = new ArrayList<>();

        for (NormalizedFinding nf : normalized) {
            Optional<SecurityFinding> existing = findingRepository
                    .findByApplicationIdAndFingerprint(app.getId(), nf.fingerprint());

            SecurityFinding finding;
            if (existing.isPresent()) {
                // Update last seen and mutable metadata; preserve firstSeen and status
                finding = existing.get();
                finding.setLastSeen(request.scanTimestamp());
                finding.setScanner(nf.scanner());
                if (nf.fixedVersion() != null) finding.setFixedVersion(nf.fixedVersion());
                if (nf.remediationUrl() != null) finding.setRemediationUrl(nf.remediationUrl());
                if (nf.affectedLocation() != null) finding.setAffectedLocation(nf.affectedLocation());
                if (pipelineRun != null) finding.setPipelineRun(pipelineRun);
                updatedCount++;
            } else {
                finding = new SecurityFinding();
                finding.setApplication(app);
                finding.setPipelineRun(pipelineRun);
                finding.setFingerprint(nf.fingerprint());
                finding.setScanner(nf.scanner());
                finding.setVulnerabilityId(nf.vulnerabilityId());
                finding.setRuleId(nf.ruleId());
                finding.setTitle(nf.title());
                finding.setDescription(nf.description());
                finding.setSeverity(nf.severity());
                finding.setAffectedPackage(nf.affectedPackage());
                finding.setInstalledVersion(nf.installedVersion());
                finding.setFixedVersion(nf.fixedVersion());
                finding.setAffectedLocation(nf.affectedLocation());
                finding.setRemediationUrl(nf.remediationUrl());
                finding.setStatus("OPEN");
                finding.setFirstSeen(request.scanTimestamp());
                finding.setLastSeen(request.scanTimestamp());
                newCount++;
            }
            findingRepository.save(finding);

            // Create scan-to-finding junction record for historical traceability.
            // This links every processed finding (new or deduplicated) to its scan.
            if (!scanFindingRepository.existsByScanIdAndFindingId(scan.getId(), finding.getId())) {
                SecurityScanFinding junction = new SecurityScanFinding();
                junction.setScanId(scan.getId());
                junction.setFindingId(finding.getId());
                scanFindingRepository.save(junction);
            }

            responses.add(SecurityFindingMapper.toDto(finding));
        }
        findingRepository.flush();

        return new SecurityScanResponse(
                scan.getId(), app.getId(), app.getName(),
                pipelineRun != null ? pipelineRun.getId() : null,
                request.scanner(), request.reportFormat(),
                request.scanTimestamp(),
                normalized.size(), newCount, updatedCount,
                responses
        );
    }

    // ── Read: list ────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<SecurityFindingResponse> listFindings(
            UUID applicationId, String severity, String status,
            String scanner, String vulnerabilityId, String search,
            Pageable pageable) {

        Specification<SecurityFinding> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (applicationId != null) predicates.add(cb.equal(root.get("application").get("id"), applicationId));
            if (severity != null) predicates.add(cb.equal(root.get("severity"), severity));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (scanner != null) predicates.add(cb.equal(root.get("scanner"), scanner));
            if (vulnerabilityId != null) predicates.add(cb.like(cb.lower(root.get("vulnerabilityId")), "%" + vulnerabilityId.toLowerCase() + "%"));
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("vulnerabilityId")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return findingRepository.findAll(spec, pageable).map(SecurityFindingMapper::toDto);
    }

    // ── Read: single ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public SecurityFindingResponse getFinding(UUID id) {
        SecurityFinding f = findingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Finding not found: " + id));
        return SecurityFindingMapper.toDto(f);
    }

    // ── Read: application findings ────────────────────────────

    @Transactional(readOnly = true)
    public Page<SecurityFindingResponse> getApplicationFindings(UUID applicationId, Pageable pageable) {
        Specification<SecurityFinding> spec = (root, query, cb) ->
                cb.equal(root.get("application").get("id"), applicationId);
        return findingRepository.findAll(spec, pageable).map(SecurityFindingMapper::toDto);
    }

    // ── Status update ─────────────────────────────────────────

    @Transactional
    public SecurityFindingResponse updateStatus(UUID id, UpdateFindingStatusRequest request) {
        SecurityFinding f = findingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Finding not found: " + id));
        f.setStatus(request.status());
        findingRepository.save(f);
        findingRepository.flush();
        return SecurityFindingMapper.toDto(f);
    }
}
