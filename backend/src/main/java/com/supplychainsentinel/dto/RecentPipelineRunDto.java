package com.supplychainsentinel.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Compact pipeline-run info for the dashboard's "recent pipeline runs" list.
 */
public record RecentPipelineRunDto(
        UUID id,
        String applicationName,
        String provider,
        String workflowName,
        String status,
        String branchName,
        Instant startedAt,
        Instant completedAt
) {}
