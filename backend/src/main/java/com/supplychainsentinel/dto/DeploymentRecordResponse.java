package com.supplychainsentinel.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for deployment records.
 */
public record DeploymentRecordResponse(
        UUID id,
        UUID applicationId,
        String applicationName,
        UUID pipelineRunId,
        UUID releaseAssessmentId,
        String argocdApplication,
        String argocdProject,
        String environment,
        String cluster,
        String namespace,
        String gitRepoUrl,
        String gitRevision,
        String imageReference,
        String imageDigest,
        String syncStatus,
        String healthStatus,
        String operationPhase,
        Instant deployedAt,
        Instant completedAt,
        String externalEventId,
        Instant createdAt,
        Instant updatedAt,
        boolean digestMatchesAssessment
) {}
