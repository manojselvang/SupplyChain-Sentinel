package com.supplychainsentinel.service;

import com.supplychainsentinel.dto.*;
import com.supplychainsentinel.entity.*;
import com.supplychainsentinel.exception.AuthException;
import com.supplychainsentinel.exception.ResourceNotFoundException;
import com.supplychainsentinel.repository.*;
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
 * Creates and manages release assessments, approvals and audit trail.
 * All risk scoring is delegated to {@link ReleaseRiskPolicy}.
 */
@Service
public class ReleaseAssessmentService {

    private final ReleaseAssessmentRepository assessmentRepo;
    private final ReleaseApprovalRepository approvalRepo;
    private final ApplicationRepository applicationRepository;
    private final PipelineRunRepository pipelineRunRepository;
    private final UserRepository userRepository;
    private final ReleaseRiskPolicy policy;
    private final AuditService auditService;

    public ReleaseAssessmentService(
            ReleaseAssessmentRepository assessmentRepo,
            ReleaseApprovalRepository approvalRepo,
            ApplicationRepository applicationRepository,
            PipelineRunRepository pipelineRunRepository,
            UserRepository userRepository,
            ReleaseRiskPolicy policy,
            AuditService auditService) {
        this.assessmentRepo = assessmentRepo;
        this.approvalRepo = approvalRepo;
        this.applicationRepository = applicationRepository;
        this.pipelineRunRepository = pipelineRunRepository;
        this.userRepository = userRepository;
        this.policy = policy;
        this.auditService = auditService;
    }

    // ── Create assessment ─────────────────────────────────────

    @Transactional
    public ReleaseAssessmentResponse createAssessment(CreateAssessmentRequest req, String requesterUsername) {
        UUID appId = UUID.fromString(req.applicationId());
        UUID runId = UUID.fromString(req.pipelineRunId());

        InventoryApplication app = applicationRepository.findById(appId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + appId));
        PipelineRun run = pipelineRunRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Pipeline run not found: " + runId));

        if (!run.getApplication().getId().equals(appId)) {
            throw new AuthException("Pipeline run does not belong to this application", HttpStatus.BAD_REQUEST);
        }

        // Run deterministic scoring
        ReleaseRiskPolicy.AssessmentResult result = policy.evaluate(app, run);

        ReleaseAssessment assessment = new ReleaseAssessment();
        assessment.setApplication(app);
        assessment.setPipelineRun(run);
        assessment.setArtifactDigest(run.getArtifactDigest() != null ? run.getArtifactDigest() : "");
        assessment.setRiskScore(result.riskScore());
        assessment.setDecision(result.decision());
        assessment.setPolicyVersion(result.policyVersion());
        assessment.setAssessmentStatus(result.initialStatus());
        assessment.setHardBlockReasons(result.hardBlockReasonsJson());
        assessment.setScoreBreakdown(result.scoreBreakdownJson());
        assessment.setEvidenceSnapshot(result.evidenceSnapshotJson());
        assessment.setRequestedBy(requesterUsername);
        assessment.setAssessedAt(Instant.now());
        assessmentRepo.save(assessment);
        assessmentRepo.flush();

        // Audit
        User requester = userRepository.findByUsername(requesterUsername).orElse(null);
        auditService.record(
                requester != null ? requester.getId() : null,
                "USER",
                "ASSESSMENT_CREATED",
                "release_assessment",
                assessment.getId(),
                Map.of("decision", result.decision(), "riskScore", result.riskScore(),
                        "pipelineRunId", runId.toString()));

