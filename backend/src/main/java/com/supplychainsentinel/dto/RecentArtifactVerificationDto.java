package com.supplychainsentinel.dto;

import java.time.Instant;
import java.util.UUID;

/** Compact artifact verification info for the dashboard. CI-reported. */
public record RecentArtifactVerificationDto(
        UUID id,
        String applicationName,
        String artifactReference,
        String verificationStatus,
        String verificationMode,
        Instant reportedAt
) {}
