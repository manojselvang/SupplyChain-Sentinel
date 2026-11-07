package com.supplychainsentinel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supplychainsentinel.entity.*;
import com.supplychainsentinel.repository.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Deterministic release risk scoring — SCS-RELEASE-POLICY-V1.
 *
 * No AI, no LLMs.  All scores are integer arithmetic with fixed weights.
 * Results are fully explainable via the scoreBreakdown and hardBlockReasons.
 */
@Component
public class ReleaseRiskPolicy {

    public static final String POLICY_VERSION = "SCS-RELEASE-POLICY-V1";
    static final int SCORE_CAP = 100;

    private final SecurityFindingRepository findingRepo;
    private final SbomDocumentRepository sbomRepo;
    private final ArtifactVerificationRepository verificationRepo;
    private final SecurityScanRepository scanRepo;
    private final ObjectMapper objectMapper;

    public ReleaseRiskPolicy(
            SecurityFindingRepository findingRepo,
            SbomDocumentRepository sbomRepo,
            ArtifactVerificationRepository verificationRepo,
            SecurityScanRepository scanRepo,
            ObjectMapper objectMapper) {
        this.findingRepo = findingRepo;
        this.sbomRepo = sbomRepo;
        this.verificationRepo = verificationRepo;
        this.scanRepo = scanRepo;
        this.objectMapper = objectMapper;
    }

    // ── Public entry point ────────────────────────────────────

