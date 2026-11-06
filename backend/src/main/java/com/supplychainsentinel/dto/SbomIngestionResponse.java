package com.supplychainsentinel.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response returned after SBOM ingestion.
 */
public record SbomIngestionResponse(
        UUID sbomId,
        UUID applicationId,
        String applicationName,
        UUID pipelineRunId,
        String artifactDigest,
        String format,
        String specVersion,
        int componentCount,
        Instant generatedAt,
        Instant ingestedAt,
        boolean created
) {}
