package com.supplychainsentinel.controller;

import com.supplychainsentinel.dto.AuthResponse;
import com.supplychainsentinel.dto.LoginRequest;
import com.supplychainsentinel.dto.UserDto;
import com.supplychainsentinel.security.JwtProperties;
import com.supplychainsentinel.security.JwtService;
import com.supplychainsentinel.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication endpoints: login, refresh, logout, current-user.
 *
 * <p>Access tokens are returned in the JSON body.
 * Refresh tokens are sent as HttpOnly, SameSite cookies that the
 * frontend never sees via JavaScript.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Login, token refresh, logout and current-user")
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "scs_refresh_token";
    private static final String COOKIE_PATH = "/api/v1/auth";

    private final AuthService authService;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public AuthController(AuthService authService, JwtService jwtService, JwtProperties jwtProperties) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    // ── Login ─────────────────────────────────────────────────

    @PostMapping("/login")
    @Operation(summary = "Authenticate and receive access token + refresh cookie")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        AuthService.AuthResult result = authService.login(request);
        setRefreshCookie(response, result.rawRefreshToken());
        return ResponseEntity.ok(result.response());
    }

    // ── Refresh ───────────────────────────────────────────────

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a valid refresh-token cookie for a new access token (rotation)")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response
    ) {
        AuthService.AuthResult result = authService.refresh(refreshToken);
        setRefreshCookie(response, result.rawRefreshToken());
        return ResponseEntity.ok(result.response());
    }

    // ── Logout ────────────────────────────────────────────────

    @PostMapping("/logout")
    @Operation(summary = "Revoke the refresh token and clear the cookie")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response
    ) {
        authService.logout(refreshToken);
        clearRefreshCookie(response);
        return ResponseEntity.noContent().build();
    }

    // ── Current user ──────────────────────────────────────────

    @GetMapping("/me")
    @Operation(summary = "Get the currently authenticated user")
    public ResponseEntity<UserDto> me(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }
        UserDto user = authService.getCurrentUser(userDetails.getUsername());
        return ResponseEntity.ok(user);
    }

    // ── Cookie helpers ────────────────────────────────────────

    private void setRefreshCookie(HttpServletResponse response, String rawToken) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, rawToken)
                .httpOnly(true)
                .secure(jwtProperties.cookieSecure())
                .sameSite("Lax")
                .path(COOKIE_PATH)
                .maxAge(jwtService.refreshTokenTtl())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(jwtProperties.cookieSecure())
                .sameSite("Lax")
                .path(COOKIE_PATH)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
