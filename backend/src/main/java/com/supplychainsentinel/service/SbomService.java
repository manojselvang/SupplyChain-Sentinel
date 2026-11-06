package com.supplychainsentinel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.supplychainsentinel.dto.SbomDocumentResponse;
import com.supplychainsentinel.dto.SbomComponentResponse;
import com.supplychainsentinel.dto.SbomIngestionRequest;
import com.supplychainsentinel.dto.SbomIngestionResponse;
import com.supplychainsentinel.entity.*;
import com.supplychainsentinel.exception.AuthException;
import com.supplychainsentinel.exception.ResourceNotFoundException;
import com.supplychainsentinel.mapper.SbomComponentMapper;
import com.supplychainsentinel.mapper.SbomDocumentMapper;
import com.supplychainsentinel.repository.*;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/**
 * SBOM ingestion, storage, component extraction, and read logic.
 *
 * <p>Supports CycloneDX JSON 1.6 and 1.7. Calculates document SHA-256
 * on the server — never trusts a caller-provided hash.  Idempotent:
 * repeated submissions of the same document for the same app+pipeline
 * return the existing record without creating a duplicate.</p>
 */
@Service
public class SbomService {

    private static final Set<String> SUPPORTED_VERSIONS = Set.of("1.6", "1.7");

    private final SbomDocumentRepository sbomDocRepository;
    private final SbomComponentRepository sbomCompRepository;
    private final ApplicationRepository applicationRepository;
    private final PipelineRunRepository pipelineRunRepository;

    public SbomService(
            SbomDocumentRepository sbomDocRepository,
            SbomComponentRepository sbomCompRepository,
            ApplicationRepository applicationRepository,
            PipelineRunRepository pipelineRunRepository
    ) {
        this.sbomDocRepository = sbomDocRepository;
        this.sbomCompRepository = sbomCompRepository;
        this.applicationRepository = applicationRepository;
        this.pipelineRunRepository = pipelineRunRepository;
    }

    // ── Ingestion ─────────────────────────────────────────────

    @Transactional
    public SbomIngestionResponse ingestSbom(SbomIngestionRequest request) {
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
            if (!pipelineRun.getApplication().getId().equals(app.getId())) {
                throw new AuthException(
                        "Pipeline run does not belong to application " + request.applicationKey(),
                        HttpStatus.BAD_REQUEST);
            }
        }

        JsonNode sbom = request.sbom();

        // Validate CycloneDX format
        String bomFormat = sbom.path("bomFormat").asText("");
        if (!"CycloneDX".equalsIgnoreCase(bomFormat)) {
            throw new IllegalArgumentException("Unsupported SBOM format: expected bomFormat=CycloneDX, got: " + bomFormat);
        }

        String specVersion = sbom.path("specVersion").asText("");
        if (!SUPPORTED_VERSIONS.contains(specVersion)) {
            throw new IllegalArgumentException(
                    "Unsupported CycloneDX spec version: " + specVersion + ". Supported: " + SUPPORTED_VERSIONS);
        }

        // Calculate document SHA-256 (never trust caller)
        String rawJson = sbom.toString();
        String documentSha256 = sha256Hex(rawJson);

        // Idempotency check: same app + pipeline + document hash
        UUID pipelineRunId = pipelineRun != null ? pipelineRun.getId() : null;
        Optional<SbomDocument> existing = sbomDocRepository
                .findByApplicationIdAndPipelineRunIdAndDocumentSha256(
                        app.getId(), pipelineRunId, documentSha256);

        if (existing.isPresent()) {
            // Already ingested — return existing record
            SbomDocument doc = existing.get();
            return new SbomIngestionResponse(
                    doc.getId(), app.getId(), app.getName(),
                    pipelineRunId, doc.getArtifactDigest(),
                    doc.getFormat(), doc.getSpecVersion(),
                    doc.getComponentCount(), doc.getGeneratedAt(),
                    doc.getIngestedAt(), false);
        }

        // Create new SBOM document record
        SbomDocument doc = new SbomDocument();
        doc.setApplication(app);
        doc.setPipelineRun(pipelineRun);
        doc.setArtifactDigest(request.artifactDigest());
        doc.setFormat("CYCLONEDX_JSON");
        doc.setSpecVersion(specVersion);
        doc.setSerialNumber(sbom.path("serialNumber").asText(null));
        doc.setDocumentVersion(sbom.path("version").asInt(1));
        doc.setDocumentSha256(documentSha256);
        doc.setIngestedAt(Instant.now());
        doc.setRawDocument(rawJson);

        // Extract generated timestamp from metadata if present
        JsonNode metadata = sbom.path("metadata");
        if (!metadata.isMissingNode()) {
            String ts = metadata.path("timestamp").asText(null);
            if (ts != null) {
                try { doc.setGeneratedAt(Instant.parse(ts)); }
                catch (Exception ignored) { }
            }
        }

