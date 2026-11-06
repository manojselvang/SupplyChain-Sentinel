package com.supplychainsentinel.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Normalized SBOM component response DTO.
 */
public record SbomComponentResponse(
        UUID id,
        String bomRef,
        String componentType,
        String groupName,
        String name,
        String version,
        String purl,
        String cpe,
        String scope,
        String supplier,
        String licenses,
        String hashes,
        Instant createdAt
) {}
