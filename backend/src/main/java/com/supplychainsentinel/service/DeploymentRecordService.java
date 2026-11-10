package com.supplychainsentinel.service;

import com.supplychainsentinel.dto.DeploymentIngestionRequest;
import com.supplychainsentinel.dto.DeploymentRecordResponse;
import com.supplychainsentinel.entity.*;
import com.supplychainsentinel.exception.AuthException;
import com.supplychainsentinel.exception.ResourceNotFoundException;
import com.supplychainsentinel.mapper.DeploymentRecordMapper;
import com.supplychainsentinel.repository.*;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Deployment record ingestion and read logic.
 *
 * <h2>One operation = one record</h2>
 * <p>All Argo CD notification triggers for the <em>same sync operation</em>
 * (sync-succeeded, sync-failed, sync-error, health-degraded) must supply the
 * <strong>same</strong> {@code externalEventId}.  The recommended construction is
 * {@code argocd-<Application UID>-<operationState.startedAt>}.  Because that value
 * is identical across every notification fired for the same operation, a
 * health-degraded follow-up updates the existing record rather than creating a
 * duplicate.
 *
 * <h2>Idempotency</h2>
 * <p>The {@code (argocdApplication, externalEventId)} pair is unique.  A second
 * delivery with the same pair updates <em>only</em> mutable status fields
 * ({@code syncStatus}, {@code healthStatus}, {@code operationPhase},
 * {@code completedAt} when previously null) and leaves all immutable deployment
 * facts ({@code deployedAt}, {@code imageReference}, {@code imageDigest},
 * {@code gitRevision}, {@code cluster}, {@code namespace}) unchanged.
 *
 * <h2>Auto-correlation</h2>
 * <p>When {@code releaseAssessmentId} is absent but {@code imageDigest} is
 * present, the service looks up the most recent assessment whose
 * {@code artifactDigest} matches the digest for the same application and links
 * it automatically.
 */
@Service
public class DeploymentRecordService {

    private final DeploymentRecordRepository deploymentRepo;
    private final ApplicationRepository applicationRepository;
    private final PipelineRunRepository pipelineRunRepository;
    private final ReleaseAssessmentRepository assessmentRepository;

    public DeploymentRecordService(
            DeploymentRecordRepository deploymentRepo,
            ApplicationRepository applicationRepository,
            PipelineRunRepository pipelineRunRepository,
            ReleaseAssessmentRepository assessmentRepository
    ) {
        this.deploymentRepo = deploymentRepo;
        this.applicationRepository = applicationRepository;
        this.pipelineRunRepository = pipelineRunRepository;
        this.assessmentRepository = assessmentRepository;
    }

    // ── Ingestion ─────────────────────────────────────────────

    public record IngestionResult(DeploymentRecordResponse response, boolean created) {}

