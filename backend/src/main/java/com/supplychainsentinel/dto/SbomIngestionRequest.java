package com.supplychainsentinel.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

/**
 * Request body for SBOM ingestion via the integration API.
 * {@code sbom} holds the raw CycloneDX JSON document.
 */
public record SbomIngestionRequest(
        @NotBlank(message = "applicationKey is required")
        String applicationKey,

        UUID pipelineRunId,

        @Pattern(regexp = "[a-fA-F0-9]{64}",
                message = "artifactDigest must be a valid SHA-256 hex string")
        String artifactDigest,

        @NotNull(message = "sbom document is required")
        JsonNode sbom
) {}
