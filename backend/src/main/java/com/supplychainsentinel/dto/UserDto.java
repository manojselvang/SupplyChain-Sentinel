package com.supplychainsentinel.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * User information returned by auth and admin endpoints.
 * Never includes the password hash.
 */
public record UserDto(
        UUID id,
        String username,
        String email,
        String displayName,
        Set<String> roles,
        boolean enabled,
        Instant createdAt
) {}
