package com.supplychainsentinel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supplychainsentinel.dto.CreateUserRequest;
import com.supplychainsentinel.entity.Role;
import com.supplychainsentinel.entity.User;
import com.supplychainsentinel.repository.RoleRepository;
import com.supplychainsentinel.repository.UserRepository;
import com.supplychainsentinel.security.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the authentication and RBAC system.
 *
 * Covers: valid login, invalid password, disabled user, expired token,
 * refresh rotation, revoked token, logout, and RBAC enforcement.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtProperties jwtProperties;

    private static final String TEST_PASSWORD = "TestPass123!";
    private static final String REFRESH_COOKIE = "scs_refresh_token";

    private User adminUser;
    private User developerUser;
    private User auditorUser;
    private User disabledUser;
    private User securityEngineerUser;
    private User releaseManagerUser;

    @BeforeEach
    void setUp() {
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        Role devRole = roleRepository.findByName("DEVELOPER").orElseThrow();
        Role auditorRole = roleRepository.findByName("AUDITOR").orElseThrow();
        Role seRole = roleRepository.findByName("SECURITY_ENGINEER").orElseThrow();
        Role rmRole = roleRepository.findByName("RELEASE_MANAGER").orElseThrow();

        adminUser = createUser("testadmin", "admin@test.local", adminRole);
        developerUser = createUser("testdev", "dev@test.local", devRole);
        auditorUser = createUser("testauditor", "auditor@test.local", auditorRole);
        securityEngineerUser = createUser("testseceng", "seceng@test.local", seRole);
        releaseManagerUser = createUser("testrelmgr", "relmgr@test.local", rmRole);
        disabledUser = createUser("testdisabled", "disabled@test.local", devRole);
        disabledUser.setEnabled(false);
        userRepository.save(disabledUser);
    }

    // ── Login tests ────────────────────────────────────────────

    @Test
    void validLoginSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"testadmin","password":"%s"}
                    """.formatted(TEST_PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.user.username").value("testadmin"))
            .andExpect(jsonPath("$.user.roles").isArray())
            .andExpect(cookie().exists(REFRESH_COOKIE));
    }

    @Test
    void invalidPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"testadmin","password":"wrongpassword"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void disabledUserCannotAuthenticate() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"testdisabled","password":"%s"}
                    """.formatted(TEST_PASSWORD)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Account is disabled"));
    }

    @Test
    void nonExistentUserReturnsGenericError() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"doesnotexist","password":"whatever"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    // ── Token tests ────────────────────────────────────────────

    @Test
    void expiredAccessTokenIsRejected() throws Exception {
        // Generate a token that expired 5 minutes ago
        String expiredToken = createExpiredToken(adminUser);

        mockMvc.perform(get("/api/v1/users")
                .header("Authorization", "Bearer " + expiredToken))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void missingTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
            .andExpect(status().isUnauthorized());
    }

    // ── Refresh token tests ────────────────────────────────────

    @Test
    void refreshTokenRotationWorks() throws Exception {
        // 1. Login to get refresh token cookie
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"testadmin","password":"%s"}
                    """.formatted(TEST_PASSWORD)))
            .andExpect(status().isOk())
            .andReturn();

        String oldRefreshToken = extractRefreshCookie(loginResult);

        // 2. Refresh to get new access token + rotated cookie
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(new Cookie(REFRESH_COOKIE, oldRefreshToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andReturn();

        String newRefreshToken = extractRefreshCookie(refreshResult);

        // The old and new refresh tokens should be different
        assert !oldRefreshToken.equals(newRefreshToken) : "Refresh token should be rotated";
    }

    @Test
    void reusedRefreshTokenIsRejected() throws Exception {
        // 1. Login
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"testadmin","password":"%s"}
                    """.formatted(TEST_PASSWORD)))
            .andExpect(status().isOk())
            .andReturn();

        String oldRefreshToken = extractRefreshCookie(loginResult);

        // 2. Refresh (rotation)
        mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(new Cookie(REFRESH_COOKIE, oldRefreshToken)))
            .andExpect(status().isOk());

        // 3. Try to reuse the old (now revoked) refresh token
        mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(new Cookie(REFRESH_COOKIE, oldRefreshToken)))
            .andExpect(status().isUnauthorized());
    }

    // ── Logout tests ──────────────────────────────────────────

    @Test
    void logoutInvalidatesRefreshToken() throws Exception {
        // 1. Login
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"testadmin","password":"%s"}
                    """.formatted(TEST_PASSWORD)))
            .andExpect(status().isOk())
            .andReturn();

        String refreshToken = extractRefreshCookie(loginResult);
        String accessToken = extractAccessToken(loginResult);

        // 2. Logout
        mockMvc.perform(post("/api/v1/auth/logout")
                .header("Authorization", "Bearer " + accessToken)
                .cookie(new Cookie(REFRESH_COOKIE, refreshToken)))
            .andExpect(status().isNoContent());

        // 3. The revoked refresh token should not work
        mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(new Cookie(REFRESH_COOKIE, refreshToken)))
            .andExpect(status().isUnauthorized());
    }

    // ── RBAC tests ────────────────────────────────────────────

    @Test
    void adminCanCreateUser() throws Exception {
        String accessToken = loginAndGetToken("testadmin");

        CreateUserRequest createRequest = new CreateUserRequest(
            "newuser", "newuser@test.local", "NewPass123!", "New User", Set.of("DEVELOPER")
        );

        mockMvc.perform(post("/api/v1/users")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.username").value("newuser"));
    }

    @Test
    void auditorCannotCreateUser() throws Exception {
        String accessToken = loginAndGetToken("testauditor");

        CreateUserRequest createRequest = new CreateUserRequest(
            "newuser2", "newuser2@test.local", "NewPass123!", "New User 2", Set.of("DEVELOPER")
        );

        mockMvc.perform(post("/api/v1/users")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isForbidden());
    }

    @Test
    void auditorCanReadUsers() throws Exception {
        String accessToken = loginAndGetToken("testauditor");

        mockMvc.perform(get("/api/v1/users")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    void developerCannotCreateUser() throws Exception {
        String accessToken = loginAndGetToken("testdev");

        CreateUserRequest createRequest = new CreateUserRequest(
            "newuser3", "newuser3@test.local", "NewPass123!", "New User 3", Set.of("DEVELOPER")
        );

        mockMvc.perform(post("/api/v1/users")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isForbidden());
    }

    @Test
    void developerCannotAccessAdminOnlyEndpoint() throws Exception {
        String accessToken = loginAndGetToken("testdev");

        mockMvc.perform(delete("/api/v1/users/" + auditorUser.getId())
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminCanDeleteUser() throws Exception {
        String accessToken = loginAndGetToken("testadmin");

        mockMvc.perform(delete("/api/v1/users/" + auditorUser.getId())
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isNoContent());
    }

    // ── Current user endpoint ──────────────────────────────────

    @Test
    void currentUserEndpointReturnsUserInfo() throws Exception {
        String accessToken = loginAndGetToken("testadmin");

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("testadmin"))
            .andExpect(jsonPath("$.roles").isArray());
    }

    // ── Invalid JWT signature ──────────────────────────────────

    @Test
    void invalidJwtSignatureIsRejected() throws Exception {
        // Create a token signed with a DIFFERENT key (different secret)
        String wrongSecret = "a-completely-different-secret-key-at-least-32-characters-long!!";
        SecretKey wrongKey = Keys.hmacShaKeyFor(wrongSecret.getBytes(StandardCharsets.UTF_8));
        String tamperedToken = Jwts.builder()
            .issuer(jwtProperties.issuer())
            .subject(adminUser.getId().toString())
            .claim("username", adminUser.getUsername())
            .claim("roles", adminUser.getRoleNames().stream().toList())
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(Instant.now().plus(Duration.ofMinutes(15))))
            .signWith(wrongKey)
            .compact();

        mockMvc.perform(get("/api/v1/users")
                .header("Authorization", "Bearer " + tamperedToken))
            .andExpect(status().isUnauthorized());
    }

    // ── Expanded RBAC tests ────────────────────────────────────

    @Test
    void securityEngineerCannotCreateUsers() throws Exception {
        String accessToken = loginAndGetToken("testseceng");

        CreateUserRequest createRequest = new CreateUserRequest(
            "secenguser", "seceng@test.local", "NewPass123!", "Sec Eng User", Set.of("DEVELOPER")
        );

        mockMvc.perform(post("/api/v1/users")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isForbidden());
    }

    @Test
    void securityEngineerCanReadUsers() throws Exception {
        String accessToken = loginAndGetToken("testseceng");

        mockMvc.perform(get("/api/v1/users")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    void releaseManagerCannotCreateUsers() throws Exception {
        String accessToken = loginAndGetToken("testrelmgr");

        CreateUserRequest createRequest = new CreateUserRequest(
            "rmuser", "rm@test.local", "NewPass123!", "RM User", Set.of("DEVELOPER")
        );

        mockMvc.perform(post("/api/v1/users")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isForbidden());
    }

    @Test
    void releaseManagerCanReadUsers() throws Exception {
        String accessToken = loginAndGetToken("testrelmgr");

        mockMvc.perform(get("/api/v1/users")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    void releaseManagerCannotDeleteUsers() throws Exception {
        String accessToken = loginAndGetToken("testrelmgr");

        mockMvc.perform(delete("/api/v1/users/" + auditorUser.getId())
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isForbidden());
    }

    // ── Cookie attribute verification ──────────────────────────

    @Test
    void refreshCookieIsHttpOnly() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"testadmin","password":"%s"}
                    """.formatted(TEST_PASSWORD)))
            .andExpect(status().isOk())
            .andReturn();

        // The Set-Cookie header should contain HttpOnly
        String setCookieHeader = result.getResponse().getHeader("Set-Cookie");
        assert setCookieHeader != null : "Set-Cookie header should be present";
        assert setCookieHeader.contains("HttpOnly") : "Refresh cookie must be HttpOnly";
        assert setCookieHeader.contains("SameSite=Lax") : "Refresh cookie must have SameSite=Lax";
    }

    // ── Helpers ────────────────────────────────────────────────

    private User createUser(String username, String email, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(TEST_PASSWORD));
        user.setDisplayName(username);
        user.setEnabled(true);
        user.setAccountLocked(false);
        user.setFailedLoginAttempts(0);
        user.getRoles().add(role);
        return userRepository.save(user);
    }

    private String loginAndGetToken(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"%s","password":"%s"}
                    """.formatted(username, TEST_PASSWORD)))
            .andExpect(status().isOk())
            .andReturn();

        return extractAccessToken(result);
    }

    @SuppressWarnings("unchecked")
    private String extractAccessToken(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        Map<String, Object> map = objectMapper.readValue(body, Map.class);
        return (String) map.get("accessToken");
    }

    private String extractRefreshCookie(MvcResult result) {
        jakarta.servlet.http.Cookie[] cookies = result.getResponse().getCookies();
        if (cookies == null) return null;
        for (jakarta.servlet.http.Cookie cookie : cookies) {
            if (REFRESH_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String createExpiredToken(User user) {
        byte[] keyBytes = jwtProperties.secret().getBytes(StandardCharsets.UTF_8);
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);
        return Jwts.builder()
            .issuer(jwtProperties.issuer())
            .subject(user.getId().toString())
            .claim("username", user.getUsername())
            .claim("roles", user.getRoleNames().stream().toList())
            .issuedAt(Date.from(Instant.now().minus(Duration.ofMinutes(10))))
            .expiration(Date.from(Instant.now().minus(Duration.ofMinutes(5))))
            .signWith(key)
            .compact();
    }
}
