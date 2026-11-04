package com.supplychainsentinel.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Compact application info for the dashboard's "recent applications" list.
 */
public record RecentApplicationDto(
        UUID id,
        String name,
        String applicationKey,
        String criticality,
        String lifecycleStatus,
        Instant updatedAt
) {}
