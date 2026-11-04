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

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for pipeline run ingestion and read endpoints.
 *
 * Covers: valid ingestion (Jenkins + GitHub Actions), API key validation,
 * unknown application, invalid provider/status, duplicate submission,
 * update existing run, invalid digest, invalid timestamps, read endpoints
 * with JWT auth, and dashboard pipeline metrics.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PipelineRunIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ApplicationRepository applicationRepository;

    private static final String TEST_PASSWORD = "TestPass123!";
    private static final String INGESTION_KEY = "test-ingestion-key-for-ci-pipeline-integration";
    private static final String INGESTION_HEADER = "X-CI-INGESTION-KEY";

    private String adminToken;
    private UUID appId;

    @BeforeEach
    void setUp() throws Exception {
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        createUser("pipeadmin", adminRole);
        adminToken = loginAndGetToken("pipeadmin");

        // Create a test application for ingestion
        InventoryApplication app = new InventoryApplication();
        app.setApplicationKey("pipe-test-app");
        app.setName("Pipeline Test App");
        app.setCriticality("HIGH");
        app.setLifecycleStatus("ACTIVE");
        app.setDataClassification("INTERNAL");
        app.setHostingEnvironment("AWS");
        app.setInternetFacing(false);
        applicationRepository.saveAndFlush(app);
        appId = app.getId();
    }

    // ── Ingestion: valid ─────────────────────────────────────

    @Test
    void validJenkinsIngestionSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/pipeline-runs")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"applicationKey":"pipe-test-app","provider":"JENKINS","externalRunId":"jenkins-123",
                     "workflowName":"build-and-test","repositoryUrl":"https://github.com/org/repo",
                     "branchName":"main","commitSha":"abc123","status":"RUNNING",
                     "startedAt":"2026-11-04T10:00:00Z"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.provider").value("JENKINS"))
            .andExpect(jsonPath("$.status").value("RUNNING"))
            .andExpect(jsonPath("$.applicationName").value("Pipeline Test App"));
    }

    @Test
    void validGitHubActionsIngestionSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/pipeline-runs")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"applicationKey":"pipe-test-app","provider":"GITHUB_ACTIONS","externalRunId":"gh-456",
                     "workflowName":"CI","repositoryUrl":"https://github.com/org/repo",
                     "branchName":"feature","commitSha":"def456","status":"SUCCESS",
                     "startedAt":"2026-11-04T10:00:00Z","completedAt":"2026-11-04T10:05:00Z",
                     "artifactName":"app.jar","artifactVersion":"1.0.0","artifactDigest":"a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.provider").value("GITHUB_ACTIONS"))
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.artifactDigest").isNotEmpty());
    }

    // ── Ingestion: API key validation ────────────────────────

    @Test
    void missingIngestionKeyRejected() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/pipeline-runs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"applicationKey":"pipe-test-app","provider":"JENKINS","externalRunId":"no-key-1",
                     "workflowName":"build","repositoryUrl":"https://github.com/org/repo",
                     "branchName":"main","commitSha":"abc","status":"RUNNING",
                     "startedAt":"2026-11-04T10:00:00Z"}
                    """))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidIngestionKeyRejected() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/pipeline-runs")
                .header(INGESTION_HEADER, "wrong-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"applicationKey":"pipe-test-app","provider":"JENKINS","externalRunId":"bad-key-1",
                     "workflowName":"build","repositoryUrl":"https://github.com/org/repo",
                     "branchName":"main","commitSha":"abc","status":"RUNNING",
                     "startedAt":"2026-11-04T10:00:00Z"}
                    """))
            .andExpect(status().isUnauthorized());
    }

    // ── Ingestion: validation errors ──────────────────────────

    @Test
    void unknownApplicationKeyRejected() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/pipeline-runs")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"applicationKey":"does-not-exist","provider":"JENKINS","externalRunId":"unknown-1",
                     "workflowName":"build","repositoryUrl":"https://github.com/org/repo",
                     "branchName":"main","commitSha":"abc","status":"RUNNING",
                     "startedAt":"2026-11-04T10:00:00Z"}
                    """))
            .andExpect(status().isNotFound());
    }

    @Test
    void invalidProviderRejected() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/pipeline-runs")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"applicationKey":"pipe-test-app","provider":"GITLAB","externalRunId":"bad-prov-1",
                     "workflowName":"build","repositoryUrl":"https://github.com/org/repo",
                     "branchName":"main","commitSha":"abc","status":"RUNNING",
                     "startedAt":"2026-11-04T10:00:00Z"}
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void invalidStatusRejected() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/pipeline-runs")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"applicationKey":"pipe-test-app","provider":"JENKINS","externalRunId":"bad-stat-1",
                     "workflowName":"build","repositoryUrl":"https://github.com/org/repo",
                     "branchName":"main","commitSha":"abc","status":"UNKNOWN",
                     "startedAt":"2026-11-04T10:00:00Z"}
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void invalidDigestRejected() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/pipeline-runs")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"applicationKey":"pipe-test-app","provider":"JENKINS","externalRunId":"bad-dig-1",
                     "workflowName":"build","repositoryUrl":"https://github.com/org/repo",
                     "branchName":"main","commitSha":"abc","status":"SUCCESS",
                     "startedAt":"2026-11-04T10:00:00Z","completedAt":"2026-11-04T10:05:00Z",
                     "artifactDigest":"not-a-valid-sha256"}
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void completedBeforeStartedRejected() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/pipeline-runs")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"applicationKey":"pipe-test-app","provider":"JENKINS","externalRunId":"bad-ts-1",
                     "workflowName":"build","repositoryUrl":"https://github.com/org/repo",
                     "branchName":"main","commitSha":"abc","status":"SUCCESS",
                     "startedAt":"2026-11-04T10:05:00Z","completedAt":"2026-11-04T10:00:00Z"}
                    """))
            .andExpect(status().isBadRequest());
    }

    // ── Ingestion: idempotent (duplicate + update) ────────────

    @Test
    void duplicateSubmissionUpdatesExistingRun() throws Exception {
        // First submission — RUNNING
        mockMvc.perform(post("/api/v1/integrations/pipeline-runs")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"applicationKey":"pipe-test-app","provider":"JENKINS","externalRunId":"dup-1",
                     "workflowName":"build","repositoryUrl":"https://github.com/org/repo",
                     "branchName":"main","commitSha":"abc123","status":"RUNNING",
                     "startedAt":"2026-11-04T10:00:00Z"}
                    """))
            .andExpect(status().isCreated());

        // Second submission — SUCCESS (same provider, repo, external_run_id)
        mockMvc.perform(post("/api/v1/integrations/pipeline-runs")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"applicationKey":"pipe-test-app","provider":"JENKINS","externalRunId":"dup-1",
                     "workflowName":"build","repositoryUrl":"https://github.com/org/repo",
                     "branchName":"main","commitSha":"abc123","status":"SUCCESS",
                     "startedAt":"2026-11-04T10:00:00Z","completedAt":"2026-11-04T10:05:00Z",
                     "artifactName":"app.jar","artifactVersion":"2.0.0"}
                    """))
            .andExpect(status().isOk())  // 200 for update, not 201
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.artifactVersion").value("2.0.0"));
    }

    // ── Read endpoints ───────────────────────────────────────

    @Test
    void listPipelineRunsWithJwt() throws Exception {
        ingestRun("list-1", "RUNNING");

        mockMvc.perform(get("/api/v1/pipeline-runs")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void filterByProvider() throws Exception {
        ingestRun("filter-prov-gh", "SUCCESS", "GITHUB_ACTIONS");
        ingestRun("filter-prov-jk", "SUCCESS", "JENKINS");

        mockMvc.perform(get("/api/v1/pipeline-runs")
                .param("provider", "GITHUB_ACTIONS")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].provider").value("GITHUB_ACTIONS"));
    }

    @Test
    void filterByStatus() throws Exception {
        ingestRun("filter-stat-1", "RUNNING");
        ingestRun("filter-stat-2", "SUCCESS");

        mockMvc.perform(get("/api/v1/pipeline-runs")
                .param("status", "SUCCESS")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].status").value("SUCCESS"));
    }

    @Test
    void filterByApplication() throws Exception {
        ingestRun("filter-app-1", "SUCCESS");

        mockMvc.perform(get("/api/v1/pipeline-runs")
                .param("applicationId", appId.toString())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void applicationPipelineHistory() throws Exception {
        ingestRun("hist-1", "SUCCESS");
        ingestRun("hist-2", "FAILED");

        mockMvc.perform(get("/api/v1/applications/" + appId + "/pipeline-runs")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void readEndpointWithoutJwtReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/pipeline-runs"))
            .andExpect(status().isUnauthorized());
    }

    // ── Dashboard pipeline metrics ──────────────────────────

    @Test
    void dashboardReflectsPipelineMetrics() throws Exception {
        ingestRun("dash-1", "SUCCESS");
        ingestRun("dash-2", "SUCCESS");
        ingestRun("dash-3", "FAILED");

        mockMvc.perform(get("/api/v1/dashboard/summary")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalPipelineRuns").value(3))
            .andExpect(jsonPath("$.successfulPipelineRuns").value(2))
            .andExpect(jsonPath("$.failedPipelineRuns").value(1));
    }

    @Test
    void dashboardRecentPipelineRuns() throws Exception {
        ingestRun("recent-1", "SUCCESS");

        mockMvc.perform(get("/api/v1/dashboard/summary")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recentPipelineRuns").isArray())
            .andExpect(jsonPath("$.recentPipelineRuns.length()").value(1))
            .andExpect(jsonPath("$.recentPipelineRuns[0].provider").value("JENKINS"));
    }

    @Test
    void dashboardEmptyPipelineMetrics() throws Exception {
        // No pipeline runs created
        mockMvc.perform(get("/api/v1/dashboard/summary")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalPipelineRuns").value(0))
            .andExpect(jsonPath("$.successfulPipelineRuns").value(0))
            .andExpect(jsonPath("$.failedPipelineRuns").value(0))
            .andExpect(jsonPath("$.recentPipelineRuns.length()").value(0));
    }

    // ── Helpers ────────────────────────────────────────────────

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

    private void ingestRun(String externalRunId, String status) throws Exception {
        ingestRun(externalRunId, status, "JENKINS");
    }

    private void ingestRun(String externalRunId, String status, String provider) throws Exception {
        mockMvc.perform(post("/api/v1/integrations/pipeline-runs")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"applicationKey":"pipe-test-app","provider":"%s","externalRunId":"%s",
                     "workflowName":"build","repositoryUrl":"https://github.com/org/repo",
                     "branchName":"main","commitSha":"abc123","status":"%s",
                     "startedAt":"2026-11-04T10:00:00Z"}
                    """.formatted(provider, externalRunId, status)))
            .andExpect(status().isCreated());
    }
}