        return toResponse(assessment);
    }

    // ── CI release gate ───────────────────────────────────────

    @Transactional
    public CiReleaseGateResponse evaluateReleaseGate(String applicationKey, UUID pipelineRunId) {
        InventoryApplication app = applicationRepository.findByApplicationKey(applicationKey)
                .orElseThrow(() -> new ResourceNotFoundException("Unknown application key: " + applicationKey));
        PipelineRun run = pipelineRunRepository.findById(pipelineRunId)
                .orElseThrow(() -> new ResourceNotFoundException("Pipeline run not found: " + pipelineRunId));
        if (!run.getApplication().getId().equals(app.getId())) {
            throw new AuthException("Pipeline run does not belong to application " + applicationKey, HttpStatus.BAD_REQUEST);
        }

        ReleaseRiskPolicy.AssessmentResult result = policy.evaluate(app, run);

        // Persist assessment for CI gate (actor = CI integration)
        ReleaseAssessment assessment = new ReleaseAssessment();
        assessment.setApplication(app);
        assessment.setPipelineRun(run);
        assessment.setArtifactDigest(run.getArtifactDigest() != null ? run.getArtifactDigest() : "");
        assessment.setRiskScore(result.riskScore());
        assessment.setDecision(result.decision());
        assessment.setPolicyVersion(result.policyVersion());
        assessment.setAssessmentStatus(result.initialStatus());
        assessment.setHardBlockReasons(result.hardBlockReasonsJson());
        assessment.setScoreBreakdown(result.scoreBreakdownJson());
        assessment.setEvidenceSnapshot(result.evidenceSnapshotJson());
        assessment.setRequestedBy("CI_INTEGRATION");
        assessment.setAssessedAt(Instant.now());
        assessmentRepo.save(assessment);
        assessmentRepo.flush();

        auditService.record(null, "CI_INTEGRATION", "CI_GATE_EVALUATED",
                "release_assessment", assessment.getId(),
                Map.of("decision", result.decision(), "riskScore", result.riskScore(),
                        "pipelineRunId", pipelineRunId.toString()));

        List<String> blocks = parseStringList(result.hardBlockReasonsJson());
        String summary = "Risk score: " + result.riskScore() + " → " + result.decision() +
                (blocks.isEmpty() ? "" : " | Blocked: " + String.join(", ", blocks));

        return new CiReleaseGateResponse(
                assessment.getId(), result.decision(), result.riskScore(), blocks, summary);
    }

    // ── Read ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ReleaseAssessmentResponse> listAssessments(
            UUID applicationId, String decision, String status, Pageable pageable) {
        Specification<ReleaseAssessment> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (applicationId != null) p.add(cb.equal(root.get("application").get("id"), applicationId));
            if (decision != null) p.add(cb.equal(root.get("decision"), decision));
            if (status != null) p.add(cb.equal(root.get("assessmentStatus"), status));
            return cb.and(p.toArray(new Predicate[0]));
        };
        return assessmentRepo.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ReleaseAssessmentResponse getAssessment(UUID id) {
        ReleaseAssessment a = assessmentRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assessment not found: " + id));
        return toResponse(a);
    }

    @Transactional(readOnly = true)
    public Page<ReleaseAssessmentResponse> getApplicationAssessments(UUID appId, Pageable pageable) {
        Specification<ReleaseAssessment> spec = (root, q, cb) ->
                cb.equal(root.get("application").get("id"), appId);
        return assessmentRepo.findAll(spec, pageable).map(this::toResponse);
    }

    // ── Approval ──────────────────────────────────────────────

    @Transactional
    public ApprovalResponse submitApproval(UUID assessmentId, ApprovalRequest req, String approverUsername) {
        ReleaseAssessment a = assessmentRepo.findById(assessmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assessment not found: " + assessmentId));

        // BLOCK cannot be approved
        if ("BLOCK".equals(a.getDecision())) {
            throw new AuthException("BLOCK assessments cannot be approved — create a new assessment after resolving blockers", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        // Already completed
        if ("READY_FOR_DEPLOYMENT".equals(a.getAssessmentStatus()) || "REJECTED".equals(a.getAssessmentStatus())) {
            throw new AuthException("Assessment is already completed", HttpStatus.CONFLICT);
        }

        // Requester cannot approve their own assessment
        if (approverUsername.equals(a.getRequestedBy())) {
            throw new AuthException("You cannot approve your own release assessment", HttpStatus.FORBIDDEN);
        }

        // WARN requires justification
        if ("WARN".equals(a.getDecision()) && "APPROVED".equals(req.decision()) &&
                (req.justification() == null || req.justification().isBlank())) {
            throw new AuthException("Justification is required when approving a WARN assessment", HttpStatus.BAD_REQUEST);
        }

        // Prevent duplicate from same approver
        User approver = userRepository.findByUsername(approverUsername).orElse(null);
        UUID approverId = approver != null ? approver.getId() : null;
        if (approverId != null && approvalRepo.existsByAssessmentIdAndApproverId(assessmentId, approverId)) {
            throw new AuthException("You have already submitted a decision for this assessment", HttpStatus.CONFLICT);
        }

        ReleaseApproval approval = new ReleaseApproval();
        approval.setAssessmentId(assessmentId);
        approval.setApproverId(approverId);
        approval.setDecision(req.decision());
        approval.setJustification(req.justification());
        approval.setApprovedAt(Instant.now());
        approvalRepo.save(approval);

        // Update assessment status
        a.setAssessmentStatus("APPROVED".equals(req.decision()) ? "READY_FOR_DEPLOYMENT" : "REJECTED");
        assessmentRepo.save(a);

        // Audit
        String auditAction = "APPROVED".equals(req.decision()) ? "RELEASE_APPROVED" : "RELEASE_REJECTED";
        auditService.record(approverId, "USER", auditAction, "release_assessment", assessmentId,
                Map.of("decision", req.decision(), "approver", approverUsername));

        return new ApprovalResponse(approval.getId(), approval.getApproverId(),
                approval.getDecision(), approval.getJustification(),
                approval.getApprovedAt(), approval.getCreatedAt());
    }

    // ── Helpers ───────────────────────────────────────────────

    private ReleaseAssessmentResponse toResponse(ReleaseAssessment a) {
        List<ApprovalResponse> approvals = approvalRepo.findByAssessmentId(a.getId())
                .stream()
                .map(ap -> new ApprovalResponse(ap.getId(), ap.getApproverId(), ap.getDecision(),
                        ap.getJustification(), ap.getApprovedAt(), ap.getCreatedAt()))
                .toList();
        List<AuditEventResponse> audit = auditService.getForEntity("release_assessment", a.getId());
        return new ReleaseAssessmentResponse(
                a.getId(), a.getApplication().getId(), a.getApplication().getName(),
                a.getPipelineRun().getId(), a.getArtifactDigest(),
                a.getRiskScore(), a.getDecision(), a.getPolicyVersion(),
                a.getAssessmentStatus(), a.getHardBlockReasons(), a.getScoreBreakdown(),
                a.getEvidenceSnapshot(), a.getRequestedBy(), a.getAssessedAt(), a.getCreatedAt(),
                approvals, audit);
    }

    @SuppressWarnings("unchecked")
    private List<String> parseStringList(String json) {
        try {
            if (json == null) return List.of();
            List<Object> list = new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, List.class);
            return list.stream().map(Object::toString).toList();
        } catch (Exception e) { return List.of(); }
    }
}
