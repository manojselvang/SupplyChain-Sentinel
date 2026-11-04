package com.supplychainsentinel.dto;

/**
 * Response returned on successful login or token refresh.
 * The access token is in the body; the refresh token is set as an HttpOnly cookie.
 */
public record AuthResponse(
        String accessToken,
        long expiresIn,
        UserDto user
) {}
