package com.supplychainsentinel.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Application inventory response DTO. Never exposes the JPA entity directly.
 */
public record ApplicationResponse(
        UUID id,
        String applicationKey,
        String name,
        String description,
        String businessUnit,
        String businessOwner,
        String technicalOwner,
        String criticality,
        String lifecycleStatus,
        String dataClassification,
        String repositoryUrl,
        String primaryLanguage,
        String hostingEnvironment,
        boolean internetFacing,
        Instant createdAt,
        Instant updatedAt
) {}
