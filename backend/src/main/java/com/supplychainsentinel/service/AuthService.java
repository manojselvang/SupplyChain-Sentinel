package com.supplychainsentinel.service;

import com.supplychainsentinel.dto.AuthResponse;
import com.supplychainsentinel.dto.LoginRequest;
import com.supplychainsentinel.dto.UserDto;
import com.supplychainsentinel.entity.RefreshToken;
import com.supplychainsentinel.entity.User;
import com.supplychainsentinel.exception.AuthException;
import com.supplychainsentinel.mapper.UserMapper;
import com.supplychainsentinel.repository.RefreshTokenRepository;
import com.supplychainsentinel.repository.UserRepository;
import com.supplychainsentinel.security.JwtProperties;
import com.supplychainsentinel.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;


/**
 * Core authentication business logic: login, refresh-token rotation, logout.
 *
 * <p>Security notes:
 * <ul>
 *   <li>Passwords are verified with BCrypt — never logged.</li>
 *   <li>Refresh tokens are random UUIDs; only their SHA-256 hash is persisted.</li>
 *   <li>Username-existence is never leaked through error messages.</li>
 *   <li>Brute-force lockout is enforced after {@code maxLoginAttempts} failures.</li>
 * </ul>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String INVALID_CREDENTIALS = "Invalid username or password";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JwtProperties jwtProperties
            
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }   

    // ── Login ─────────────────────────────────────────────────

    /**
     * Result of a successful login or refresh — the raw refresh token is returned
     * to the controller which sets it as an HttpOnly cookie.
     */
    public record AuthResult(AuthResponse response, String rawRefreshToken) {}

    @Transactional
    public AuthResult login(LoginRequest request) {
        // Always return the same message for not-found and wrong-password
        User user = userRepository.findByUsernameWithRoles(request.username())
                .orElseThrow(() -> new AuthException(INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            recordFailedAttempt(user);
            throw new AuthException(INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED);
        }

        // Password is correct — check account status
        if (isInLockout(user)) {
            throw new AuthException(
                "Account temporarily locked due to too many failed attempts. Try again later.",
                HttpStatus.LOCKED);
        }



        if (!user.isEnabled()) {
            throw new AuthException("Account is disabled", HttpStatus.UNAUTHORIZED);
        }

        if (user.isAccountLocked()) {
            throw new AuthException("Account is locked", HttpStatus.UNAUTHORIZED);
        }

        // Success — reset counter and update last login
        user.setFailedLoginAttempts(0);
        user.setLastFailedLoginAt(null);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        return issueTokens(user);
    }

    // ── Refresh ───────────────────────────────────────────────

    @Transactional
    public AuthResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new AuthException("Refresh token is required", HttpStatus.UNAUTHORIZED);
        }

        String hash = jwtService.hashRefreshToken(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new AuthException("Invalid or expired refresh token", HttpStatus.UNAUTHORIZED));

        if (stored.isRevoked()) {
            // Token reuse detected — revoke entire family for safety
            log.warn("Revoked refresh token presented — possible token theft");
            throw new AuthException("Invalid or expired refresh token", HttpStatus.UNAUTHORIZED);
        }

        if (stored.getExpiresAt().isBefore(Instant.now())) {
            stored.setRevoked(true);
            refreshTokenRepository.save(stored);
            throw new AuthException("Invalid or expired refresh token", HttpStatus.UNAUTHORIZED);
        }

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new AuthException("Invalid or expired refresh token", HttpStatus.UNAUTHORIZED));

        if (!user.isEnabled()) {
            throw new AuthException("Account is disabled", HttpStatus.UNAUTHORIZED);
        }

        // Rotate: revoke old token, issue new one
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        AuthResult result = issueTokens(user);

        // Mark the old token as replaced by the new one (rotation chain)
        RefreshToken newToken = refreshTokenRepository.findByTokenHash(
                jwtService.hashRefreshToken(result.rawRefreshToken())
        ).orElseThrow();
        stored.setReplacedBy(newToken.getId());
        refreshTokenRepository.save(stored);

        return result;
    }

    // ── Logout ────────────────────────────────────────────────

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return; // nothing to revoke
        }
        String hash = jwtService.hashRefreshToken(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    // ── Current user ─────────────────────────────────────────

    @Transactional(readOnly = true)
    public UserDto getCurrentUser(String username) {
        User user = userRepository.findByUsernameWithRoles(username)
                .orElseThrow(() -> new AuthException("User not found", HttpStatus.NOT_FOUND));
        return UserMapper.toDto(user);
    }

    // ── Helpers ──────────────────────────────────────────────

    private AuthResult issueTokens(User user) {
        List<String> roles = user.getRoles().stream()
                .map(r -> r.getName())
                .sorted()
                .toList();

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getUsername(), roles);
        String rawRefreshToken = jwtService.generateRefreshToken();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(user.getId());
        refreshToken.setTokenHash(jwtService.hashRefreshToken(rawRefreshToken));
        refreshToken.setExpiresAt(Instant.now().plus(jwtService.refreshTokenTtl()));
        refreshTokenRepository.save(refreshToken);

        UserDto userDto = UserMapper.toDto(user);
        AuthResponse response = new AuthResponse(
                accessToken,
                jwtService.accessTokenTtl().getSeconds(),
                userDto
        );

        return new AuthResult(response, rawRefreshToken);
    }

    private void recordFailedAttempt(User user) {
        user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
        user.setLastFailedLoginAt(Instant.now());
        userRepository.save(user);
    }

    private boolean isInLockout(User user) {
        if (user.getFailedLoginAttempts() < jwtProperties.maxLoginAttempts()) {
            return false;
        }
        if (user.getLastFailedLoginAt() == null) {
            return false;
        }
        Instant lockoutExpiry = user.getLastFailedLoginAt()
                .plus(Duration.ofMinutes(jwtProperties.lockoutDurationMinutes()));
        return Instant.now().isBefore(lockoutExpiry);
    }
}