    @Transactional
    public IngestionResult ingest(DeploymentIngestionRequest req) {
        InventoryApplication app = applicationRepository
                .findByApplicationKey(req.applicationKey())
                .orElseThrow(() -> new ResourceNotFoundException("Unknown application key: " + req.applicationKey()));

        // Validate imageDigest format when provided (must be exactly 64 hex characters)
        if (req.imageDigest() != null && !req.imageDigest().isBlank()) {
            if (!req.imageDigest().matches("[0-9a-fA-F]{64}")) {
                throw new AuthException(
                        "imageDigest must be exactly 64 hexadecimal characters (SHA-256 hex, no 'sha256:' prefix). Got: "
                                + req.imageDigest().length() + " characters",
                        HttpStatus.BAD_REQUEST);
            }
        }

        // Guard against a degenerate externalEventId that would match any other
        // notification lacking operationState.startedAt.  A blank ID must never
        // reach the database because it would conflate unrelated operations.
        if (req.externalEventId() == null || req.externalEventId().isBlank()) {
            throw new AuthException(
                    "externalEventId must not be blank. Ensure the Argo CD notification template " +
                    "populates it from app.metadata.uid and app.status.operationState.startedAt.",
                    HttpStatus.BAD_REQUEST);
        }

        // Optional pipeline run
        PipelineRun pipelineRun = null;
        if (req.pipelineRunId() != null) {
            pipelineRun = pipelineRunRepository.findById(req.pipelineRunId())
                    .orElseThrow(() -> new AuthException("Unknown pipeline run ID: " + req.pipelineRunId(), HttpStatus.BAD_REQUEST));
            if (!pipelineRun.getApplication().getId().equals(app.getId())) {
                throw new AuthException("Pipeline run does not belong to application " + req.applicationKey(), HttpStatus.BAD_REQUEST);
            }
        }

        // Resolve releaseAssessmentId: explicit > auto-correlation by digest
        UUID resolvedAssessmentId = req.releaseAssessmentId();
        if (resolvedAssessmentId != null) {
            final UUID explicitId = resolvedAssessmentId;
            ReleaseAssessment assessment = assessmentRepository.findById(explicitId)
                    .orElseThrow(() -> new AuthException("Unknown release assessment ID: " + explicitId, HttpStatus.BAD_REQUEST));
            if (!assessment.getApplication().getId().equals(app.getId())) {
                throw new AuthException("Release assessment does not belong to application " + req.applicationKey(), HttpStatus.BAD_REQUEST);
            }
        } else if (req.imageDigest() != null && !req.imageDigest().isBlank()) {
            // Auto-correlate: find the most recent assessment for this app whose artifactDigest matches
            resolvedAssessmentId = assessmentRepository
                    .findTopByApplicationIdAndArtifactDigestIgnoreCase(app.getId(), req.imageDigest())
                    .map(ReleaseAssessment::getId)
                    .orElse(null);
        }

        // Idempotency check
        Optional<DeploymentRecord> existing = deploymentRepo
                .findByArgocdApplicationAndExternalEventId(req.argocdApplication(), req.externalEventId());

        DeploymentRecord record;
        boolean created;

        if (existing.isPresent()) {
            record = existing.get();
            created = false;
            // Update ONLY mutable status fields on retry/update.
            // Immutable deployment facts (deployedAt, imageReference, imageDigest,
            // gitRevision) are intentionally left unchanged to preserve deployment history.
            record.setSyncStatus(req.syncStatus());
            record.setHealthStatus(req.healthStatus());
            if (req.operationPhase() != null) record.setOperationPhase(req.operationPhase());
            // completedAt may arrive late (e.g. health notification after sync)
            if (req.completedAt() != null && record.getCompletedAt() == null) {
                record.setCompletedAt(req.completedAt());
            }
            if (req.webhookEvidence() != null) record.setWebhookEvidence(req.webhookEvidence().toString());
        } else {
            record = new DeploymentRecord();
            created = true;
            record.setApplication(app);
            record.setPipelineRun(pipelineRun);
            record.setReleaseAssessmentId(resolvedAssessmentId);
            record.setArgocdApplication(req.argocdApplication());
            record.setArgocdProject(req.argocdProject());
            record.setEnvironment(req.environment());
            record.setCluster(req.cluster());
            record.setNamespace(req.namespace());
            record.setGitRepoUrl(req.gitRepoUrl());
            record.setGitRevision(req.gitRevision());
            record.setImageReference(req.imageReference());
            record.setImageDigest(req.imageDigest());
            record.setSyncStatus(req.syncStatus());
            record.setHealthStatus(req.healthStatus());
            record.setOperationPhase(req.operationPhase());
            record.setDeployedAt(req.deployedAt() != null ? req.deployedAt() : Instant.now());
            record.setCompletedAt(req.completedAt());
            record.setExternalEventId(req.externalEventId());
            if (req.webhookEvidence() != null) {
                String evidence = req.webhookEvidence().toString();
                if (evidence.length() > 10000) evidence = evidence.substring(0, 10000);
                record.setWebhookEvidence(evidence);
            }
        }

        deploymentRepo.save(record);
        deploymentRepo.flush();

        // Check digest match with assessment if linked
        boolean digestMatches = checkDigestMatch(record);

        return new IngestionResult(DeploymentRecordMapper.toDto(record, digestMatches), created);
    }

    // ── Read: list ────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<DeploymentRecordResponse> listDeployments(
            UUID applicationId, String syncStatus, String healthStatus,
            String environment, Pageable pageable) {

        Specification<DeploymentRecord> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (applicationId != null) p.add(cb.equal(root.get("application").get("id"), applicationId));
            if (syncStatus != null) p.add(cb.equal(root.get("syncStatus"), syncStatus));
            if (healthStatus != null) p.add(cb.equal(root.get("healthStatus"), healthStatus));
            if (environment != null) p.add(cb.equal(root.get("environment"), environment));
            return cb.and(p.toArray(new Predicate[0]));
        };

        return deploymentRepo.findAll(spec, pageable).map(d -> {
            boolean match = checkDigestMatch(d);
            return DeploymentRecordMapper.toDto(d, match);
        });
    }

    // ── Read: application deployments ──────────────────────────

    @Transactional(readOnly = true)
    public Page<DeploymentRecordResponse> getApplicationDeployments(UUID applicationId, Pageable pageable) {
        Specification<DeploymentRecord> spec = (root, q, cb) ->
                cb.equal(root.get("application").get("id"), applicationId);
        return deploymentRepo.findAll(spec, pageable).map(d -> {
            boolean match = checkDigestMatch(d);
            return DeploymentRecordMapper.toDto(d, match);
        });
    }

    // ── Helper: digest correlation ────────────────────────────

    private boolean checkDigestMatch(DeploymentRecord d) {
        if (d.getReleaseAssessmentId() == null || d.getImageDigest() == null) return false;
        return assessmentRepository.findById(d.getReleaseAssessmentId())
                .map(a -> d.getImageDigest().equalsIgnoreCase(a.getArtifactDigest()))
                .orElse(false);
    }
}
