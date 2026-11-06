package com.supplychainsentinel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supplychainsentinel.entity.InventoryApplication;
import com.supplychainsentinel.entity.PipelineRun;
import com.supplychainsentinel.entity.Role;
import com.supplychainsentinel.entity.User;
import com.supplychainsentinel.repository.*;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for SBOM ingestion, deduplication, component extraction,
 * artifact verification, and dashboard metrics.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SbomIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private PipelineRunRepository pipelineRunRepository;
    @Autowired private SbomDocumentRepository sbomDocumentRepository;
    @Autowired private SbomComponentRepository sbomComponentRepository;
    @Autowired private ArtifactVerificationRepository verificationRepository;

    private static final String TEST_PASSWORD = "TestPass123!";
    private static final String INGESTION_KEY = "test-ingestion-key-for-ci-pipeline-integration";
    private static final String INGESTION_HEADER = "X-CI-INGESTION-KEY";
    private static final String APP_KEY = "sbom-test-app";
    private static final String ARTIFACT_DIGEST = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2";

    private String adminToken;
    private String devToken;
    private UUID appId;
    private UUID pipelineRunId;

    @BeforeEach
    void setUp() throws Exception {
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        Role devRole = roleRepository.findByName("DEVELOPER").orElseThrow();
        createUser("sbomadmin", adminRole);
        createUser("sbomdev", devRole);
        adminToken = loginAndGetToken("sbomadmin");
        devToken = loginAndGetToken("sbomdev");

        InventoryApplication app = new InventoryApplication();
        app.setApplicationKey(APP_KEY);
        app.setName("SBOM Test App");
        app.setCriticality("HIGH");
        app.setLifecycleStatus("ACTIVE");
        app.setDataClassification("INTERNAL");
        app.setHostingEnvironment("AWS");
        applicationRepository.saveAndFlush(app);
        appId = app.getId();

        // Create a pipeline run with a known artifact digest
        PipelineRun run = new PipelineRun();
        run.setApplication(app);
        run.setProvider("GITHUB_ACTIONS");
        run.setExternalRunId("sbom-gha-run-1");
        run.setRepositoryUrl("https://github.com/org/repo");
        run.setBranchName("main");
        run.setCommitSha("abc123abc123abc123abc123abc123abc123abc123");
        run.setStatus("SUCCESS");
        run.setStartedAt(java.time.Instant.now());
        run.setArtifactDigest(ARTIFACT_DIGEST);
        pipelineRunRepository.saveAndFlush(run);
        pipelineRunId = run.getId();
    }

    // ── CycloneDX 1.7 ingestion ────────────────────────────────

    @Test
    void cyclonedx17IngestionSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/sboms")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sbomPayload17()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.applicationName").value("SBOM Test App"))
            .andExpect(jsonPath("$.format").value("CYCLONEDX_JSON"))
            .andExpect(jsonPath("$.specVersion").value("1.7"))
            .andExpect(jsonPath("$.componentCount").value(2))
            .andExpect(jsonPath("$.created").value(true));
    }

    @Test
    void cyclonedx16IngestionSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/sboms")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sbomPayload16()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.specVersion").value("1.6"))
            .andExpect(jsonPath("$.componentCount").value(1));
    }

    // ── Idempotency ────────────────────────────────────────────

    @Test
    void duplicateSbomSubmissionIsIdempotent() throws Exception {
        // First submission
        MvcResult r1 = mockMvc.perform(post("/api/v1/integrations/sboms")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sbomPayload17()))
            .andExpect(status().isCreated())
            .andReturn();
        String id1 = extractField(r1, "sbomId");

        // Second identical submission
        MvcResult r2 = mockMvc.perform(post("/api/v1/integrations/sboms")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sbomPayload17()))
            .andExpect(status().isOk())           // 200, not 201
            .andExpect(jsonPath("$.created").value(false))
            .andReturn();
        String id2 = extractField(r2, "sbomId");

        // Same SBOM document returned
        assertEquals(id1, id2, "Duplicate submission should return the same SBOM record");

        // Only 1 document in the DB
        assertEquals(1, sbomDocumentRepository.count());
    }

    // ── Validation errors ──────────────────────────────────────

    @Test
    void unknownApplicationKeyRejected() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/sboms")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sbomPayloadForApp("no-such-app")))
            .andExpect(status().isNotFound());
    }

    @Test
    void invalidFormatRejected() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/sboms")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"applicationKey":"%s","sbom":{"bomFormat":"SPDX","specVersion":"2.3","version":1,"components":[]}}
                    """.formatted(APP_KEY)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void unsupportedVersionRejected() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/sboms")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"applicationKey":"%s","sbom":{"bomFormat":"CycloneDX","specVersion":"1.5","version":1,"components":[]}}
                    """.formatted(APP_KEY)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void missingSbomFieldRejected() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/sboms")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"applicationKey":"%s"}
                    """.formatted(APP_KEY)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void invalidDigestRejected() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/sboms")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"applicationKey":"%s","artifactDigest":"not-a-valid-sha256",
                     "sbom":{"bomFormat":"CycloneDX","specVersion":"1.7","version":1,"components":[]}}
                    """.formatted(APP_KEY)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void wrongIngestionKeyRejected() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/sboms")
                .header(INGESTION_HEADER, "wrong-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(sbomPayload17()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void missingIngestionKeyRejected() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/sboms")
                .contentType(MediaType.APPLICATION_JSON)
                .content(sbomPayload17()))
            .andExpect(status().isUnauthorized());
    }

    // ── Pipeline association ───────────────────────────────────

    @Test
    void sbomLinkedToPipelineRun() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/integrations/sboms")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sbomPayloadWithPipeline()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.pipelineRunId").isNotEmpty())
            .andReturn();

        String returnedPipelineId = extractField(result, "pipelineRunId");
        assertEquals(pipelineRunId.toString(), returnedPipelineId);
    }

    @Test
    void invalidPipelineAssociationRejected() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/sboms")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sbomPayloadWithPipelineId(UUID.randomUUID())))
            .andExpect(status().isBadRequest());
    }

    // ── Component extraction ───────────────────────────────────

    @Test
    void componentsExtractedAndSearchable() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/integrations/sboms")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sbomPayload17()))
            .andExpect(status().isCreated())
            .andReturn();

        String sbomId = extractField(result, "sbomId");

        // Search by name
        mockMvc.perform(get("/api/v1/sboms/" + sbomId + "/components")
                .param("name", "spring")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.totalElements").value(1));

        // Get all components
        mockMvc.perform(get("/api/v1/sboms/" + sbomId + "/components")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2));
    }

    // ── Read endpoints ─────────────────────────────────────────

    @Test
    void listSbomsWithJwt() throws Exception {
        ingestSbom17();
        mockMvc.perform(get("/api/v1/sboms")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].applicationName").value("SBOM Test App"))
            .andExpect(jsonPath("$.content[0].specVersion").value("1.7"));
    }

    @Test
    void getSbomById() throws Exception {
        MvcResult r = ingestSbom17();
        String sbomId = extractField(r, "sbomId");

        mockMvc.perform(get("/api/v1/sboms/" + sbomId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(sbomId));
    }

    @Test
    void applicationSbomHistory() throws Exception {
        ingestSbom17();
        mockMvc.perform(get("/api/v1/applications/" + appId + "/sboms")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void allRolesCanReadSboms() throws Exception {
        ingestSbom17();
        mockMvc.perform(get("/api/v1/sboms")
                .header("Authorization", "Bearer " + devToken))
            .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedReadRejected() throws Exception {
        mockMvc.perform(get("/api/v1/sboms"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void filterByApplicationId() throws Exception {
        ingestSbom17();
        mockMvc.perform(get("/api/v1/sboms")
                .param("applicationId", appId.toString())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ── Artifact verification ──────────────────────────────────

    @Test
    void keylessVerificationSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/artifact-verifications")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(verificationPayloadKeyless()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
            .andExpect(jsonPath("$.verificationMode").value("KEYLESS"))
            .andExpect(jsonPath("$.signerIdentity").value("https://github.com/actions/runner"))
            .andExpect(jsonPath("$.oidcIssuer").value("https://token.actions.githubusercontent.com"));
    }

    @Test
    void keyVerificationSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/artifact-verifications")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(verificationPayloadKey()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.verificationMode").value("KEY"))
            .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));
    }

    @Test
    void verifiedStatusRequiresSignerIdentity() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/artifact-verifications")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"applicationKey":"%s","artifactReference":"docker.io/example/app@sha256:%s",
                     "artifactDigest":"%s","verificationMode":"KEYLESS","verificationStatus":"VERIFIED",
                     "reportedAt":"2026-11-05T10:00:00Z"}
                    """.formatted(APP_KEY, ARTIFACT_DIGEST, ARTIFACT_DIGEST)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void keylessVerifiedRequiresOidcIssuer() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/artifact-verifications")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"applicationKey":"%s","artifactReference":"docker.io/example/app@sha256:%s",
                     "artifactDigest":"%s","verificationMode":"KEYLESS","verificationStatus":"VERIFIED",
                     "signerIdentity":"https://github.com/actions/runner",
                     "reportedAt":"2026-11-05T10:00:00Z"}
                    """.formatted(APP_KEY, ARTIFACT_DIGEST, ARTIFACT_DIGEST)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void keyVerifiedRequiresPublicKeyFingerprint() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/artifact-verifications")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"applicationKey":"%s","artifactReference":"docker.io/example/app@sha256:%s",
                     "artifactDigest":"%s","verificationMode":"KEY","verificationStatus":"VERIFIED",
                     "signerIdentity":"CI Pipeline","reportedAt":"2026-11-05T10:00:00Z"}
                    """.formatted(APP_KEY, ARTIFACT_DIGEST, ARTIFACT_DIGEST)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void mutableImageTagRejected() throws Exception {
        // Missing @sha256: in the reference
        mockMvc.perform(post("/api/v1/integrations/artifact-verifications")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"applicationKey":"%s","artifactReference":"docker.io/example/app:latest",
                     "verificationMode":"KEYLESS","verificationStatus":"FAILED",
                     "reportedAt":"2026-11-05T10:00:00Z"}
                    """.formatted(APP_KEY)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void digestMismatchRejected() throws Exception {
        // artifactDigest in payload doesn't match digest in reference
        String otherDigest = "b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3";
        mockMvc.perform(post("/api/v1/integrations/artifact-verifications")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"applicationKey":"%s","artifactReference":"docker.io/example/app@sha256:%s",
                     "artifactDigest":"%s","verificationMode":"KEYLESS","verificationStatus":"FAILED",
                     "reportedAt":"2026-11-05T10:00:00Z"}
                    """.formatted(APP_KEY, ARTIFACT_DIGEST, otherDigest)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void failedVerificationRecorded() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/artifact-verifications")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"applicationKey":"%s","artifactReference":"docker.io/example/app@sha256:%s",
                     "verificationMode":"KEYLESS","verificationStatus":"FAILED",
                     "reportedAt":"2026-11-05T10:00:00Z"}
                    """.formatted(APP_KEY, ARTIFACT_DIGEST)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.verificationStatus").value("FAILED"));
    }

    @Test
    void verificationHistoryRetained() throws Exception {
        // First verification - FAILED
        mockMvc.perform(post("/api/v1/integrations/artifact-verifications")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"applicationKey":"%s","artifactReference":"docker.io/example/app@sha256:%s",
                     "verificationMode":"KEYLESS","verificationStatus":"FAILED",
                     "reportedAt":"2026-11-05T10:00:00Z"}
                    """.formatted(APP_KEY, ARTIFACT_DIGEST)))
            .andExpect(status().isCreated());

        // Second verification - VERIFIED (fix applied)
        mockMvc.perform(post("/api/v1/integrations/artifact-verifications")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(verificationPayloadKeyless()))
            .andExpect(status().isCreated());

        // Both records should exist
        assertEquals(2, verificationRepository.count(), "Both verification attempts should be retained");

        // Read history
        mockMvc.perform(get("/api/v1/applications/" + appId + "/artifact-verifications")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void readVerificationsWithJwt() throws Exception {
        ingestVerificationKeyless();
        mockMvc.perform(get("/api/v1/artifact-verifications")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void readVerificationsFilterByStatus() throws Exception {
        ingestVerificationKeyless();
        mockMvc.perform(get("/api/v1/artifact-verifications")
                .param("status", "VERIFIED")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void unauthenticatedVerificationReadRejected() throws Exception {
        mockMvc.perform(get("/api/v1/artifact-verifications"))
            .andExpect(status().isUnauthorized());
    }

    // ── Dashboard metrics ──────────────────────────────────────

    @Test
    void dashboardReflectsSbomMetrics() throws Exception {
        ingestSbom17();
        mockMvc.perform(get("/api/v1/dashboard/summary")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalSbomDocuments").value(1))
            .andExpect(jsonPath("$.totalSbomComponents").value(2))
            .andExpect(jsonPath("$.recentSbomDocuments").isArray())
            .andExpect(jsonPath("$.recentSbomDocuments.length()").value(1));
    }

    @Test
    void dashboardReflectsVerificationMetrics() throws Exception {
        ingestVerificationKeyless();
        mockMvc.perform(get("/api/v1/dashboard/summary")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verifiedArtifacts").value(1))
            .andExpect(jsonPath("$.failedArtifactVerifications").value(0))
            .andExpect(jsonPath("$.recentArtifactVerifications").isArray())
            .andExpect(jsonPath("$.recentArtifactVerifications.length()").value(1));
    }

    @Test
    void dashboardEmptySbomAndVerificationMetrics() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalSbomDocuments").value(0))
            .andExpect(jsonPath("$.totalSbomComponents").value(0))
            .andExpect(jsonPath("$.verifiedArtifacts").value(0))
            .andExpect(jsonPath("$.failedArtifactVerifications").value(0))
            .andExpect(jsonPath("$.recentSbomDocuments.length()").value(0))
            .andExpect(jsonPath("$.recentArtifactVerifications.length()").value(0));
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
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, TEST_PASSWORD)))
            .andExpect(status().isOk())
            .andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> map = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return (String) map.get("accessToken");
    }

    private MvcResult ingestSbom17() throws Exception {
        return mockMvc.perform(post("/api/v1/integrations/sboms")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sbomPayload17()))
            .andExpect(status().isCreated())
            .andReturn();
    }

    private void ingestVerificationKeyless() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/artifact-verifications")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(verificationPayloadKeyless()))
            .andExpect(status().isCreated());
    }

    @SuppressWarnings("unchecked")
    private String extractField(MvcResult result, String field) throws Exception {
        Map<String, Object> map = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return String.valueOf(map.get(field));
    }

    // ── Payload builders ───────────────────────────────────────

    /**
     * CycloneDX 1.7 payload with 2 real-world-like components.
     * This mirrors actual Syft output for a Java application.
     */
    private String sbomPayload17() {
        return sbomPayloadForApp(APP_KEY);
    }

    private String sbomPayloadForApp(String appKey) {
        return """
            {
              "applicationKey": "%s",
              "artifactDigest": "%s",
              "sbom": {
                "bomFormat": "CycloneDX",
                "specVersion": "1.7",
                "serialNumber": "urn:uuid:f81d4fae-7dec-11d0-a765-00a0c91e6bf6",
                "version": 1,
                "metadata": {
                  "timestamp": "2026-11-05T10:00:00Z",
                  "tools": [{"vendor": "anchore", "name": "syft", "version": "0.98.0"}],
                  "component": {
                    "type": "application",
                    "name": "test-app",
                    "version": "1.0.0",
                    "purl": "pkg:oci/test-app@sha256:%s"
                  }
                },
                "components": [
                  {
                    "bom-ref": "pkg:maven/org.springframework/spring-core@6.1.4",
                    "type": "library",
                    "group": "org.springframework",
                    "name": "spring-core",
                    "version": "6.1.4",
                    "purl": "pkg:maven/org.springframework/spring-core@6.1.4",
                    "hashes": [{"alg": "SHA-256", "content": "abc123"}],
                    "licenses": [{"license": {"id": "Apache-2.0"}}]
                  },
                  {
                    "bom-ref": "pkg:maven/com.fasterxml.jackson.core/jackson-databind@2.16.1",
                    "type": "library",
                    "group": "com.fasterxml.jackson.core",
                    "name": "jackson-databind",
                    "version": "2.16.1",
                    "purl": "pkg:maven/com.fasterxml.jackson.core/jackson-databind@2.16.1"
                  }
                ]
              }
            }
            """.formatted(appKey, ARTIFACT_DIGEST, ARTIFACT_DIGEST);
    }

    private String sbomPayload16() {
        return """
            {
              "applicationKey": "%s",
              "sbom": {
                "bomFormat": "CycloneDX",
                "specVersion": "1.6",
                "version": 1,
                "components": [
                  {
                    "type": "library",
                    "name": "logback-classic",
                    "version": "1.4.14",
                    "purl": "pkg:maven/ch.qos.logback/logback-classic@1.4.14"
                  }
                ]
              }
            }
            """.formatted(APP_KEY);
    }

    private String sbomPayloadWithPipeline() {
        return sbomPayloadWithPipelineId(pipelineRunId);
    }

    private String sbomPayloadWithPipelineId(UUID runId) {
        return """
            {
              "applicationKey": "%s",
              "pipelineRunId": "%s",
              "artifactDigest": "%s",
              "sbom": {
                "bomFormat": "CycloneDX",
                "specVersion": "1.7",
                "version": 1,
                "components": []
              }
            }
            """.formatted(APP_KEY, runId, ARTIFACT_DIGEST);
    }

    private String verificationPayloadKeyless() {
        return """
            {
              "applicationKey": "%s",
              "artifactReference": "docker.io/example/app@sha256:%s",
              "artifactDigest": "%s",
              "verificationMode": "KEYLESS",
              "verificationStatus": "VERIFIED",
              "signerIdentity": "https://github.com/actions/runner",
              "oidcIssuer": "https://token.actions.githubusercontent.com",
              "cosignVersion": "v2.2.4",
              "verifiedAt": "2026-11-05T10:00:00Z",
              "reportedAt": "2026-11-05T10:01:00Z",
              "verificationEvidence": {"certIssuer": "https://token.actions.githubusercontent.com"}
            }
            """.formatted(APP_KEY, ARTIFACT_DIGEST, ARTIFACT_DIGEST);
    }

    private String verificationPayloadKey() {
        return """
            {
              "applicationKey": "%s",
              "artifactReference": "docker.io/example/app@sha256:%s",
              "artifactDigest": "%s",
              "verificationMode": "KEY",
              "verificationStatus": "VERIFIED",
              "signerIdentity": "CI Pipeline",
              "publicKeyFingerprint": "%s",
              "cosignVersion": "v2.2.4",
              "verifiedAt": "2026-11-05T10:00:00Z",
              "reportedAt": "2026-11-05T10:01:00Z"
            }
            """.formatted(APP_KEY, ARTIFACT_DIGEST, ARTIFACT_DIGEST, ARTIFACT_DIGEST);
    }
}
