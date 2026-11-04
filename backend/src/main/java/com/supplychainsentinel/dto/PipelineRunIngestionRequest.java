package com.supplychainsentinel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Request body for pipeline run ingestion from CI/CD systems.
 *
 * Required fields: applicationKey, provider, externalRunId, workflowName,
 * repositoryUrl, branchName, commitSha, status, startedAt.
 * All other fields are optional.
 */
public record PipelineRunIngestionRequest(

        @NotBlank(message = "Application key is required")
        String applicationKey,

        @NotBlank(message = "Provider is required")
        @Pattern(regexp = "JENKINS|GITHUB_ACTIONS",
                message = "Provider must be JENKINS or GITHUB_ACTIONS")
        String provider,

        @NotBlank(message = "External run ID is required")
        @Size(max = 255)
        String externalRunId,

        @NotBlank(message = "Workflow name is required")
        String workflowName,

        @NotBlank(message = "Repository URL is required")
        @Size(max = 500)
        String repositoryUrl,

        @NotBlank(message = "Branch name is required")
        String branchName,

        @NotBlank(message = "Commit SHA is required")
        String commitSha,

        String commitMessage,

        String runNumber,

        String triggerEvent,

        @NotBlank(message = "Status is required")
        @Pattern(regexp = "QUEUED|RUNNING|SUCCESS|FAILED|CANCELLED",
                message = "Status must be one of: QUEUED, RUNNING, SUCCESS, FAILED, CANCELLED")
        String status,

        @NotNull(message = "Started at timestamp is required")
        Instant startedAt,

        Instant completedAt,

        String artifactName,

        String artifactVersion,

        @Pattern(regexp = "[a-fA-F0-9]{64}",
                message = "Artifact digest must be a valid SHA-256 hex string")
        String artifactDigest
) {}
