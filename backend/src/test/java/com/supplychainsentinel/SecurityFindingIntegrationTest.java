package com.supplychainsentinel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supplychainsentinel.entity.InventoryApplication;
import com.supplychainsentinel.entity.Role;
import com.supplychainsentinel.entity.User;
import com.supplychainsentinel.repository.ApplicationRepository;
import com.supplychainsentinel.repository.PipelineRunRepository;
import com.supplychainsentinel.repository.RoleRepository;
import com.supplychainsentinel.repository.SecurityFindingRepository;
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
 * Integration tests for security scan ingestion, finding deduplication,
 * RBAC, and dashboard metrics.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityFindingIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private PipelineRunRepository pipelineRunRepository;
    @Autowired private SecurityFindingRepository findingRepository;

    private static final String TEST_PASSWORD = "TestPass123!";
    private static final String INGESTION_KEY = "test-ingestion-key-for-ci-pipeline-integration";
    private static final String INGESTION_HEADER = "X-CI-INGESTION-KEY";
    private static final String APP_KEY = "sec-test-app";

    private String adminToken;
    private String secEngToken;
    private String devToken;
    private String rmToken;
    private String auditorToken;
    private UUID appId;

    @BeforeEach
    void setUp() throws Exception {
        InventoryApplication app = new InventoryApplication();
        app.setApplicationKey(APP_KEY);
        app.setName("Security Test App");
        app.setCriticality("HIGH");
        app.setLifecycleStatus("ACTIVE");
        app.setDataClassification("INTERNAL");
        app.setHostingEnvironment("AWS");
        applicationRepository.saveAndFlush(app);
        appId = app.getId();

        createAndToken("secadmin", "ADMIN");
        createAndToken("secseceng", "SECURITY_ENGINEER");
        createAndToken("secdev", "DEVELOPER");
        createAndToken("secrm", "RELEASE_MANAGER");
        createAndToken("secauditor", "AUDITOR");

        adminToken = loginAndGetToken("secadmin");
        secEngToken = loginAndGetToken("secseceng");
        devToken = loginAndGetToken("secdev");
        rmToken = loginAndGetToken("secrm");
        auditorToken = loginAndGetToken("secauditor");
    }

    // ── Trivy JSON ingestion ──────────────────────────────────

    @Test
    void trivyJsonIngestionSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/security-scans")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(trivyPayload(APP_KEY, null)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.scanner").value("trivy"))
            .andExpect(jsonPath("$.reportFormat").value("TRIVY_JSON"))
            .andExpect(jsonPath("$.totalFindings").value(2))
            .andExpect(jsonPath("$.newFindings").value(2))
            .andExpect(jsonPath("$.findings").isArray())
            .andExpect(jsonPath("$.findings[0].severity").isNotEmpty())
            .andExpect(jsonPath("$.findings[0].applicationName").value("Security Test App"));
    }

    @Test
    void sarifIngestionSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/security-scans")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sarifPayload(APP_KEY)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.reportFormat").value("SARIF"))
            .andExpect(jsonPath("$.totalFindings").value(1))
            .andExpect(jsonPath("$.newFindings").value(1))
            .andExpect(jsonPath("$.findings[0].ruleId").value("S2083"));
    }

    // ── Deduplication ─────────────────────────────────────────

    @Test
    void duplicateFindingUpdatesLastSeenPreservesStatus() throws Exception {
        // First scan — creates findings
        mockMvc.perform(post("/api/v1/integrations/security-scans")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(trivyPayload(APP_KEY, null)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.newFindings").value(2));

        // Triage one finding
        MvcResult listResult = mockMvc.perform(get("/api/v1/security-findings")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn();

        @SuppressWarnings("unchecked")
        var page = objectMapper.readValue(listResult.getResponse().getContentAsString(), Map.class);
        @SuppressWarnings("unchecked")
        var content = (java.util.List<Map<String, Object>>) page.get("content");
        String findingId = (String) content.get(0).get("id");

        mockMvc.perform(patch("/api/v1/security-findings/" + findingId + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACKNOWLEDGED\"}"))
            .andExpect(status().isOk());

        // Second scan — same findings, should update lastSeen, not create new
        mockMvc.perform(post("/api/v1/integrations/security-scans")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(trivyPayload(APP_KEY, null)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.newFindings").value(0))
            .andExpect(jsonPath("$.updatedFindings").value(2));

        // Verify triage status is preserved
        mockMvc.perform(get("/api/v1/security-findings/" + findingId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));

        // DB should have exactly 2 findings, not 4
        mockMvc.perform(get("/api/v1/security-findings")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2));
    }

    // ── Ingestion key validation ──────────────────────────────

    @Test
    void missingIngestionKeyReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/security-scans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(trivyPayload(APP_KEY, null)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongIngestionKeyReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/security-scans")
                .header(INGESTION_HEADER, "wrong-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(trivyPayload(APP_KEY, null)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownApplicationKeyReturns404() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/security-scans")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(trivyPayload("no-such-app", null)))
            .andExpect(status().isNotFound());
    }

    @Test
    void invalidReportFormatReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/security-scans")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"applicationKey":"%s","scanner":"trivy","reportFormat":"SNYK",
                     "scanTimestamp":"2026-11-05T10:00:00Z","report":{}}
                    """.formatted(APP_KEY)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void invalidPipelineAssociationReturns400() throws Exception {
        UUID randomId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/integrations/security-scans")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(trivyPayload(APP_KEY, randomId)))
            .andExpect(status().isBadRequest());
    }

    // ── Read endpoints / RBAC ─────────────────────────────────

    @Test
    void allRolesCanReadFindings() throws Exception {
        ingestTrivyForTest();
        for (String token : new String[]{adminToken, secEngToken, devToken, rmToken, auditorToken}) {
            mockMvc.perform(get("/api/v1/security-findings").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        }
    }

    @Test
    void unauthenticatedReadReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/security-findings")).andExpect(status().isUnauthorized());
    }

    @Test
    void filterBySeverity() throws Exception {
        ingestTrivyForTest();
        mockMvc.perform(get("/api/v1/security-findings?severity=HIGH")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void filterByStatus() throws Exception {
        ingestTrivyForTest();
        mockMvc.perform(get("/api/v1/security-findings?status=OPEN")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void applicationFindingHistory() throws Exception {
        ingestTrivyForTest();
        mockMvc.perform(get("/api/v1/applications/" + appId + "/security-findings")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2));
    }

    // ── Status update RBAC ────────────────────────────────────

    @Test
    void adminCanUpdateFindingStatus() throws Exception {
        ingestTrivyForTest();
        String id = getFirstFindingId();
        mockMvc.perform(patch("/api/v1/security-findings/" + id + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACCEPTED\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void securityEngineerCanUpdateFindingStatus() throws Exception {
        ingestTrivyForTest();
        String id = getFirstFindingId();
        mockMvc.perform(patch("/api/v1/security-findings/" + id + "/status")
                .header("Authorization", "Bearer " + secEngToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACKNOWLEDGED\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void developerCannotUpdateFindingStatus() throws Exception {
        ingestTrivyForTest();
        String id = getFirstFindingId();
        mockMvc.perform(patch("/api/v1/security-findings/" + id + "/status")
                .header("Authorization", "Bearer " + devToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACCEPTED\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void auditorCannotUpdateFindingStatus() throws Exception {
        ingestTrivyForTest();
        String id = getFirstFindingId();
        mockMvc.perform(patch("/api/v1/security-findings/" + id + "/status")
                .header("Authorization", "Bearer " + auditorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"REMEDIATED\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void invalidStatusValueReturns400() throws Exception {
        ingestTrivyForTest();
        String id = getFirstFindingId();
        mockMvc.perform(patch("/api/v1/security-findings/" + id + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INVALID\"}"))
            .andExpect(status().isBadRequest());
    }

    // ── Dashboard finding metrics ─────────────────────────────

    @Test
    void dashboardReflectsFindingMetrics() throws Exception {
        ingestTrivyForTest();
        mockMvc.perform(get("/api/v1/dashboard/summary")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.openFindings").value(2))
            .andExpect(jsonPath("$.openFindingsBySeverity").isArray())
            .andExpect(jsonPath("$.recentFindings").isArray())
            .andExpect(jsonPath("$.recentFindings.length()").value(2));
    }

    @Test
    void dashboardEmptyFindingMetrics() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.openFindings").value(0))
            .andExpect(jsonPath("$.criticalOpenFindings").value(0))
            .andExpect(jsonPath("$.highOpenFindings").value(0))
            .andExpect(jsonPath("$.openFindingsBySeverity").isArray())
            .andExpect(jsonPath("$.openFindingsBySeverity.length()").value(0))
            .andExpect(jsonPath("$.recentFindings.length()").value(0));
    }

    // ── Delete app blocked by findings ────────────────────────

    @Test
    void deleteApplicationWithFindingsIsBlocked() throws Exception {
        ingestTrivyForTest();
        // Attempt to delete app that has findings — FK RESTRICT should block it
        mockMvc.perform(delete("/api/v1/applications/" + appId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isConflict());
    }

    // ── Helpers ───────────────────────────────────────────────

    private void createAndToken(String username, String roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
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
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, TEST_PASSWORD)))
            .andExpect(status().isOk())
            .andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> map = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return (String) map.get("accessToken");
    }

    private void ingestTrivyForTest() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/security-scans")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(trivyPayload(APP_KEY, null)))
            .andExpect(status().isCreated());
    }

    private String getFirstFindingId() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/security-findings")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn();
        @SuppressWarnings("unchecked")
        var page = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        @SuppressWarnings("unchecked")
        var content = (java.util.List<Map<String, Object>>) page.get("content");
        return (String) content.get(0).get("id");
    }

    private String trivyPayload(String appKey, UUID pipelineRunId) {
        String pipelineRunField = pipelineRunId != null
                ? "\"pipelineRunId\":\"%s\",".formatted(pipelineRunId)
                : "";
        return """
            {
                "applicationKey":"%s",
                %s
                "scanner":"trivy",
                "reportFormat":"TRIVY_JSON",
                "scanTimestamp":"2026-11-05T10:00:00Z",
                "report":{
                    "Results":[
                        {
                            "Target":"alpine:3.18",
                            "Type":"alpine",
                            "Vulnerabilities":[
                                {
                                    "VulnerabilityID":"CVE-2023-0001",
                                    "PkgName":"libssl",
                                    "InstalledVersion":"1.1.1t",
                                    "FixedVersion":"1.1.1u",
                                    "Severity":"HIGH",
                                    "Title":"OpenSSL vulnerability",
                                    "Description":"Buffer overflow in OpenSSL",
                                    "PrimaryURL":"https://nvd.nist.gov/vuln/detail/CVE-2023-0001"
                                },
                                {
                                    "VulnerabilityID":"CVE-2023-0002",
                                    "PkgName":"musl",
                                    "InstalledVersion":"1.2.3",
                                    "FixedVersion":"1.2.4",
                                    "Severity":"MEDIUM",
                                    "Title":"musl libc vulnerability",
                                    "Description":"Integer overflow in musl"
                                }
                            ]
                        }
                    ]
                }
            }
            """.formatted(appKey, pipelineRunField);
    }

    private String sarifPayload(String appKey) {
        return """
            {
                "applicationKey":"%s",
                "scanner":"codeql",
                "reportFormat":"SARIF",
                "scanTimestamp":"2026-11-05T10:00:00Z",
                "report":{
                    "version":"2.1.0",
                    "runs":[
                        {
                            "tool":{
                                "driver":{
                                    "name":"CodeQL",
                                    "rules":[
                                        {
                                            "id":"S2083",
                                            "name":"PathTraversalInjection",
                                            "shortDescription":{"text":"Path traversal injection"},
                                            "fullDescription":{"text":"User-controlled data used in path operations"},
                                            "properties":{"security-severity":"7.5"}
                                        }
                                    ]
                                }
                            },
                            "results":[
                                {
                                    "ruleId":"S2083",
                                    "level":"error",
                                    "message":{"text":"Untrusted data flows to file path"},
                                    "locations":[
                                        {
                                            "physicalLocation":{
                                                "artifactLocation":{"uri":"src/main/FileService.java"},
                                                "region":{"startLine":42}
                                            }
                                        }
                                    ]
                                }
                            ]
                        }
                    ]
                }
            }
            """.formatted(appKey);
    }
}
