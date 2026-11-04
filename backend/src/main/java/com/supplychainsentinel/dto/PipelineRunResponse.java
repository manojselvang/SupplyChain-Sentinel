package com.supplychainsentinel.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for pipeline run data. Never exposes the JPA entity directly.
 */
public record PipelineRunResponse(
        UUID id,
        UUID applicationId,
        String applicationName,
        String provider,
        String externalRunId,
        String workflowName,
        String repositoryUrl,
        String branchName,
        String commitSha,
        String commitMessage,
        String runNumber,
        String status,
        String triggerEvent,
        Instant startedAt,
        Instant completedAt,
        String artifactName,
        String artifactVersion,
        String artifactDigest,
        Instant createdAt,
        Instant updatedAt
) {}
