package com.supplychainsentinel.dto;

import java.time.Instant;
import java.util.UUID;

/** Compact SBOM info for the dashboard recent-SBOMs list. */
public record RecentSbomDto(
        UUID id,
        String applicationName,
        String specVersion,
        int componentCount,
        String artifactDigest,
        Instant ingestedAt
) {}
