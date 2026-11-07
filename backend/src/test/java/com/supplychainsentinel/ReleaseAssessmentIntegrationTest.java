package com.supplychainsentinel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supplychainsentinel.entity.*;
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

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Phase 8 release risk assessment, approval workflow,
 * CI release gate, separation of duties, and audit trail.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReleaseAssessmentIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private PipelineRunRepository pipelineRunRepository;
    @Autowired private SecurityScanRepository scanRepository;
    @Autowired private SecurityFindingRepository findingRepository;
    @Autowired private SbomDocumentRepository sbomRepository;
    @Autowired private ArtifactVerificationRepository verificationRepository;

    private static final String TEST_PASSWORD = "TestPass123!";
    private static final String INGESTION_KEY = "test-ingestion-key-for-ci-pipeline-integration";
    private static final String INGESTION_HEADER = "X-CI-INGESTION-KEY";
    private static final String DIGEST = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2";

    private String adminToken;
    private String rmToken;
    private String devToken;
    private String auditorToken;
    private UUID adminUserId;
    private UUID appId;
    private UUID runId;

    @BeforeEach
    void setUp() throws Exception {
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        Role rmRole = roleRepository.findByName("RELEASE_MANAGER").orElseThrow();
        Role devRole = roleRepository.findByName("DEVELOPER").orElseThrow();
        Role auditorRole = roleRepository.findByName("AUDITOR").orElseThrow();

        User adminUser = createUser("rasadmin", adminRole);
        adminUserId = adminUser.getId();
        createUser("rasrm", rmRole);
        createUser("rasdev", devRole);
        createUser("rasauditor", auditorRole);

        adminToken = loginAndGetToken("rasadmin");
        rmToken = loginAndGetToken("rasrm");
        devToken = loginAndGetToken("rasdev");
        auditorToken = loginAndGetToken("rasauditor");

        // Create app
        InventoryApplication app = new InventoryApplication();
        app.setApplicationKey("ras-app");
        app.setName("Release Assessment App");
        app.setCriticality("HIGH");
        app.setLifecycleStatus("ACTIVE");
        app.setDataClassification("INTERNAL");
        app.setHostingEnvironment("AWS");
        app.setInternetFacing(false);
        applicationRepository.saveAndFlush(app);
        appId = app.getId();

        // Create a SUCCESS pipeline run with artifact digest
        PipelineRun run = new PipelineRun();
        run.setApplication(app);
        run.setProvider("GITHUB_ACTIONS");
        run.setExternalRunId("ras-run-1");
        run.setRepositoryUrl("https://github.com/org/repo");
        run.setBranchName("main");
        run.setCommitSha("abc123abc123abc123abc123abc123abc123abc123");
        run.setStatus("SUCCESS");
        run.setStartedAt(Instant.now());
        run.setArtifactDigest(DIGEST);
        pipelineRunRepository.saveAndFlush(run);
        runId = run.getId();
    }

    // ── Full PASS scenario ────────────────────────────────────

    /**
     * Seeds the minimum evidence for a PASS: scan, SBOM, Cosign verification, no critical findings.
     */
    private void seedPassEvidence() {
        seedScan();
        seedSbom();
        seedVerification();
    }

    private void seedScan() {
        InventoryApplication app = applicationRepository.findById(appId).orElseThrow();
        PipelineRun run = pipelineRunRepository.findById(runId).orElseThrow();
        SecurityScan scan = new SecurityScan();
        scan.setApplication(app);
        scan.setPipelineRun(run);
        scan.setScanner("trivy");
        scan.setReportFormat("TRIVY_JSON");
        scan.setScanTimestamp(Instant.now());
        scanRepository.saveAndFlush(scan);
    }

    private void seedSbom() {
        InventoryApplication app = applicationRepository.findById(appId).orElseThrow();
        PipelineRun run = pipelineRunRepository.findById(runId).orElseThrow();
        SbomDocument sbom = new SbomDocument();
        sbom.setApplication(app);
        sbom.setPipelineRun(run);
        sbom.setArtifactDigest(DIGEST);
        sbom.setFormat("CYCLONEDX_JSON");
        sbom.setSpecVersion("1.7");
        sbom.setDocumentSha256("abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890"); // 64 chars
        sbom.setComponentCount(10);
        sbom.setIngestedAt(Instant.now());
        sbomRepository.saveAndFlush(sbom);
    }

    private void seedVerification() {
        InventoryApplication app = applicationRepository.findById(appId).orElseThrow();
        PipelineRun run = pipelineRunRepository.findById(runId).orElseThrow();
        ArtifactVerification av = new ArtifactVerification();
        av.setApplication(app);
        av.setPipelineRun(run);
        av.setArtifactReference("ghcr.io/org/app@sha256:" + DIGEST);
        av.setArtifactDigest(DIGEST);
        av.setVerificationTool("COSIGN");
        av.setVerificationMode("KEYLESS");
        av.setVerificationStatus("VERIFIED");
        av.setSignerIdentity("https://github.com/org/app/.github/workflows/ci.yml");
        av.setOidcIssuer("https://token.actions.githubusercontent.com");
        av.setReportedAt(Instant.now());
        verificationRepository.saveAndFlush(av);
    }

    private void seedFinding(String severity, String status) {
        InventoryApplication app = applicationRepository.findById(appId).orElseThrow();
        SecurityFinding f = new SecurityFinding();
        f.setApplication(app);
        f.setFingerprint(UUID.randomUUID().toString());
        f.setScanner("trivy");
        f.setTitle("Test finding " + severity);
        f.setSeverity(severity);
        f.setStatus(status);
        f.setFirstSeen(Instant.now());
        f.setLastSeen(Instant.now());
        findingRepository.saveAndFlush(f);
    }

    // ── Assessment creation tests ─────────────────────────────

    @Test
    void passDecisionWithCompleteEvidence() throws Exception {
        seedPassEvidence();
        // No active CRITICAL findings, score = 15 (HIGH) + 0 (not internet) = 15 → PASS
        String body = "{\"applicationId\":\"%s\",\"pipelineRunId\":\"%s\"}".formatted(appId, runId);
        mockMvc.perform(post("/api/v1/release-assessments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.decision").value("PASS"))
            .andExpect(jsonPath("$.policyVersion").value("SCS-RELEASE-POLICY-V1"))
            .andExpect(jsonPath("$.riskScore").value(15))
            .andExpect(jsonPath("$.hardBlockReasons").exists());
    }

    @Test
    void internetFacingAdds10Points() throws Exception {
        // Set app to internet-facing (HIGH + 10 internet = 25 → WARN)
        InventoryApplication app = applicationRepository.findById(appId).orElseThrow();
        app.setInternetFacing(true);
        applicationRepository.saveAndFlush(app);
        seedPassEvidence();

        String body = "{\"applicationId\":\"%s\",\"pipelineRunId\":\"%s\"}".formatted(appId, runId);
        mockMvc.perform(post("/api/v1/release-assessments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.riskScore").value(25))
            .andExpect(jsonPath("$.decision").value("WARN"));
    }

    @Test
    void criticalApplicationAdds20Points() throws Exception {
        InventoryApplication app = applicationRepository.findById(appId).orElseThrow();
        app.setCriticality("CRITICAL");
        applicationRepository.saveAndFlush(app);
        seedPassEvidence();

        String body = "{\"applicationId\":\"%s\",\"pipelineRunId\":\"%s\"}".formatted(appId, runId);
        mockMvc.perform(post("/api/v1/release-assessments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.riskScore").value(20));
    }

    @Test
    void highVulnerabilityAddsToScore() throws Exception {
        seedPassEvidence();
        seedFinding("HIGH", "OPEN");
        // 15 (HIGH) + 15 (HIGH vuln) = 30 → WARN
        String body = "{\"applicationId\":\"%s\",\"pipelineRunId\":\"%s\"}".formatted(appId, runId);
        mockMvc.perform(post("/api/v1/release-assessments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.decision").value("WARN"));
    }

    @Test
    void remediatedFindingExcludedFromScore() throws Exception {
        seedPassEvidence();
        seedFinding("HIGH", "REMEDIATED"); // must not add points
        String body = "{\"applicationId\":\"%s\",\"pipelineRunId\":\"%s\"}".formatted(appId, runId);
        mockMvc.perform(post("/api/v1/release-assessments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.riskScore").value(15))
            .andExpect(jsonPath("$.decision").value("PASS"));
    }

    @Test
    void acknowledgedFindingIncludedInScore() throws Exception {
        seedPassEvidence();
        seedFinding("HIGH", "ACKNOWLEDGED");
        String body = "{\"applicationId\":\"%s\",\"pipelineRunId\":\"%s\"}".formatted(appId, runId);
        mockMvc.perform(post("/api/v1/release-assessments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.riskScore").value(30)); // 15 + 15
    }

    @Test
    void scoreCapAt100() throws Exception {
        seedPassEvidence();
        // Seed many critical findings to exceed 100
        for (int i = 0; i < 5; i++) seedFinding("CRITICAL", "OPEN");
        String body = "{\"applicationId\":\"%s\",\"pipelineRunId\":\"%s\"}".formatted(appId, runId);
        mockMvc.perform(post("/api/v1/release-assessments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.riskScore").value(100));
    }

    // ── Hard block tests ──────────────────────────────────────

    @Test
    void blockedWhenPipelineNotSuccess() throws Exception {
        PipelineRun run = pipelineRunRepository.findById(runId).orElseThrow();
        run.setStatus("FAILED");
        pipelineRunRepository.saveAndFlush(run);
        seedSbom(); seedVerification(); seedScan();

        String body = "{\"applicationId\":\"%s\",\"pipelineRunId\":\"%s\"}".formatted(appId, runId);
        mockMvc.perform(post("/api/v1/release-assessments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.decision").value("BLOCK"));
    }

    @Test
    void blockedWhenMissingDigest() throws Exception {
        PipelineRun run = pipelineRunRepository.findById(runId).orElseThrow();
        run.setArtifactDigest(null);
        pipelineRunRepository.saveAndFlush(run);
        seedScan(); seedSbom(); seedVerification();

        String body = "{\"applicationId\":\"%s\",\"pipelineRunId\":\"%s\"}".formatted(appId, runId);
        mockMvc.perform(post("/api/v1/release-assessments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.decision").value("BLOCK"));
    }

    @Test
    void blockedWhenNoSbom() throws Exception {
        seedScan(); seedVerification();
        // No SBOM
        String body = "{\"applicationId\":\"%s\",\"pipelineRunId\":\"%s\"}".formatted(appId, runId);
        mockMvc.perform(post("/api/v1/release-assessments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.decision").value("BLOCK"));
    }

    @Test
    void blockedWhenNoCosignVerification() throws Exception {
        seedScan(); seedSbom();
        // No verification
        String body = "{\"applicationId\":\"%s\",\"pipelineRunId\":\"%s\"}".formatted(appId, runId);
        mockMvc.perform(post("/api/v1/release-assessments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.decision").value("BLOCK"));
    }

    @Test
    void blockedWhenFailedCosignVerification() throws Exception {
        seedScan(); seedSbom();
        // Add FAILED verification (not VERIFIED)
        InventoryApplication app = applicationRepository.findById(appId).orElseThrow();
        PipelineRun run = pipelineRunRepository.findById(runId).orElseThrow();
        ArtifactVerification av = new ArtifactVerification();
        av.setApplication(app); av.setPipelineRun(run);
        av.setArtifactReference("ghcr.io/org/app@sha256:" + DIGEST);
        av.setArtifactDigest(DIGEST);
        av.setVerificationTool("COSIGN"); av.setVerificationMode("KEYLESS");
        av.setVerificationStatus("FAILED");
        av.setReportedAt(Instant.now());
        verificationRepository.saveAndFlush(av);

        String body = "{\"applicationId\":\"%s\",\"pipelineRunId\":\"%s\"}".formatted(appId, runId);
        mockMvc.perform(post("/api/v1/release-assessments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.decision").value("BLOCK"));
    }

    @Test
    void blockedWhenNoSecurityScan() throws Exception {
        seedSbom(); seedVerification();
        // No scan
        String body = "{\"applicationId\":\"%s\",\"pipelineRunId\":\"%s\"}".formatted(appId, runId);
        mockMvc.perform(post("/api/v1/release-assessments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.decision").value("BLOCK"));
    }

    @Test
    void blockedWhenActiveCriticalFinding() throws Exception {
        seedPassEvidence();
        seedFinding("CRITICAL", "OPEN");

        String body = "{\"applicationId\":\"%s\",\"pipelineRunId\":\"%s\"}".formatted(appId, runId);
        mockMvc.perform(post("/api/v1/release-assessments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.decision").value("BLOCK"));
    }

    // ── Read endpoints ────────────────────────────────────────

    @Test
    void allRolesCanReadAssessments() throws Exception {
        seedPassEvidence();
        createAssessmentAndGetId(adminToken);
        for (String tok : new String[]{adminToken, rmToken, devToken, auditorToken}) {
            mockMvc.perform(get("/api/v1/release-assessments")
                    .header("Authorization", "Bearer " + tok))
                .andExpect(status().isOk());
        }
    }

    @Test
    void unauthenticatedReadRejected() throws Exception {
        mockMvc.perform(get("/api/v1/release-assessments"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void filterByDecision() throws Exception {
        seedPassEvidence();
        createAssessmentAndGetId(adminToken);
        mockMvc.perform(get("/api/v1/release-assessments?decision=PASS")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ── Approval tests ────────────────────────────────────────

    @Test
    void adminCanApprovePassAssessment() throws Exception {
        seedPassEvidence();
        String id = createAssessmentAndGetId(rmToken); // RM creates, admin approves

        mockMvc.perform(post("/api/v1/release-assessments/" + id + "/approvals")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVED\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.decision").value("APPROVED"));
    }

    @Test
    void releaseManagerCanApprove() throws Exception {
        seedPassEvidence();
        String id = createAssessmentAndGetId(adminToken); // admin creates, RM approves

        mockMvc.perform(post("/api/v1/release-assessments/" + id + "/approvals")
                .header("Authorization", "Bearer " + rmToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVED\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void developerCannotApprove() throws Exception {
        seedPassEvidence();
        String id = createAssessmentAndGetId(adminToken);

        mockMvc.perform(post("/api/v1/release-assessments/" + id + "/approvals")
                .header("Authorization", "Bearer " + devToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVED\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void requesterCannotApproveOwnAssessment() throws Exception {
        seedPassEvidence();
        String id = createAssessmentAndGetId(adminToken); // admin creates

        // Admin tries to approve their own
        mockMvc.perform(post("/api/v1/release-assessments/" + id + "/approvals")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVED\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void warnRequiresJustification() throws Exception {
        // Make WARN: HIGH + internet-facing (15 + 10 = 25)
        InventoryApplication app = applicationRepository.findById(appId).orElseThrow();
        app.setInternetFacing(true);
        applicationRepository.saveAndFlush(app);
        seedPassEvidence();
        String id = createAssessmentAndGetId(adminToken);

        // Approve without justification → should fail
        mockMvc.perform(post("/api/v1/release-assessments/" + id + "/approvals")
                .header("Authorization", "Bearer " + rmToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVED\"}"))
            .andExpect(status().isBadRequest());

        // With justification → should succeed
        mockMvc.perform(post("/api/v1/release-assessments/" + id + "/approvals")
                .header("Authorization", "Bearer " + rmToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVED\",\"justification\":\"Risk accepted — monitored deployment\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void blockAssessmentCannotBeApproved() throws Exception {
        // No evidence → BLOCK
        String id = createAssessmentAndGetId(adminToken);

        mockMvc.perform(post("/api/v1/release-assessments/" + id + "/approvals")
                .header("Authorization", "Bearer " + rmToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVED\",\"justification\":\"Override\"}"))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void duplicateApprovalRejected() throws Exception {
        seedPassEvidence();
        String id = createAssessmentAndGetId(adminToken);

        mockMvc.perform(post("/api/v1/release-assessments/" + id + "/approvals")
                .header("Authorization", "Bearer " + rmToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVED\"}"))
            .andExpect(status().isCreated());

        // Second approval from same person
        mockMvc.perform(post("/api/v1/release-assessments/" + id + "/approvals")
                .header("Authorization", "Bearer " + rmToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVED\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void approvedAssessmentStatusBecomesReadyForDeployment() throws Exception {
        seedPassEvidence();
        String id = createAssessmentAndGetId(adminToken);

        mockMvc.perform(post("/api/v1/release-assessments/" + id + "/approvals")
                .header("Authorization", "Bearer " + rmToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVED\"}"))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/release-assessments/" + id)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.assessmentStatus").value("READY_FOR_DEPLOYMENT"));
    }

    @Test
    void rejectedAssessmentStatusBecomesRejected() throws Exception {
        seedPassEvidence();
        String id = createAssessmentAndGetId(adminToken);

        mockMvc.perform(post("/api/v1/release-assessments/" + id + "/approvals")
                .header("Authorization", "Bearer " + rmToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"REJECTED\",\"justification\":\"Not ready\"}"))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/release-assessments/" + id)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.assessmentStatus").value("REJECTED"));
    }

    // ── CI release gate ────────────────────────────────────────

    @Test
    void ciReleaseGatePassWithCompleteEvidence() throws Exception {
        seedPassEvidence();
        String body = "{\"applicationKey\":\"ras-app\",\"pipelineRunId\":\"%s\"}".formatted(runId);
        mockMvc.perform(post("/api/v1/integrations/release-gates/evaluate")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.decision").value("PASS"))
            .andExpect(jsonPath("$.assessmentId").isNotEmpty());
    }

    @Test
    void ciReleaseGateBlockWithoutEvidence() throws Exception {
        String body = "{\"applicationKey\":\"ras-app\",\"pipelineRunId\":\"%s\"}".formatted(runId);
        mockMvc.perform(post("/api/v1/integrations/release-gates/evaluate")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.decision").value("BLOCK"))
            .andExpect(jsonPath("$.hardBlockReasons").isArray());
    }

    @Test
    void ciReleaseGateInvalidKeyRejected() throws Exception {
        String body = "{\"applicationKey\":\"ras-app\",\"pipelineRunId\":\"%s\"}".formatted(runId);
        mockMvc.perform(post("/api/v1/integrations/release-gates/evaluate")
                .header(INGESTION_HEADER, "wrong-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void ciReleaseGateAudited() throws Exception {
        seedPassEvidence();
        String body = "{\"applicationKey\":\"ras-app\",\"pipelineRunId\":\"%s\"}".formatted(runId);
        MvcResult result = mockMvc.perform(post("/api/v1/integrations/release-gates/evaluate")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> resp = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        String assessmentId = (String) resp.get("assessmentId");

        // Audit trail must contain the CI_GATE_EVALUATED event
        mockMvc.perform(get("/api/v1/release-assessments/" + assessmentId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.auditHistory").isArray())
            .andExpect(jsonPath("$.auditHistory.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    // ── Dashboard metrics ──────────────────────────────────────

    @Test
    void dashboardReflectsAssessmentMetrics() throws Exception {
        seedPassEvidence();
        createAssessmentAndGetId(adminToken);

        mockMvc.perform(get("/api/v1/dashboard/summary")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalReleaseAssessments").value(1))
            .andExpect(jsonPath("$.passAssessments").value(1))
            .andExpect(jsonPath("$.recentReleaseAssessments").isArray())
            .andExpect(jsonPath("$.recentReleaseAssessments.length()").value(1));
    }

    @Test
    void dashboardEmptyAssessments() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalReleaseAssessments").value(0))
            .andExpect(jsonPath("$.recentReleaseAssessments.length()").value(0));
    }

    // ── Helpers ────────────────────────────────────────────────

    private User createUser(String username, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@test.local");
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
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, TEST_PASSWORD)))
            .andExpect(status().isOk())
            .andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> map = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return (String) map.get("accessToken");
    }

    private String createAssessmentAndGetId(String token) throws Exception {
        String body = "{\"applicationId\":\"%s\",\"pipelineRunId\":\"%s\"}".formatted(appId, runId);
        MvcResult result = mockMvc.perform(post("/api/v1/release-assessments")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> map = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return (String) map.get("id");
    }
}
