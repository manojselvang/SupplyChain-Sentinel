package com.supplychainsentinel.dto;

import java.time.Instant;
import java.util.UUID;

public record RecentReleaseAssessmentDto(
    UUID id,
    String applicationName,
    String decision,
    int riskScore,
    String assessmentStatus,
    String artifactDigest,
    Instant assessedAt
) {}