        // Extract components
        JsonNode componentsNode = sbom.path("components");
        List<SbomComponent> components = new ArrayList<>();
        if (componentsNode.isArray()) {
            Set<String> seen = new HashSet<>(); // dedup within same SBOM
            for (JsonNode compNode : componentsNode) {
                String name = compNode.path("name").asText("");
                String version = compNode.path("version").asText("");
                String purl = compNode.path("purl").asText(null);
                String dedupKey = name + "|" + version + "|" + (purl != null ? purl : "");
                if (name.isBlank() || !seen.add(dedupKey)) continue;

                SbomComponent comp = new SbomComponent();
                comp.setSbomId(doc.getId()); // will be set after save, but we set it now
                comp.setBomRef(compNode.path("bom-ref").asText(null));
                comp.setComponentType(compNode.path("type").asText(null));
                comp.setGroupName(compNode.path("group").asText(null));
                comp.setName(truncate(name, 500));
                comp.setVersion(truncate(version, 100));
                comp.setPurl(truncate(purl, 500));
                comp.setCpe(truncate(compNode.path("cpe").asText(null), 500));
                comp.setScope(compNode.path("scope").asText(null));
                comp.setSupplier(truncate(extractSupplier(compNode), 500));

                // Extract licenses as JSON string
                JsonNode licensesNode = compNode.path("licenses");
                if (!licensesNode.isMissingNode() && !licensesNode.isNull()) {
                    comp.setLicenses(licensesNode.toString());
                }

                // Extract hashes
                JsonNode hashesNode = compNode.path("hashes");
                if (!hashesNode.isMissingNode() && !hashesNode.isNull()) {
                    comp.setHashes(hashesNode.toString());
                }

                components.add(comp);
            }
        }

        doc.setComponentCount(components.size());
        sbomDocRepository.save(doc);
        sbomDocRepository.flush();

        // Save components with the correct sbom_id
        for (SbomComponent comp : components) {
            comp.setSbomId(doc.getId());
            sbomCompRepository.save(comp);
        }
        sbomCompRepository.flush();

        return new SbomIngestionResponse(
                doc.getId(), app.getId(), app.getName(),
                pipelineRunId, doc.getArtifactDigest(),
                doc.getFormat(), doc.getSpecVersion(),
                doc.getComponentCount(), doc.getGeneratedAt(),
                doc.getIngestedAt(), true);
    }

    // ── Read: list SBOMs ──────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<SbomDocumentResponse> listSboms(
            UUID applicationId, String artifactDigest, Pageable pageable) {

        Specification<SbomDocument> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (applicationId != null)
                predicates.add(cb.equal(root.get("application").get("id"), applicationId));
            if (artifactDigest != null)
                predicates.add(cb.equal(root.get("artifactDigest"), artifactDigest));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return sbomDocRepository.findAll(spec, pageable).map(SbomDocumentMapper::toDto);
    }

    // ── Read: single SBOM ────────────────────────────────────

    @Transactional(readOnly = true)
    public SbomDocumentResponse getSbom(UUID id) {
        SbomDocument doc = sbomDocRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SBOM not found: " + id));
        return SbomDocumentMapper.toDto(doc);
    }

    // ── Read: components ──────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<SbomComponentResponse> getSbomComponents(
            UUID sbomId, String nameSearch, String purlSearch, Pageable pageable) {

        // Verify SBOM exists
        if (!sbomDocRepository.existsById(sbomId)) {
            throw new ResourceNotFoundException("SBOM not found: " + sbomId);
        }

        Specification<SbomComponent> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("sbomId"), sbomId));
            if (nameSearch != null && !nameSearch.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")),
                        "%" + nameSearch.toLowerCase() + "%"));
            }
            if (purlSearch != null && !purlSearch.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("purl")),
                        "%" + purlSearch.toLowerCase() + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return sbomCompRepository.findAll(spec, pageable).map(SbomComponentMapper::toDto);
    }

    // ── Read: application SBOMs ────────────────────────────────

    @Transactional(readOnly = true)
    public Page<SbomDocumentResponse> getApplicationSboms(UUID applicationId, Pageable pageable) {
        Specification<SbomDocument> spec = (root, query, cb) ->
                cb.equal(root.get("application").get("id"), applicationId);
        return sbomDocRepository.findAll(spec, pageable).map(SbomDocumentMapper::toDto);
    }

    // ── Helpers ───────────────────────────────────────────────

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private static String extractSupplier(JsonNode compNode) {
        JsonNode supplier = compNode.path("supplier");
        if (supplier.isMissingNode() || supplier.isNull()) return null;
        return supplier.path("name").asText(null);
    }
}
