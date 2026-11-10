package com.supplychainsentinel.dto;

import java.time.Instant;
import java.util.UUID;

public record RecentDeploymentDto(
    UUID id,
    String applicationName,
    String argocdApplication,
    String environment,
    String syncStatus,
    String healthStatus,
    String imageDigest,
    Instant deployedAt
) {}
