package com.supplychainsentinel.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * SBOM document metadata response. Never includes the raw JSON document.
 */
public record SbomDocumentResponse(
        UUID id,
        UUID applicationId,
        String applicationName,
        UUID pipelineRunId,
        String artifactDigest,
        String format,
        String specVersion,
        String serialNumber,
        Integer documentVersion,
        String documentSha256,
        int componentCount,
        Instant generatedAt,
        Instant ingestedAt,
        Instant createdAt
) {}
