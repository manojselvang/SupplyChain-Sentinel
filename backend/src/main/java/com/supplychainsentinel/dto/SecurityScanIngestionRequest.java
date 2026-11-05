package com.supplychainsentinel.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.UUID;

/**
 * Request body for scanner report ingestion.
 * {@code report} holds the raw scanner JSON (Trivy or SARIF).
 */
public record SecurityScanIngestionRequest(

        @NotBlank(message = "applicationKey is required")
        String applicationKey,

        UUID pipelineRunId,

        @NotBlank(message = "scanner is required")
        String scanner,

        @NotBlank(message = "reportFormat is required")
        @Pattern(regexp = "TRIVY_JSON|SARIF",
                message = "reportFormat must be TRIVY_JSON or SARIF")
        String reportFormat,

        @Pattern(regexp = "[a-fA-F0-9]{64}",
                message = "artifactDigest must be a SHA-256 hex string")
        String artifactDigest,

        @NotNull(message = "scanTimestamp is required")
        Instant scanTimestamp,

        @NotNull(message = "report is required")
        JsonNode report
) {}
