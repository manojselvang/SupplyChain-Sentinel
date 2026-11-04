package com.supplychainsentinel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Login request payload.
 */
public record LoginRequest(
        @NotBlank(message = "Username is required")
        @Size(max = 100, message = "Username must be at most 100 characters")
        String username,

        @NotBlank(message = "Password is required")
        @Size(min = 1, max = 255, message = "Password must be between 1 and 255 characters")
        String password
) {}
