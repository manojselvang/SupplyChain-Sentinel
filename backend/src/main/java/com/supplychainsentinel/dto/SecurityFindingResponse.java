package com.supplychainsentinel.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for a single security finding. Never exposes the JPA entity.
 */
public record SecurityFindingResponse(
        UUID id,
        UUID applicationId,
        String applicationName,
        UUID pipelineRunId,
        String scanner,
        String vulnerabilityId,
        String ruleId,
        String title,
        String description,
        String severity,
        String affectedPackage,
        String installedVersion,
        String fixedVersion,
        String affectedLocation,
        String remediationUrl,
        String status,
        Instant firstSeen,
        Instant lastSeen,
        Instant updatedAt
) {}
