package com.supplychainsentinel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supplychainsentinel.entity.Role;
import com.supplychainsentinel.entity.User;
import com.supplychainsentinel.repository.RoleRepository;
import com.supplychainsentinel.repository.UserRepository;
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

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the application inventory CRUD and RBAC.
 *
 * Uses the dedicated test database (supplychain_sentinel_test) with
 * @Transactional rollback for per-test data isolation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApplicationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String TEST_PASSWORD = "TestPass123!";

    private String adminToken;
    private String securityEngineerToken;
    private String developerToken;
    private String releaseManagerToken;
    private String auditorToken;

    @BeforeEach
    void setUp() throws Exception {
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        Role seRole = roleRepository.findByName("SECURITY_ENGINEER").orElseThrow();
        Role devRole = roleRepository.findByName("DEVELOPER").orElseThrow();
        Role rmRole = roleRepository.findByName("RELEASE_MANAGER").orElseThrow();
        Role auditorRole = roleRepository.findByName("AUDITOR").orElseThrow();

        createUser("appadmin", "appadmin@test.local", adminRole);
        createUser("appseceng", "appseceng@test.local", seRole);
        createUser("appdev", "appdev@test.local", devRole);
        createUser("apprelmgr", "apprelmgr@test.local", rmRole);
        createUser("appauditor", "appauditor@test.local", auditorRole);

        adminToken = loginAndGetToken("appadmin");
        securityEngineerToken = loginAndGetToken("appseceng");
        developerToken = loginAndGetToken("appdev");
        releaseManagerToken = loginAndGetToken("apprelmgr");
        auditorToken = loginAndGetToken("appauditor");
    }

    // ── Create tests ────────────────────────────────────────────

    @Test
    void adminCanCreateApplication() throws Exception {
        mockMvc.perform(post("/api/v1/applications")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateJson("app-001", "Payment Gateway")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.applicationKey").value("app-001"))
            .andExpect(jsonPath("$.name").value("Payment Gateway"))
            .andExpect(jsonPath("$.criticality").value("CRITICAL"))
            .andExpect(jsonPath("$.lifecycleStatus").value("ACTIVE"))
            .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void securityEngineerCanCreateApplication() throws Exception {
        mockMvc.perform(post("/api/v1/applications")
                .header("Authorization", "Bearer " + securityEngineerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateJson("app-002", "Auth Service")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.applicationKey").value("app-002"));
    }

    @Test
    void duplicateApplicationKeyRejected() throws Exception {
        // First create succeeds
        mockMvc.perform(post("/api/v1/applications")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateJson("dup-key", "First App")))
            .andExpect(status().isCreated());

        // Second with same key fails
        mockMvc.perform(post("/api/v1/applications")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateJson("dup-key", "Second App")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("already exists")));
    }

    @Test
    void missingRequiredFieldsRejected() throws Exception {
        // Missing applicationKey
        mockMvc.perform(post("/api/v1/applications")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Test App","criticality":"HIGH","lifecycleStatus":"ACTIVE","dataClassification":"INTERNAL","hostingEnvironment":"AWS"}
                    """))
            .andExpect(status().isBadRequest());

        // Missing name
        mockMvc.perform(post("/api/v1/applications")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"applicationKey":"app-no-name","criticality":"HIGH","lifecycleStatus":"ACTIVE","dataClassification":"INTERNAL","hostingEnvironment":"AWS"}
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void invalidEnumValueRejected() throws Exception {
        mockMvc.perform(post("/api/v1/applications")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"applicationKey":"app-bad-enum","name":"Bad Enum","criticality":"INVALID","lifecycleStatus":"ACTIVE","dataClassification":"INTERNAL","hostingEnvironment":"AWS"}
                    """))
            .andExpect(status().isBadRequest());

        // Invalid lifecycle status
        mockMvc.perform(post("/api/v1/applications")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"applicationKey":"app-bad-status","name":"Bad Status","criticality":"HIGH","lifecycleStatus":"INVALID","dataClassification":"INTERNAL","hostingEnvironment":"AWS"}
                    """))
            .andExpect(status().isBadRequest());

        // Invalid hosting environment
        mockMvc.perform(post("/api/v1/applications")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"applicationKey":"app-bad-host","name":"Bad Host","criticality":"HIGH","lifecycleStatus":"ACTIVE","dataClassification":"INTERNAL","hostingEnvironment":"ORACLE"}
                    """))
            .andExpect(status().isBadRequest());
    }

    // ── Retrieve tests ─────────────────────────────────────────

    @Test
    void retrieveApplicationById() throws Exception {
        String id = createApplicationAndGetId("app-get", "Get Me");

        mockMvc.perform(get("/api/v1/applications/" + id)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.applicationKey").value("app-get"))
            .andExpect(jsonPath("$.name").value("Get Me"));
    }

    @Test
    void applicationNotFoundReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/applications/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("not found")));
    }

    // ── List / search / filter tests ────────────────────────────

    @Test
    void listApplicationsWithPagination() throws Exception {
        createApplicationAndGetId("page-1", "Page One");
        createApplicationAndGetId("page-2", "Page Two");
        createApplicationAndGetId("page-3", "Page Three");

        mockMvc.perform(get("/api/v1/applications")
                .param("size", "2")
                .param("page", "0")
                .param("sort", "name,asc")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(2)))
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.pageable").isNotEmpty());
    }

    @Test
    void searchByName() throws Exception {
        createApplicationAndGetId("search-1", "Payment Gateway");
        createApplicationAndGetId("search-2", "User Service");
        createApplicationAndGetId("search-3", "Payment API");

        mockMvc.perform(get("/api/v1/applications")
                .param("search", "Payment")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(2)))
            .andExpect(jsonPath("$.content[0].name").value(org.hamcrest.Matchers.containsString("Payment")));
    }

    @Test
    void searchByApplicationKey() throws Exception {
        createApplicationAndGetId("key-aaa", "App AAA");
        createApplicationAndGetId("key-bbb", "App BBB");

        mockMvc.perform(get("/api/v1/applications")
                .param("search", "key-aaa")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(1)))
            .andExpect(jsonPath("$.content[0].applicationKey").value("key-aaa"));
    }

    @Test
    void filterByCriticality() throws Exception {
        createApplicationWithCriticality("crit-1", "Critical App", "CRITICAL");
        createApplicationWithCriticality("crit-2", "High App", "HIGH");
        createApplicationWithCriticality("crit-3", "Another Critical", "CRITICAL");

        mockMvc.perform(get("/api/v1/applications")
                .param("criticality", "CRITICAL")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(2)));
    }

    @Test
    void filterByLifecycleStatus() throws Exception {
        createApplicationWithStatus("stat-1", "Active App", "ACTIVE");
        createApplicationWithStatus("stat-2", "Deprecated App", "DEPRECATED");

        mockMvc.perform(get("/api/v1/applications")
                .param("lifecycleStatus", "DEPRECATED")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(1)))
            .andExpect(jsonPath("$.content[0].lifecycleStatus").value("DEPRECATED"));
    }

    // ── Update tests ───────────────────────────────────────────

    @Test
    void adminCanUpdateApplication() throws Exception {
        String id = createApplicationAndGetId("upd-1", "Original Name");

        mockMvc.perform(put("/api/v1/applications/" + id)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Updated Name","criticality":"LOW"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Updated Name"))
            .andExpect(jsonPath("$.criticality").value("LOW"));
    }

    @Test
    void securityEngineerCanUpdateApplication() throws Exception {
        String id = createApplicationAndGetId("upd-2", "SE Update Target");

        mockMvc.perform(put("/api/v1/applications/" + id)
                .header("Authorization", "Bearer " + securityEngineerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"SE Updated","description":"Updated by security engineer"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("SE Updated"));
    }

    @Test
    void updateNotFoundReturns404() throws Exception {
        mockMvc.perform(put("/api/v1/applications/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Ghost App"}
                    """))
            .andExpect(status().isNotFound());
    }

    @Test
    void updateWithInvalidEnumRejected() throws Exception {
        String id = createApplicationAndGetId("upd-bad", "Bad Update");

        mockMvc.perform(put("/api/v1/applications/" + id)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"criticality":"INVALID_VALUE"}
                    """))
            .andExpect(status().isBadRequest());
    }

    // ── Delete tests ────────────────────────────────────────────

    @Test
    void adminCanDeleteApplication() throws Exception {
        String id = createApplicationAndGetId("del-1", "Delete Me");

        mockMvc.perform(delete("/api/v1/applications/" + id)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNoContent());

        // Verify it's gone
        mockMvc.perform(get("/api/v1/applications/" + id)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteNotFoundReturns404() throws Exception {
        mockMvc.perform(delete("/api/v1/applications/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNotFound());
    }

    // ── RBAC tests ─────────────────────────────────────────────

    @Test
    void developerCannotCreateApplication() throws Exception {
        mockMvc.perform(post("/api/v1/applications")
                .header("Authorization", "Bearer " + developerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateJson("dev-block", "Dev Blocked")))
            .andExpect(status().isForbidden());
    }

    @Test
    void releaseManagerCannotCreateApplication() throws Exception {
        mockMvc.perform(post("/api/v1/applications")
                .header("Authorization", "Bearer " + releaseManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateJson("rm-block", "RM Blocked")))
            .andExpect(status().isForbidden());
    }

    @Test
    void auditorCannotCreateApplication() throws Exception {
        mockMvc.perform(post("/api/v1/applications")
                .header("Authorization", "Bearer " + auditorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateJson("aud-block", "Auditor Blocked")))
            .andExpect(status().isForbidden());
    }

    @Test
    void securityEngineerCannotDeleteApplication() throws Exception {
        String id = createApplicationAndGetId("se-del-block", "SE Delete Block");

        mockMvc.perform(delete("/api/v1/applications/" + id)
                .header("Authorization", "Bearer " + securityEngineerToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void developerCannotUpdateApplication() throws Exception {
        String id = createApplicationAndGetId("dev-upd-block", "Dev Update Block");

        mockMvc.perform(put("/api/v1/applications/" + id)
                .header("Authorization", "Bearer " + developerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Hacked"}
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void auditorCannotDeleteApplication() throws Exception {
        String id = createApplicationAndGetId("aud-del-block", "Auditor Delete Block");

        mockMvc.perform(delete("/api/v1/applications/" + id)
                .header("Authorization", "Bearer " + auditorToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void allRolesCanReadApplications() throws Exception {
        createApplicationAndGetId("rbac-read", "RBAC Read Test");

        // ADMIN
        mockMvc.perform(get("/api/v1/applications")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());

        // SECURITY_ENGINEER
        mockMvc.perform(get("/api/v1/applications")
                .header("Authorization", "Bearer " + securityEngineerToken))
            .andExpect(status().isOk());

        // DEVELOPER
        mockMvc.perform(get("/api/v1/applications")
                .header("Authorization", "Bearer " + developerToken))
            .andExpect(status().isOk());

        // RELEASE_MANAGER
        mockMvc.perform(get("/api/v1/applications")
                .header("Authorization", "Bearer " + releaseManagerToken))
            .andExpect(status().isOk());

        // AUDITOR
        mockMvc.perform(get("/api/v1/applications")
                .header("Authorization", "Bearer " + auditorToken))
            .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedAccessRejected() throws Exception {
        // Create without token
        mockMvc.perform(post("/api/v1/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateJson("no-auth", "No Auth")))
            .andExpect(status().isUnauthorized());

        // List without token
        mockMvc.perform(get("/api/v1/applications"))
            .andExpect(status().isUnauthorized());

        // Get without token
        mockMvc.perform(get("/api/v1/applications/" + UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    // ── Helpers ────────────────────────────────────────────────

    private void createUser(String username, String email, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(TEST_PASSWORD));
        user.setDisplayName(username);
        user.setEnabled(true);
        user.setAccountLocked(false);
        user.setFailedLoginAttempts(0);
        user.getRoles().add(role);
        userRepository.save(user);
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
        Map<String, Object> map = objectMapper.readValue(
                result.getResponse().getContentAsString(), Map.class);
        return (String) map.get("accessToken");
    }

    private String validCreateJson(String key, String name) {
        return """
            {"applicationKey":"%s","name":"%s","criticality":"CRITICAL","lifecycleStatus":"ACTIVE","dataClassification":"INTERNAL","hostingEnvironment":"AWS","internetFacing":true}
            """.formatted(key, name);
    }

    private String createApplicationAndGetId(String key, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/applications")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateJson(key, name)))
            .andExpect(status().isCreated())
            .andReturn();
        Map<String, Object> map = objectMapper.readValue(
                result.getResponse().getContentAsString(), Map.class);
        return (String) map.get("id");
    }

    private void createApplicationWithCriticality(String key, String name, String criticality) throws Exception {
        mockMvc.perform(post("/api/v1/applications")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"applicationKey":"%s","name":"%s","criticality":"%s","lifecycleStatus":"ACTIVE","dataClassification":"INTERNAL","hostingEnvironment":"AWS"}
                    """.formatted(key, name, criticality)))
            .andExpect(status().isCreated());
    }

    private void createApplicationWithStatus(String key, String name, String status) throws Exception {
        mockMvc.perform(post("/api/v1/applications")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"applicationKey":"%s","name":"%s","criticality":"HIGH","lifecycleStatus":"%s","dataClassification":"INTERNAL","hostingEnvironment":"AWS"}
                    """.formatted(key, name, status)))
            .andExpect(status().isCreated());
    }
}
