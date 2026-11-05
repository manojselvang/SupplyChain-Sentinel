package com.supplychainsentinel.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Compact finding info for the dashboard "recent findings" list.
 */
public record RecentFindingDto(
        UUID id,
        String applicationName,
        String title,
        String vulnerabilityId,
        String severity,
        String scanner,
        String status,
        Instant lastSeen
) {}
