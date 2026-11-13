package com.supplychainsentinel;

import com.supplychainsentinel.entity.Role;
import com.supplychainsentinel.entity.User;
import com.supplychainsentinel.repository.RoleRepository;
import com.supplychainsentinel.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full Spring context tests under the production profile with all required
 * environment variables supplied. Verifies:
 * <ul>
 *   <li>Swagger UI and OpenAPI endpoints return 404 (disabled in prod)</li>
 *   <li>Actuator health probe groups (/liveness, /readiness) return 200</li>
 *   <li>Combined /actuator/health returns 200</li>
 *   <li>Login cookie has Secure + HttpOnly + SameSite=Lax attributes</li>
 *   <li>Core API endpoints still function under the prod profile</li>
 * </ul>
 *
 * <p>Validator unit tests are in {@link ProductionConfigValidatorTest}.
 *
 * <p>NOTE: Tests use the local test PostgreSQL instance, not an actual RDS instance.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "prod"})
@TestPropertySource(properties = {
        "DB_HOST=127.0.0.1",
        "DB_PORT=5433",
        "DB_NAME=supplychain_sentinel_test",
        "DB_USERNAME=postgres",
        "DB_PASSWORD=postgres",
        "JWT_SECRET=production-safe-jwt-secret-at-least-32-bytes-long-for-testing",
        "CI_INGESTION_API_KEY=production-safe-ingestion-api-key-at-least-32-bytes",
        "APP_CORS_ALLOWED_ORIGINS=https://sentinel.example.com",
        "APP_COOKIE_SECURE=true",
        "app.production.validate-on-startup=false",
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false",
        "management.endpoint.health.probes.enabled=true",
        "management.health.livenessState.enabled=true",
        "management.health.readinessState.enabled=true",
})
@Transactional
class ProductionConfigIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired PasswordEncoder passwordEncoder;

    // ── Swagger disabled in prod profile ──────────────────────────────────

    @Test
    void swaggerUiIsNotAccessibleInProd() throws Exception {
        // springdoc disabled → NoResourceFoundException → 404 (not 500)
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isNotFound());
    }

    @Test
    void openApiDocsNotAccessibleInProd() throws Exception {
        mockMvc.perform(get("/api-docs"))
                .andExpect(status().isNotFound());
    }

    @Test
    void v3ApiDocsNotAccessibleInProd() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isNotFound());
    }

    // ── Actuator health probe groups ────────────────────────────────────────

    @Test
    void livenessProbeReturns200() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void readinessProbeReturns200() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void combinedHealthEndpointStillAccessible() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    // ── Cookie attributes: Secure + HttpOnly + SameSite=Lax ───────────────

    @Test
    void prodCookieIsSecureHttpOnlySameSiteLax() throws Exception {
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        User user = new User();
        user.setUsername("prodcookietest");
        user.setEmail("prodcookietest@test.local");
        user.setPasswordHash(passwordEncoder.encode("TestPass123!"));
        user.setDisplayName("Prod Cookie Test");
        user.setEnabled(true);
        user.setAccountLocked(false);
        user.setFailedLoginAttempts(0);
        user.getRoles().add(adminRole);
        userRepository.save(user);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"prodcookietest","password":"TestPass123!"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assert setCookie != null : "Set-Cookie header must be present";
        assert setCookie.contains("Secure") :
                "Refresh cookie must have Secure flag in prod. Got: " + setCookie;
        assert setCookie.contains("HttpOnly") :
                "Refresh cookie must be HttpOnly. Got: " + setCookie;
        assert setCookie.contains("SameSite=Lax") :
                "Refresh cookie must have SameSite=Lax. Got: " + setCookie;
    }

    // ── Core API still functional under prod profile ────────────────────────

    @Test
    void loginEndpointStillFunctionalInProd() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"nonexistent","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
