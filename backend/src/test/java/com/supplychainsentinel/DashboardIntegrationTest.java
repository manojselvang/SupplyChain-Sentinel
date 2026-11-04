package com.supplychainsentinel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supplychainsentinel.entity.InventoryApplication;
import com.supplychainsentinel.entity.Role;
import com.supplychainsentinel.entity.User;
import com.supplychainsentinel.repository.ApplicationRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the dashboard summary endpoint.
 *
 * Verifies KPI counts, distribution metrics, recent applications,
 * empty-database behaviour, and RBAC for all five roles.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DashboardIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ApplicationRepository applicationRepository;

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

        createUser("dashadmin", adminRole);
        createUser("dashseceng", seRole);
        createUser("dashdev", devRole);
        createUser("dashrelmgr", rmRole);
        createUser("dashauditor", auditorRole);

        adminToken = loginAndGetToken("dashadmin");
        securityEngineerToken = loginAndGetToken("dashseceng");
        developerToken = loginAndGetToken("dashdev");
        releaseManagerToken = loginAndGetToken("dashrelmgr");
        auditorToken = loginAndGetToken("dashauditor");
    }

    // ── Data setup helper ───────────────────────────────────────

    private void seedApplications() {
        createApp("dash-crit-1", "Critical Payment", "CRITICAL", "ACTIVE", "AWS", "Payments", true);
        createApp("dash-crit-2", "Legacy Critical", "CRITICAL", "DEPRECATED", "ON_PREMISES", "Payments", false);
        createApp("dash-high-1", "High Service", "HIGH", "ACTIVE", "AZURE", "Platform", true);
        createApp("dash-med-1", "Medium Tool", "MEDIUM", "ACTIVE", "AWS", "Platform", false);
        createApp("dash-low-1", "Low Utility", "LOW", "PLANNING", "ON_PREMISES", "Security", false);
    }

    private void createApp(String key, String name, String criticality, String status,
                           String hosting, String businessUnit, boolean internetFacing) {
        InventoryApplication app = new InventoryApplication();
        app.setApplicationKey(key);
        app.setName(name);
        app.setCriticality(criticality);
        app.setLifecycleStatus(status);
        app.setDataClassification("INTERNAL");
        app.setHostingEnvironment(hosting);
        app.setBusinessUnit(businessUnit);
        app.setInternetFacing(internetFacing);
        applicationRepository.saveAndFlush(app);
    }

    // ── KPI tests ───────────────────────────────────────────────

    @Test
    void kpiCountsMatchDatabase() throws Exception {
        seedApplications();

        mockMvc.perform(get("/api/v1/dashboard/summary")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalApplications").value(5))
            .andExpect(jsonPath("$.activeApplications").value(3))
            .andExpect(jsonPath("$.criticalApplications").value(2))
            .andExpect(jsonPath("$.highCriticalityApplications").value(1))
            .andExpect(jsonPath("$.internetFacingApplications").value(2))
            .andExpect(jsonPath("$.highCriticalInternetFacing").value(2));
    }

    // ── Distribution tests ──────────────────────────────────────

    @Test
    void criticalityDistributionMatches() throws Exception {
        seedApplications();

        mockMvc.perform(get("/api/v1/dashboard/summary")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.criticalityDistribution").isArray())
            .andExpect(jsonPath("$.criticalityDistribution.length()").value(4));
    }

    @Test
    void lifecycleDistributionMatches() throws Exception {
        seedApplications();

        mockMvc.perform(get("/api/v1/dashboard/summary")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lifecycleStatusDistribution").isArray())
            .andExpect(jsonPath("$.lifecycleStatusDistribution.length()").value(3));
    }

    @Test
    void hostingEnvironmentDistributionMatches() throws Exception {
        seedApplications();

        mockMvc.perform(get("/api/v1/dashboard/summary")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hostingEnvironmentDistribution").isArray())
            .andExpect(jsonPath("$.hostingEnvironmentDistribution.length()").value(3));
    }

    @Test
    void businessUnitDistributionMatches() throws Exception {
        seedApplications();

        mockMvc.perform(get("/api/v1/dashboard/summary")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.businessUnitDistribution").isArray())
            .andExpect(jsonPath("$.businessUnitDistribution.length()").value(3));
    }

    // ── Recent applications ────────────────────────────────────

    @Test
    void recentApplicationsReturned() throws Exception {
        seedApplications();

        mockMvc.perform(get("/api/v1/dashboard/summary")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recentApplications").isArray())
            .andExpect(jsonPath("$.recentApplications.length()").value(5))
            .andExpect(jsonPath("$.recentApplications[0].name").isNotEmpty())
            .andExpect(jsonPath("$.recentApplications[0].applicationKey").isNotEmpty())
            .andExpect(jsonPath("$.recentApplications[0].criticality").isNotEmpty())
            .andExpect(jsonPath("$.recentApplications[0].lifecycleStatus").isNotEmpty())
            .andExpect(jsonPath("$.recentApplications[0].updatedAt").isNotEmpty());
    }

    // ── Empty database ──────────────────────────────────────────

    @Test
    void emptyDatabaseReturnsZerosAndEmptyLists() throws Exception {
        // No applications seeded — only users in @BeforeEach
        mockMvc.perform(get("/api/v1/dashboard/summary")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalApplications").value(0))
            .andExpect(jsonPath("$.activeApplications").value(0))
            .andExpect(jsonPath("$.criticalApplications").value(0))
            .andExpect(jsonPath("$.highCriticalityApplications").value(0))
            .andExpect(jsonPath("$.internetFacingApplications").value(0))
            .andExpect(jsonPath("$.highCriticalInternetFacing").value(0))
            .andExpect(jsonPath("$.criticalityDistribution").isArray())
            .andExpect(jsonPath("$.criticalityDistribution.length()").value(0))
            .andExpect(jsonPath("$.lifecycleStatusDistribution.length()").value(0))
            .andExpect(jsonPath("$.hostingEnvironmentDistribution.length()").value(0))
            .andExpect(jsonPath("$.businessUnitDistribution.length()").value(0))
            .andExpect(jsonPath("$.recentApplications.length()").value(0));
    }

    // ── RBAC tests ──────────────────────────────────────────────

    @Test
    void adminCanAccessDashboard() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }

    @Test
    void securityEngineerCanAccessDashboard() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary")
                .header("Authorization", "Bearer " + securityEngineerToken))
            .andExpect(status().isOk());
    }

    @Test
    void developerCanAccessDashboard() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary")
                .header("Authorization", "Bearer " + developerToken))
            .andExpect(status().isOk());
    }

    @Test
    void releaseManagerCanAccessDashboard() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary")
                .header("Authorization", "Bearer " + releaseManagerToken))
            .andExpect(status().isOk());
    }

    @Test
    void auditorCanAccessDashboard() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary")
                .header("Authorization", "Bearer " + auditorToken))
            .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary"))
            .andExpect(status().isUnauthorized());
    }

    // ── Helpers ─────────────────────────────────────────────────

    private void createUser(String username, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@test.local");
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

        @SuppressWarnings("unchecked")
        Map<String, Object> map = objectMapper.readValue(
                result.getResponse().getContentAsString(), Map.class);
        return (String) map.get("accessToken");
    }
}
