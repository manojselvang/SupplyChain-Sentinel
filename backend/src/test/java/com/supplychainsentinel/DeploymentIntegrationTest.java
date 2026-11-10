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
 * Integration tests for Argo CD deployment ingestion.
 *
 * <h2>One sync operation = one DeploymentRecord</h2>
 * All Argo CD triggers for the same operation (sync-succeeded, health-degraded, etc.)
 * use the same {@code externalEventId = argocd-<uid>-<operationState.startedAt>}.
 * A health-degraded follow-up therefore updates the existing record, not creates a new one.
 *
 * <p>Covered:
 * <ul>
 *   <li>Basic create / read / auth / field validation
 *   <li>Sync-success followed by health-degraded updates the SAME record (count stays 1)
 *   <li>Duplicate notification delivery is idempotent (200 OK, immutable fields preserved)
 *   <li>New sync operation at the SAME Git revision but different startedAt → new record
 *   <li>Release assessment digest match and mismatch (explicit and auto-correlation)
 *   <li>Invalid imageDigest format rejected with 400
 *   <li>Missing optional operationState fields accepted
 *   <li>Dashboard KPI reflection
 * </ul>
 *
 * <p>NOTE: These tests use MockMvc. No real Argo CD notification has been sent
 * to a running Sentinel backend; live integration testing requires a real cluster.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DeploymentIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private PipelineRunRepository pipelineRunRepository;
    @Autowired private ReleaseAssessmentRepository assessmentRepository;

    private static final String TEST_PASSWORD  = "TestPass123!";
    private static final String INGESTION_KEY   = "test-ingestion-key-for-ci-pipeline-integration";
    private static final String INGESTION_HEADER = "X-CI-INGESTION-KEY";
    // Valid 64-char hex SHA-256 digest
    private static final String DIGEST       = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2";
    // Different digest for mismatch tests
    private static final String OTHER_DIGEST = "b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3";

    private String adminToken;
    private UUID appId;
    private UUID pipelineRunId;

    @BeforeEach
    void setUp() throws Exception {
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        createUser("deployadmin", adminRole);
        adminToken = loginAndGetToken("deployadmin");

        InventoryApplication app = new InventoryApplication();
        app.setApplicationKey("deploy-app");
        app.setName("Deploy Test App");
        app.setCriticality("HIGH");
        app.setLifecycleStatus("ACTIVE");
        app.setDataClassification("INTERNAL");
        app.setHostingEnvironment("AWS");
        applicationRepository.saveAndFlush(app);
        appId = app.getId();

        PipelineRun run = new PipelineRun();
        run.setApplication(app);
        run.setProvider("GITHUB_ACTIONS");
        run.setExternalRunId("deploy-run-1");
        run.setRepositoryUrl("https://github.com/org/repo");
        run.setBranchName("main");
        run.setCommitSha("abc123abc123abc123abc123abc123abc123abc123");
        run.setStatus("SUCCESS");
        run.setStartedAt(Instant.now());
        run.setArtifactDigest(DIGEST);
        pipelineRunRepository.saveAndFlush(run);
        pipelineRunId = run.getId();
    }

    // ── Basic ingestion ───────────────────────────────────────

    @Test
    void validArgocdWebhookSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/deployments")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(deploymentPayload("argocd-uid-abc-2026-11-08T12:00:00Z", "SYNCED", "HEALTHY", "production")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.argocdApplication").value("payment-service"))
            .andExpect(jsonPath("$.environment").value("production"))
            .andExpect(jsonPath("$.syncStatus").value("SYNCED"))
            .andExpect(jsonPath("$.healthStatus").value("HEALTHY"))
            .andExpect(jsonPath("$.applicationName").value("Deploy Test App"));
    }

    @Test
    void syncFailedEvent() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/deployments")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(deploymentPayload("argocd-uid-abc-2026-11-08T12:00:00Z-fail", "OUT_OF_SYNC", "DEGRADED", "staging")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.syncStatus").value("OUT_OF_SYNC"))
            .andExpect(jsonPath("$.healthStatus").value("DEGRADED"));
    }

    // ── One sync operation = one DeploymentRecord ──────────────

    /**
     * Sync-succeeded notification creates the record; health-degraded notification
     * arrives later for the same operation. Both must carry the SAME externalEventId
     * (argocd-<uid>-<startedAt>) so the second notification updates the existing
     * record instead of creating a duplicate.
     *
     * Verifies:
     * - First notification → 201 Created
     * - Second notification → 200 OK (existing record updated)
     * - Total deployment count remains 1
     * - healthStatus updated to DEGRADED
     * - Immutable facts (imageDigest, gitRevision, deployedAt) unchanged
     */
    @Test
    void syncSuccessFollowedByHealthDegradedUpdatesSameRecord() throws Exception {
        // Shared externalEventId for the entire operation — derived from app UID + operationState.startedAt
        final String operationEventId = "argocd-3f8a1c2d-e456-7890-abcd-ef1234567890-2026-11-08T12:00:00Z";
        final String deployedAt       = "2026-11-08T12:00:00Z";

        // First notification: sync succeeded
        String syncSuccessBody = """
            {
                "applicationKey": "deploy-app",
                "argocdApplication": "payment-service",
                "environment": "production",
                "imageReference": "registry/app@sha256:%s",
                "imageDigest": "%s",
                "gitRevision": "abc123def456",
                "syncStatus": "SYNCED",
                "healthStatus": "HEALTHY",
                "operationPhase": "Succeeded",
                "externalEventId": "%s",
                "deployedAt": "%s",
                "completedAt": "2026-11-08T12:01:30Z"
            }
            """.formatted(DIGEST, DIGEST, operationEventId, deployedAt);

        mockMvc.perform(post("/api/v1/integrations/deployments")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(syncSuccessBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.syncStatus").value("SYNCED"))
            .andExpect(jsonPath("$.healthStatus").value("HEALTHY"));

        // Second notification: health degraded — SAME externalEventId
        String healthDegradedBody = """
            {
                "applicationKey": "deploy-app",
                "argocdApplication": "payment-service",
                "environment": "production",
                "imageReference": "registry/app@sha256:%s",
                "imageDigest": "%s",
                "gitRevision": "abc123def456",
                "syncStatus": "SYNCED",
                "healthStatus": "DEGRADED",
                "operationPhase": "Succeeded",
                "externalEventId": "%s",
                "deployedAt": "%s"
            }
            """.formatted(DIGEST, DIGEST, operationEventId, deployedAt);

        mockMvc.perform(post("/api/v1/integrations/deployments")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(healthDegradedBody))
            .andExpect(status().isOk())               // 200 — updated, not created
            .andExpect(jsonPath("$.healthStatus").value("DEGRADED"))
            .andExpect(jsonPath("$.syncStatus").value("SYNCED"))
            // Immutable facts unchanged
            .andExpect(jsonPath("$.imageDigest").value(DIGEST))
            .andExpect(jsonPath("$.gitRevision").value("abc123def456"))
            .andExpect(jsonPath("$.deployedAt").value(deployedAt));

        // Exactly one deployment record for this operation
        mockMvc.perform(get("/api/v1/applications/" + appId + "/deployments")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ── Duplicate delivery idempotency ─────────────────────────

    /**
     * Sending the exact same notification twice (webhook retry) returns 200 OK
     * and does not create a second record.
     */
    @Test
    void duplicateWebhookIsIdempotent() throws Exception {
        final String eventId = "argocd-uid-dup-2026-11-08T12:00:00Z";

        mockMvc.perform(post("/api/v1/integrations/deployments")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(deploymentPayload(eventId, "PENDING", "PROGRESSING", "production")))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/integrations/deployments")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(deploymentPayload(eventId, "SYNCED", "HEALTHY", "production")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.syncStatus").value("SYNCED"))
            .andExpect(jsonPath("$.healthStatus").value("HEALTHY"));
    }

    /**
     * A retry that carries different immutable fields (imageDigest, gitRevision,
     * deployedAt) must not overwrite the values from the first delivery.
     */
    @Test
    void duplicateWebhookPreservesImmutableFields() throws Exception {
        final String eventId       = "argocd-uid-preserve-2026-11-08T10:00:00Z";
        final String firstDeployedAt = "2026-11-08T10:00:00Z";

        String body1 = """
            {
                "applicationKey": "deploy-app",
                "argocdApplication": "payment-service",
                "environment": "production",
                "imageReference": "registry/app@sha256:%s",
                "imageDigest": "%s",
                "syncStatus": "PENDING",
                "healthStatus": "PROGRESSING",
                "gitRevision": "rev-original",
                "externalEventId": "%s",
                "deployedAt": "%s"
            }
            """.formatted(DIGEST, DIGEST, eventId, firstDeployedAt);

        mockMvc.perform(post("/api/v1/integrations/deployments")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body1))
            .andExpect(status().isCreated());

        // Retry arrives with stale / different immutable values — must be ignored
        String body2 = """
            {
                "applicationKey": "deploy-app",
                "argocdApplication": "payment-service",
                "environment": "production",
                "imageReference": "registry/app@sha256:%s",
                "imageDigest": "%s",
                "syncStatus": "SYNCED",
                "healthStatus": "HEALTHY",
                "gitRevision": "rev-should-not-overwrite",
                "externalEventId": "%s",
                "deployedAt": "2026-11-08T11:00:00Z"
            }
            """.formatted(OTHER_DIGEST, OTHER_DIGEST, eventId);

        mockMvc.perform(post("/api/v1/integrations/deployments")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body2))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.syncStatus").value("SYNCED"))
            .andExpect(jsonPath("$.healthStatus").value("HEALTHY"))
            // Immutable fields from first delivery preserved
            .andExpect(jsonPath("$.imageDigest").value(DIGEST))
            .andExpect(jsonPath("$.gitRevision").value("rev-original"))
            .andExpect(jsonPath("$.deployedAt").value(firstDeployedAt));
    }

    // ── New operation = new record ─────────────────────────────

    /**
     * A second sync operation on the SAME Git revision but with a different
     * operationState.startedAt generates a different externalEventId and must
     * produce a new DeploymentRecord.
     */
    @Test
    void newSyncOperationDifferentStartedAtCreatesNewRecord() throws Exception {
        // First operation: started at T1
        mockMvc.perform(post("/api/v1/integrations/deployments")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(deploymentPayloadWithEventId(
                        "argocd-uid-abc-2026-11-08T12:00:00Z", "abc123", "SYNCED", "HEALTHY")))
            .andExpect(status().isCreated());

        // Second operation on the same revision: started at T2 (different ID)
        mockMvc.perform(post("/api/v1/integrations/deployments")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(deploymentPayloadWithEventId(
                        "argocd-uid-abc-2026-11-08T14:30:00Z", "abc123", "SYNCED", "HEALTHY")))
            .andExpect(status().isCreated());  // 201, not 200

        mockMvc.perform(get("/api/v1/applications/" + appId + "/deployments")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2));
    }

    // ── Auth ──────────────────────────────────────────────────

    @Test
    void missingIngestionKeyRejected() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/deployments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(deploymentPayload("argocd-uid-nokey-2026-11-08T12:00:00Z", "SYNCED", "HEALTHY", "production")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidIngestionKeyRejected() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/deployments")
                .header(INGESTION_HEADER, "wrong-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(deploymentPayload("argocd-uid-badkey-2026-11-08T12:00:00Z", "SYNCED", "HEALTHY", "production")))
            .andExpect(status().isUnauthorized());
    }

    // ── Validation ────────────────────────────────────────────

    @Test
    void unknownApplicationKeyRejected() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/deployments")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(deploymentPayloadForApp("no-such-app", "argocd-uid-unknown-2026-11-08T12:00:00Z",
                        "SYNCED", "HEALTHY", "production")))
            .andExpect(status().isNotFound());
    }

    @Test
    void missingRequiredFieldsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/deployments")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"applicationKey":"deploy-app","environment":"production",
                     "imageReference":"registry/app:latest","externalEventId":"argocd-uid-x-2026-11-08T12:00:00Z"}
                    """))
            .andExpect(status().isBadRequest());
    }

    /** imageDigest with sha256: prefix rejected — only 64-char hex accepted. */
    @Test
    void invalidImageDigestFormatRejected() throws Exception {
        String body = """
            {
                "applicationKey": "deploy-app",
                "argocdApplication": "payment-service",
                "environment": "production",
                "imageReference": "registry/app@sha256:%s",
                "imageDigest": "sha256:%s",
                "syncStatus": "SYNCED",
                "healthStatus": "HEALTHY",
                "externalEventId": "argocd-uid-baddigest-2026-11-08T12:00:00Z"
            }
            """.formatted(DIGEST, DIGEST);

        mockMvc.perform(post("/api/v1/integrations/deployments")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    /** imageDigest shorter than 64 characters rejected. */
    @Test
    void shortImageDigestRejected() throws Exception {
        String body = """
            {
                "applicationKey": "deploy-app",
                "argocdApplication": "payment-service",
                "environment": "production",
                "imageReference": "registry/app:latest",
                "imageDigest": "abc123",
                "syncStatus": "SYNCED",
                "healthStatus": "HEALTHY",
                "externalEventId": "argocd-uid-shortdigest-2026-11-08T12:00:00Z"
            }
            """;

        mockMvc.perform(post("/api/v1/integrations/deployments")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    /**
     * Missing optional operationState fields (gitRevision, operationPhase,
     * deployedAt, completedAt) must be accepted. deployedAt defaults server-side.
     */
    @Test
    void missingOptionalOperationStateFieldsAccepted() throws Exception {
        String body = """
            {
                "applicationKey": "deploy-app",
                "argocdApplication": "payment-service",
                "environment": "production",
                "imageReference": "registry/app:latest",
                "syncStatus": "SYNCED",
                "healthStatus": "HEALTHY",
                "externalEventId": "argocd-uid-noopstate-2026-11-08T12:00:00Z"
            }
            """;

        mockMvc.perform(post("/api/v1/integrations/deployments")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.gitRevision").doesNotExist())
            .andExpect(jsonPath("$.operationPhase").doesNotExist())
            .andExpect(jsonPath("$.deployedAt").isNotEmpty());  // defaulted server-side
    }

    // ── Pipeline association ───────────────────────────────────

    @Test
    void deploymentLinkedToPipelineRun() throws Exception {
        String body = """
            {
                "applicationKey": "deploy-app",
                "pipelineRunId": "%s",
                "argocdApplication": "payment-service",
                "environment": "production",
                "cluster": "prod-cluster",
                "namespace": "payments",
                "imageReference": "registry/app@sha256:%s",
                "imageDigest": "%s",
                "syncStatus": "SYNCED",
                "healthStatus": "HEALTHY",
                "externalEventId": "argocd-uid-linked-2026-11-08T12:00:00Z",
                "deployedAt": "2026-11-08T12:00:00Z"
            }
            """.formatted(pipelineRunId, DIGEST, DIGEST);

        mockMvc.perform(post("/api/v1/integrations/deployments")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.pipelineRunId").value(pipelineRunId.toString()));
    }

    @Test
    void invalidPipelineAssociationRejected() throws Exception {
        String body = """
            {
                "applicationKey": "deploy-app",
                "pipelineRunId": "%s",
                "argocdApplication": "payment-service",
                "environment": "production",
                "imageReference": "registry/app@sha256:%s",
                "syncStatus": "SYNCED",
                "healthStatus": "HEALTHY",
                "externalEventId": "argocd-uid-badpipe-2026-11-08T12:00:00Z"
            }
            """.formatted(UUID.randomUUID(), DIGEST);

        mockMvc.perform(post("/api/v1/integrations/deployments")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    // ── Release digest correlation ─────────────────────────────

    /**
     * Explicit releaseAssessmentId + matching imageDigest → digestMatchesAssessment true.
     */
    @Test
    void explicitAssessmentIdWithMatchingDigestReturnsTrue() throws Exception {
        UUID assessmentId = createAssessmentWithDigest(DIGEST);

        String body = """
            {
                "applicationKey": "deploy-app",
                "releaseAssessmentId": "%s",
                "argocdApplication": "payment-service",
                "environment": "production",
                "imageReference": "registry/app@sha256:%s",
                "imageDigest": "%s",
                "syncStatus": "SYNCED",
                "healthStatus": "HEALTHY",
                "externalEventId": "argocd-uid-digmatch-2026-11-08T12:00:00Z"
            }
            """.formatted(assessmentId, DIGEST, DIGEST);

        mockMvc.perform(post("/api/v1/integrations/deployments")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.releaseAssessmentId").value(assessmentId.toString()))
            .andExpect(jsonPath("$.digestMatchesAssessment").value(true));
    }

    /**
     * Explicit releaseAssessmentId + imageDigest that differs from assessment
     * artifactDigest → digestMatchesAssessment false.
     */
    @Test
    void explicitAssessmentIdWithMismatchedDigestReturnsFalse() throws Exception {
        UUID assessmentId = createAssessmentWithDigest(OTHER_DIGEST);

        String body = """
            {
                "applicationKey": "deploy-app",
                "releaseAssessmentId": "%s",
                "argocdApplication": "payment-service",
                "environment": "production",
                "imageReference": "registry/app@sha256:%s",
                "imageDigest": "%s",
                "syncStatus": "SYNCED",
                "healthStatus": "HEALTHY",
                "externalEventId": "argocd-uid-digmismatch-2026-11-08T12:00:00Z"
            }
            """.formatted(assessmentId, DIGEST, DIGEST);

        mockMvc.perform(post("/api/v1/integrations/deployments")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.releaseAssessmentId").value(assessmentId.toString()))
            .andExpect(jsonPath("$.digestMatchesAssessment").value(false));
    }

    /**
     * No releaseAssessmentId supplied + imageDigest matches an existing assessment
     * → auto-correlation links it.
     */
    @Test
    void autoCorrelationByDigestLinksAssessment() throws Exception {
        UUID assessmentId = createAssessmentWithDigest(DIGEST);

        String body = """
            {
                "applicationKey": "deploy-app",
                "argocdApplication": "payment-service",
                "environment": "production",
                "imageReference": "registry/app@sha256:%s",
                "imageDigest": "%s",
                "syncStatus": "SYNCED",
                "healthStatus": "HEALTHY",
                "externalEventId": "argocd-uid-autocorr-2026-11-08T12:00:00Z"
            }
            """.formatted(DIGEST, DIGEST);

        mockMvc.perform(post("/api/v1/integrations/deployments")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.releaseAssessmentId").value(assessmentId.toString()))
            .andExpect(jsonPath("$.digestMatchesAssessment").value(true));
    }

    /**
     * No releaseAssessmentId + no matching assessment → field absent, false.
     */
    @Test
    void autoCorrelationWithNoMatchingAssessmentLeavesNullId() throws Exception {
        createAssessmentWithDigest(OTHER_DIGEST);  // different digest — won't match

        String body = """
            {
                "applicationKey": "deploy-app",
                "argocdApplication": "payment-service",
                "environment": "production",
                "imageReference": "registry/app@sha256:%s",
                "imageDigest": "%s",
                "syncStatus": "SYNCED",
                "healthStatus": "HEALTHY",
                "externalEventId": "argocd-uid-nomatch-2026-11-08T12:00:00Z"
            }
            """.formatted(DIGEST, DIGEST);

        mockMvc.perform(post("/api/v1/integrations/deployments")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.releaseAssessmentId").doesNotExist())
            .andExpect(jsonPath("$.digestMatchesAssessment").value(false));
    }

    // ── Read endpoints ─────────────────────────────────────────

    @Test
    void listDeploymentsWithJwt() throws Exception {
        ingestDeployment("argocd-uid-list-2026-11-08T12:00:00Z", "SYNCED", "HEALTHY", "production");
        mockMvc.perform(get("/api/v1/deployments")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void filterBySyncStatus() throws Exception {
        ingestDeployment("argocd-uid-fsync1-2026-11-08T12:00:00Z", "SYNCED",      "HEALTHY",  "production");
        ingestDeployment("argocd-uid-fsync2-2026-11-08T12:00:00Z", "ERROR",       "DEGRADED", "staging");
        mockMvc.perform(get("/api/v1/deployments?syncStatus=ERROR")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].syncStatus").value("ERROR"));
    }

    @Test
    void applicationDeploymentHistory() throws Exception {
        ingestDeployment("argocd-uid-hist1-2026-11-08T12:00:00Z", "SYNCED",     "HEALTHY", "production");
        ingestDeployment("argocd-uid-hist2-2026-11-08T14:00:00Z", "OUT_OF_SYNC","HEALTHY", "staging");
        mockMvc.perform(get("/api/v1/applications/" + appId + "/deployments")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void unauthenticatedReadRejected() throws Exception {
        mockMvc.perform(get("/api/v1/deployments"))
            .andExpect(status().isUnauthorized());
    }

    // ── Dashboard ─────────────────────────────────────────────

    @Test
    void dashboardReflectsDeploymentMetrics() throws Exception {
        ingestDeployment("argocd-uid-dash1-2026-11-08T12:00:00Z", "SYNCED", "HEALTHY", "production");
        mockMvc.perform(get("/api/v1/dashboard/summary")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalDeployments").value(1))
            .andExpect(jsonPath("$.successfulDeployments").value(1))
            .andExpect(jsonPath("$.recentDeployments").isArray())
            .andExpect(jsonPath("$.recentDeployments.length()").value(1));
    }

    @Test
    void dashboardEmptyDeploymentMetrics() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalDeployments").value(0))
            .andExpect(jsonPath("$.recentDeployments.length()").value(0));
    }

    // ── Helpers ────────────────────────────────────────────────

    /**
     * Create a ReleaseAssessment for "deploy-app" with the given artifactDigest.
     * decision must be PASS|WARN|BLOCK; assessmentStatus must be
     * PENDING_APPROVAL|READY_FOR_DEPLOYMENT|REJECTED|BLOCKED (V8 CHECK constraints).
     */
    private UUID createAssessmentWithDigest(String digest) {
        InventoryApplication app = applicationRepository.findByApplicationKey("deploy-app").orElseThrow();

        PipelineRun run = new PipelineRun();
        run.setApplication(app);
        run.setProvider("GITHUB_ACTIONS");
        run.setExternalRunId("assess-run-" + digest.substring(0, 8));
        run.setRepositoryUrl("https://github.com/org/repo");
        run.setBranchName("main");
        run.setCommitSha("abc123abc123abc123abc123abc123abc123abc123");
        run.setStatus("SUCCESS");
        run.setStartedAt(Instant.now());
        run.setArtifactDigest(digest);
        pipelineRunRepository.saveAndFlush(run);

        ReleaseAssessment assessment = new ReleaseAssessment();
        assessment.setApplication(app);
        assessment.setPipelineRun(run);
        assessment.setArtifactDigest(digest);
        assessment.setRiskScore(10);
        assessment.setDecision("PASS");                       // chk_ra_decision: PASS|WARN|BLOCK
        assessment.setPolicyVersion("SCS-RELEASE-POLICY-V1");
        assessment.setAssessmentStatus("READY_FOR_DEPLOYMENT"); // chk_ra_status
        assessment.setAssessedAt(Instant.now());
        assessmentRepository.saveAndFlush(assessment);
        return assessment.getId();
    }

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

    private void ingestDeployment(String eventId, String sync, String health, String env) throws Exception {
        mockMvc.perform(post("/api/v1/integrations/deployments")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(deploymentPayload(eventId, sync, health, env)))
            .andExpect(status().isCreated());
    }

    private String deploymentPayload(String eventId, String sync, String health, String env) {
        return deploymentPayloadForApp("deploy-app", eventId, sync, health, env);
    }

    /** Payload with explicit eventId and gitRevision; operationPhase fixed. */
    private String deploymentPayloadWithEventId(String eventId, String revision, String sync, String health) {
        return """
            {
                "applicationKey": "deploy-app",
                "argocdApplication": "payment-service",
                "argocdProject": "default",
                "environment": "production",
                "imageReference": "registry/app@sha256:%s",
                "imageDigest": "%s",
                "gitRevision": "%s",
                "syncStatus": "%s",
                "healthStatus": "%s",
                "operationPhase": "Succeeded",
                "externalEventId": "%s",
                "deployedAt": "2026-11-08T12:00:00Z"
            }
            """.formatted(DIGEST, DIGEST, revision, sync, health, eventId);
    }

    private String deploymentPayloadForApp(String appKey, String eventId, String sync, String health, String env) {
        return """
            {
                "applicationKey": "%s",
                "argocdApplication": "payment-service",
                "argocdProject": "default",
                "environment": "%s",
                "cluster": "prod-cluster",
                "namespace": "payments",
                "gitRepoUrl": "https://github.com/org/gitops",
                "gitRevision": "abc123def456",
                "imageReference": "registry/app@sha256:%s",
                "imageDigest": "%s",
                "syncStatus": "%s",
                "healthStatus": "%s",
                "operationPhase": "Succeeded",
                "externalEventId": "%s",
                "deployedAt": "2026-11-08T12:00:00Z",
                "webhookEvidence": {"source": "argocd-notifications", "app": "payment-service"}
            }
            """.formatted(appKey, env, DIGEST, DIGEST, sync, health, eventId);
    }
}
