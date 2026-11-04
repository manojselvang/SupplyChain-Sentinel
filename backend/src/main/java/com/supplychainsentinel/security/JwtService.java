package com.supplychainsentinel.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Handles JWT access-token creation/validation and refresh-token generation/hashing.
 *
 * <p>Access tokens are stateless HS256 JWTs.
 * Refresh tokens are random UUIDs; only their SHA-256 hash is persisted.</p>
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey signingKey;
    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        // HMAC-SHA256 requires at least 256 bits (32 bytes) of key material
        byte[] keyBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                "JWT secret must be at least 32 bytes (256 bits). Current: " + keyBytes.length);
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    // ── Access token (JWT) ────────────────────────────────────

    /**
     * Generates a signed JWT access token for the given user.
     */
    public String generateAccessToken(UUID userId, String username, List<String> roles) {
        Instant now = Instant.now();
        Instant expiry = now.plus(Duration.ofMinutes(properties.accessTokenTtlMinutes()));
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(userId.toString())
                .claim("username", username)
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Validates a JWT (signature, expiration, issuer) and returns its claims.
     * Throws on invalid/expired tokens.
     */
    public Claims parseAccessToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Extracts the user ID (subject) from a valid token.
     */
    public UUID extractUserId(String token) {
        return UUID.fromString(parseAccessToken(token).getSubject());
    }

    /**
     * Checks whether a token is expired without leaking why it failed.
     * Returns true if the token is valid and not expired.
     */
    public boolean isTokenValid(String token) {
        try {
            parseAccessToken(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("JWT expired");
            return false;
        } catch (JwtException e) {
            log.debug("JWT invalid: {}", e.getMessage());
            return false;
        }
    }

    // ── Refresh token (opaque random UUID) ────────────────────

    /**
     * Generates a new random refresh token (raw value to send to client).
     */
    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    /**
     * Hashes a raw refresh token with SHA-256 for storage.
     */
    public String hashRefreshToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Access-token TTL as a Duration (for cookie maxAge etc.).
     */
    public Duration accessTokenTtl() {
        return Duration.ofMinutes(properties.accessTokenTtlMinutes());
    }

    /**
     * Refresh-token TTL as a Duration.
     */
    public Duration refreshTokenTtl() {
        return Duration.ofDays(properties.refreshTokenTtlDays());
    }

    JwtProperties getProperties() {
        return properties;
    }
}
