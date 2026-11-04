package com.supplychainsentinel.service;

import com.supplychainsentinel.dto.PipelineRunIngestionRequest;
import com.supplychainsentinel.dto.PipelineRunResponse;
import com.supplychainsentinel.entity.InventoryApplication;
import com.supplychainsentinel.entity.PipelineRun;
import com.supplychainsentinel.exception.ResourceNotFoundException;
import com.supplychainsentinel.mapper.PipelineRunMapper;
import com.supplychainsentinel.repository.ApplicationRepository;
import com.supplychainsentinel.repository.PipelineRunRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

/**
 * Pipeline run ingestion and read business logic.
 *
 * <p>Ingestion is idempotent: repeated submissions for the same
 * (provider, repository_url, external_run_id) update the existing record
 * instead of creating duplicates.</p>
 */
@Service
public class PipelineRunService {

    private final PipelineRunRepository pipelineRunRepository;
    private final ApplicationRepository applicationRepository;

    public PipelineRunService(
            PipelineRunRepository pipelineRunRepository,
            ApplicationRepository applicationRepository
    ) {
        this.pipelineRunRepository = pipelineRunRepository;
        this.applicationRepository = applicationRepository;
    }

    // ── Ingestion (idempotent) ─────────────────────────────────

    public record IngestionResult(PipelineRunResponse response, boolean created) {}

    @Transactional
    public IngestionResult ingest(PipelineRunIngestionRequest request) {
        // Resolve application by key
        InventoryApplication app = applicationRepository
                .findByApplicationKey(request.applicationKey())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Unknown application key: " + request.applicationKey()));

        // Validate timestamps
        if (request.completedAt() != null && request.completedAt().isBefore(request.startedAt())) {
            throw new IllegalArgumentException("completedAt must not be before startedAt");
        }

        // Find existing run (idempotent check)
        Optional<PipelineRun> existing = pipelineRunRepository
                .findByProviderAndRepositoryUrlAndExternalRunId(
                        request.provider(), request.repositoryUrl(), request.externalRunId());

        PipelineRun run;
        boolean created;

        if (existing.isPresent()) {
            run = existing.get();
            created = false;
            // Update mutable fields
            run.setStatus(request.status());
            if (request.commitMessage() != null) run.setCommitMessage(request.commitMessage());
            if (request.runNumber() != null) run.setRunNumber(request.runNumber());
            if (request.triggerEvent() != null) run.setTriggerEvent(request.triggerEvent());
            if (request.completedAt() != null) run.setCompletedAt(request.completedAt());
            if (request.artifactName() != null) run.setArtifactName(request.artifactName());
            if (request.artifactVersion() != null) run.setArtifactVersion(request.artifactVersion());
            if (request.artifactDigest() != null) run.setArtifactDigest(request.artifactDigest());
            // Allow branch/commit updates (e.g. force-push scenarios)
            if (request.branchName() != null) run.setBranchName(request.branchName());
            if (request.commitSha() != null) run.setCommitSha(request.commitSha());
        } else {
            run = new PipelineRun();
            created = true;
            run.setApplication(app);
            run.setProvider(request.provider());
            run.setExternalRunId(request.externalRunId());
            run.setWorkflowName(request.workflowName());
            run.setRepositoryUrl(request.repositoryUrl());
            run.setBranchName(request.branchName());
            run.setCommitSha(request.commitSha());
            run.setCommitMessage(request.commitMessage());
            run.setRunNumber(request.runNumber());
            run.setStatus(request.status());
            run.setTriggerEvent(request.triggerEvent());
            run.setStartedAt(request.startedAt());
            run.setCompletedAt(request.completedAt());
            run.setArtifactName(request.artifactName());
            run.setArtifactVersion(request.artifactVersion());
            run.setArtifactDigest(request.artifactDigest());
        }

        pipelineRunRepository.save(run);
        pipelineRunRepository.flush();
        return new IngestionResult(PipelineRunMapper.toDto(run), created);
    }

    // ── Read: list with filters ───────────────────────────────

    @Transactional(readOnly = true)
    public Page<PipelineRunResponse> listPipelineRuns(
            UUID applicationId, String provider, String status, Pageable pageable) {

        Specification<PipelineRun> spec = (root, query, cb) -> {
            ArrayList<Predicate> predicates = new ArrayList<>();
            if (applicationId != null) {
                predicates.add(cb.equal(root.get("application").get("id"), applicationId));
            }
            if (provider != null) {
                predicates.add(cb.equal(root.get("provider"), provider));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return pipelineRunRepository.findAll(spec, pageable)
                .map(PipelineRunMapper::toDto);
    }

    // ── Read: application pipeline history ────────────────────

    @Transactional(readOnly = true)
    public Page<PipelineRunResponse> getApplicationPipelineRuns(UUID applicationId, Pageable pageable) {
        Specification<PipelineRun> spec = (root, query, cb) ->
                cb.equal(root.get("application").get("id"), applicationId);

        return pipelineRunRepository.findAll(spec, pageable)
                .map(PipelineRunMapper::toDto);
    }
}
