package com.supplychainsentinel.security;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Externalised JWT and auth configuration.
 * Bound from environment variables via relaxed binding.
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(

        @NotBlank(message = "JWT secret must be set (JWT_SECRET)")
        String secret,

        @Min(1)
        int accessTokenTtlMinutes,

        @Min(1)
        int refreshTokenTtlDays,

        boolean cookieSecure,

        @Min(1)
        int maxLoginAttempts,

        @Min(1)
        int lockoutDurationMinutes,

        String issuer
) {
    /** Sensible defaults for local development. */
    public JwtProperties {
        if (accessTokenTtlMinutes <= 0) accessTokenTtlMinutes = 15;
        if (refreshTokenTtlDays <= 0) refreshTokenTtlDays = 7;
        if (maxLoginAttempts <= 0) maxLoginAttempts = 5;
        if (lockoutDurationMinutes <= 0) lockoutDurationMinutes = 15;
        if (issuer == null || issuer.isBlank()) issuer = "supplychain-sentinel";
    }
}
