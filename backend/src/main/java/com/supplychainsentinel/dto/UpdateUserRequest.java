package com.supplychainsentinel.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Request body for updating a user (ADMIN only).
 * All fields are optional — only provided fields are updated.
 */
public record UpdateUserRequest(
        @Email(message = "Email must be valid")
        String email,

        String displayName,

        Set<String> roles,

        Boolean enabled,

        @Size(min = 8, max = 255, message = "Password must be at least 8 characters")
        String password
) {}
