package com.supplychainsentinel.repository;

import com.supplychainsentinel.entity.PipelineRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface PipelineRunRepository extends JpaRepository<PipelineRun, UUID>,
        JpaSpecificationExecutor<PipelineRun> {

    Optional<PipelineRun> findByProviderAndRepositoryUrlAndExternalRunId(
            String provider, String repositoryUrl, String externalRunId);

    // ── Dashboard aggregation queries ─────────────────────────

    @Query("SELECT COUNT(pr) FROM PipelineRun pr")
    long countAllRuns();

    @Query("SELECT COUNT(pr) FROM PipelineRun pr WHERE pr.status = 'SUCCESS'")
    long countSuccessful();

    @Query("SELECT COUNT(pr) FROM PipelineRun pr WHERE pr.status = 'FAILED'")
    long countFailed();
}
