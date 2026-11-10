package com.supplychainsentinel.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

/**
 * Request body for Argo CD deployment event ingestion.
 */
public record DeploymentIngestionRequest(
        @NotBlank(message = "applicationKey is required")
        String applicationKey,

        UUID pipelineRunId,
        UUID releaseAssessmentId,

        @NotBlank(message = "argocdApplication is required")
        String argocdApplication,

        String argocdProject,

        @NotBlank(message = "environment is required")
        String environment,

        String cluster,
        String namespace,
        String gitRepoUrl,
        String gitRevision,

        @NotBlank(message = "imageReference is required")
        String imageReference,

        String imageDigest,

        @NotBlank(message = "syncStatus is required")
        String syncStatus,

        @NotBlank(message = "healthStatus is required")
        String healthStatus,

        String operationPhase,

        Instant deployedAt,
        Instant completedAt,

        @NotBlank(message = "externalEventId is required")
        String externalEventId,

        JsonNode webhookEvidence
) {}
