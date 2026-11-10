package com.supplychainsentinel.mapper;

import com.supplychainsentinel.dto.DeploymentRecordResponse;
import com.supplychainsentinel.entity.DeploymentRecord;

public final class DeploymentRecordMapper {

    private DeploymentRecordMapper() { }

    public static DeploymentRecordResponse toDto(DeploymentRecord d) {
        // Check if image digest matches the release assessment's artifact digest
        boolean digestMatches = false;
        if (d.getReleaseAssessmentId() != null && d.getImageDigest() != null) {
            // We can't directly access the assessment here without a repo lookup,
            // but the service layer handles this. Default to false; the service can override.
            digestMatches = false;
        }
        return new DeploymentRecordResponse(
                d.getId(),
                d.getApplication().getId(),
                d.getApplication().getName(),
                d.getPipelineRun() != null ? d.getPipelineRun().getId() : null,
                d.getReleaseAssessmentId(),
                d.getArgocdApplication(),
                d.getArgocdProject(),
                d.getEnvironment(),
                d.getCluster(),
                d.getNamespace(),
                d.getGitRepoUrl(),
                d.getGitRevision(),
                d.getImageReference(),
                d.getImageDigest(),
                d.getSyncStatus(),
                d.getHealthStatus(),
                d.getOperationPhase(),
                d.getDeployedAt(),
                d.getCompletedAt(),
                d.getExternalEventId(),
                d.getCreatedAt(),
                d.getUpdatedAt(),
                digestMatches
        );
    }

    public static DeploymentRecordResponse toDto(DeploymentRecord d, boolean digestMatches) {
        return new DeploymentRecordResponse(
                d.getId(),
                d.getApplication().getId(),
                d.getApplication().getName(),
                d.getPipelineRun() != null ? d.getPipelineRun().getId() : null,
                d.getReleaseAssessmentId(),
                d.getArgocdApplication(),
                d.getArgocdProject(),
                d.getEnvironment(),
                d.getCluster(),
                d.getNamespace(),
                d.getGitRepoUrl(),
                d.getGitRevision(),
                d.getImageReference(),
                d.getImageDigest(),
                d.getSyncStatus(),
                d.getHealthStatus(),
                d.getOperationPhase(),
                d.getDeployedAt(),
                d.getCompletedAt(),
                d.getExternalEventId(),
                d.getCreatedAt(),
                d.getUpdatedAt(),
                digestMatches
        );
    }
}
