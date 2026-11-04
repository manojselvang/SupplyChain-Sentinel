package com.supplychainsentinel.mapper;

import com.supplychainsentinel.dto.PipelineRunResponse;
import com.supplychainsentinel.entity.PipelineRun;

/**
 * Converts between {@link PipelineRun} entities and {@link PipelineRunResponse} DTOs.
 */
public final class PipelineRunMapper {

    private PipelineRunMapper() { }

    public static PipelineRunResponse toDto(PipelineRun run) {
        return new PipelineRunResponse(
                run.getId(),
                run.getApplication().getId(),
                run.getApplication().getName(),
                run.getProvider(),
                run.getExternalRunId(),
                run.getWorkflowName(),
                run.getRepositoryUrl(),
                run.getBranchName(),
                run.getCommitSha(),
                run.getCommitMessage(),
                run.getRunNumber(),
                run.getStatus(),
                run.getTriggerEvent(),
                run.getStartedAt(),
                run.getCompletedAt(),
                run.getArtifactName(),
                run.getArtifactVersion(),
                run.getArtifactDigest(),
                run.getCreatedAt(),
                run.getUpdatedAt()
        );
    }
}