    public AssessmentResult evaluate(InventoryApplication app, PipelineRun run) {
        UUID appId = app.getId();
        UUID runId = run.getId();
        String digest = run.getArtifactDigest();

        List<String> hardBlocks = new ArrayList<>();
        Map<String, Object> breakdown = new LinkedHashMap<>();
        Map<String, Object> evidence = new LinkedHashMap<>();

        // ── 1. Hard-block gates ────────────────────────────────

        // Gate 1: pipeline must be SUCCESS
        if (!"SUCCESS".equals(run.getStatus())) {
            hardBlocks.add("PIPELINE_NOT_SUCCESSFUL: status=" + run.getStatus());
        }

        // Gate 2: artifact digest must exist
        boolean hasDigest = digest != null && !digest.isBlank();
        if (!hasDigest) {
            hardBlocks.add("MISSING_ARTIFACT_DIGEST");
        }
        evidence.put("artifactDigest", digest);
        evidence.put("pipelineStatus", run.getStatus());

        // Gate 3: SBOM must exist for this exact run+digest
        boolean hasSbom = false;
        int sbomComponents = 0;
        if (hasDigest) {
            Specification<SbomDocument> sbomSpec = (root, q, cb) -> cb.and(
                    cb.equal(root.get("application").get("id"), appId),
                    cb.equal(root.get("pipelineRun").get("id"), runId),
                    cb.equal(root.get("artifactDigest"), digest));
            var sbomList = sbomRepo.findAll(sbomSpec);
            hasSbom = !sbomList.isEmpty();
            if (hasSbom) sbomComponents = sbomList.get(0).getComponentCount();
            if (!hasSbom) hardBlocks.add("NO_SBOM_FOR_ARTIFACT");
        }
        evidence.put("hasSbom", hasSbom);
        evidence.put("sbomComponentCount", sbomComponents);

        // Gate 4: successful Cosign verification must exist for this digest
        boolean hasVerification = false;
        if (hasDigest) {
            Specification<ArtifactVerification> avSpec = (root, q, cb) -> cb.and(
                    cb.equal(root.get("application").get("id"), appId),
                    cb.equal(root.get("artifactDigest"), digest),
                    cb.equal(root.get("verificationStatus"), "VERIFIED"));
            hasVerification = !verificationRepo.findAll(avSpec).isEmpty();
            if (!hasVerification) hardBlocks.add("NO_VERIFIED_COSIGN_FOR_DIGEST");
        }
        evidence.put("hasCosignVerification", hasVerification);

        // Gate 5: security scan must exist for this pipeline
        Specification<SecurityScan> scanSpec = (root, q, cb) -> cb.and(
                cb.equal(root.get("application").get("id"), appId),
                cb.equal(root.get("pipelineRun").get("id"), runId));
        boolean hasScan = !scanRepo.findAll(scanSpec).isEmpty();
        if (!hasScan) hardBlocks.add("NO_SECURITY_SCAN_FOR_PIPELINE");
        evidence.put("hasSecurityScan", hasScan);

        // ── 2. Gather active findings ──────────────────────────
        Specification<SecurityFinding> findingSpec = (root, q, cb) -> {
            List<jakarta.persistence.criteria.Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("application").get("id"), appId));
            p.add(root.get("status").in("OPEN", "ACKNOWLEDGED"));
            return cb.and(p.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        List<SecurityFinding> activeFindings = findingRepo.findAll(findingSpec);

        // Gate 6: any CRITICAL open/acknowledged finding → hard block
        long critCount = activeFindings.stream()
                .filter(f -> "CRITICAL".equals(f.getSeverity())).count();
        if (critCount > 0) {
            hardBlocks.add("ACTIVE_CRITICAL_VULNERABILITIES: count=" + critCount);
        }

        // Count accepted findings (reported separately, not blocked)
        long acceptedCount = findingRepo.findAll((root, q, cb) ->
                cb.and(cb.equal(root.get("application").get("id"), appId),
                        cb.equal(root.get("status"), "ACCEPTED"))).size();

        // ── 3. Score calculation ────────────────────────────────

        // Application criticality
        int critPoints = switch (app.getCriticality()) {
            case "CRITICAL" -> 20;
            case "HIGH" -> 15;
            case "MEDIUM" -> 10;
            default -> 5;
        };
        breakdown.put("criticalityPoints", critPoints);
        breakdown.put("applicationCriticality", app.getCriticality());

        // Internet-facing bonus
        int exposurePoints = app.isInternetFacing() ? 10 : 0;
        breakdown.put("internetFacingPoints", exposurePoints);

        // Vulnerability points from active findings
        int vulnPoints = 0;
        Map<String, Integer> sevCounts = new LinkedHashMap<>();
        Map<String, Integer> sevWeights = Map.of(
                "CRITICAL", 30, "HIGH", 15, "MEDIUM", 5, "LOW", 1, "INFO", 0, "UNKNOWN", 0);
        for (SecurityFinding f : activeFindings) {
            int w = sevWeights.getOrDefault(f.getSeverity(), 0);
            vulnPoints += w;
            sevCounts.merge(f.getSeverity(), 1, Integer::sum);
        }
        breakdown.put("vulnerabilityPoints", vulnPoints);
        breakdown.put("findingsBySeverity", sevCounts);

        int totalScore = Math.min(critPoints + exposurePoints + vulnPoints, SCORE_CAP);
        breakdown.put("totalScore", totalScore);

        evidence.put("activeFindingsCount", activeFindings.size());
        evidence.put("acceptedFindingsCount", (int) acceptedCount);
        evidence.put("criticalFindings", (int) critCount);

        // ── 4. Decision ────────────────────────────────────────
        String decision;
        if (!hardBlocks.isEmpty()) {
            decision = "BLOCK";
        } else if (totalScore >= 50) {
            decision = "BLOCK";
        } else if (totalScore >= 25) {
            decision = "WARN";
        } else {
            decision = "PASS";
        }

        // Initial assessment status
        String initialStatus = switch (decision) {
            case "BLOCK" -> "BLOCKED";
            case "PASS" -> "PENDING_APPROVAL";
            default -> "PENDING_APPROVAL";
        };

        try {
            return new AssessmentResult(
                    totalScore, decision, initialStatus, POLICY_VERSION,
                    objectMapper.writeValueAsString(hardBlocks),
                    objectMapper.writeValueAsString(breakdown),
                    objectMapper.writeValueAsString(evidence));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialise assessment result", e);
        }
    }

    // ── Result record ─────────────────────────────────────────

    public record AssessmentResult(
            int riskScore,
            String decision,
            String initialStatus,
            String policyVersion,
            String hardBlockReasonsJson,
            String scoreBreakdownJson,
            String evidenceSnapshotJson
    ) {}
}
