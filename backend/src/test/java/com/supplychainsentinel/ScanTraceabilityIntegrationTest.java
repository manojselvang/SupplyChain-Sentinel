package com.supplychainsentinel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supplychainsentinel.entity.InventoryApplication;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for scan-to-finding traceability.
 *
 * Verifies that the junction table (security_scan_findings) correctly
 * records which findings were detected in which scan, preserving
 * historical associations across repeated ingestions.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ScanTraceabilityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private SecurityScanFindingRepository scanFindingRepository;
    @Autowired private SecurityFindingRepository findingRepository;

    private static final String TEST_PASSWORD = "TestPass123!";
    private static final String INGESTION_KEY = "test-ingestion-key-for-ci-pipeline-integration";
    private static final String INGESTION_HEADER = "X-CI-INGESTION-KEY";

    private String adminToken;
    private UUID app1Id;
    private UUID app2Id;

    @BeforeEach
    void setUp() throws Exception {
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        createUser("traceadmin", adminRole);
        adminToken = loginAndGetToken("traceadmin");

        app1Id = createApp("trace-app-1", "Trace App 1");
        app2Id = createApp("trace-app-2", "Trace App 2");
    }

    // ── Traceability tests ──────────────────────────────────────

    @Test
    void newFindingAssociatedWithOriginalScan() throws Exception {
        MvcResult result = ingestTrivy("trace-app-1", "2026-11-05T10:00:00Z");
        UUID scanId = extractScanId(result);
        UUID findingId = extractFirstFindingId(result);

        // Junction record should exist linking scan to finding
        assertTrue(scanFindingRepository.existsByScanIdAndFindingId(scanId, findingId),
                "Junction record should link the scan to the finding");
    }

    @Test
    void sameFindingAssociatedWithSecondScan() throws Exception {
        // First scan
        MvcResult r1 = ingestTrivy("trace-app-1", "2026-11-05T10:00:00Z");
        UUID scan1Id = extractScanId(r1);
        UUID findingId = extractFirstFindingId(r1);

        // Second scan (same findings → deduplication)
        MvcResult r2 = ingestTrivy("trace-app-1", "2026-11-05T11:00:00Z");
        UUID scan2Id = extractScanId(r2);

        // Both scan-to-finding junctions should exist
        assertTrue(scanFindingRepository.existsByScanIdAndFindingId(scan1Id, findingId),
                "First scan junction should exist");
        assertTrue(scanFindingRepository.existsByScanIdAndFindingId(scan2Id, findingId),
                "Second scan junction should exist");

        // The finding should be associated with 2 scans
        long junctionCount = scanFindingRepository.countByFindingId(findingId);
        assertEquals(2L, junctionCount, "Finding should be associated with 2 scans");
    }

    @Test
    void historicalScanAssociationsPreserved() throws Exception {
        // Three scans of the same findings
        MvcResult r1 = ingestTrivy("trace-app-1", "2026-11-05T10:00:00Z");
        UUID scan1Id = extractScanId(r1);
        UUID findingId = extractFirstFindingId(r1);

        MvcResult r2 = ingestTrivy("trace-app-1", "2026-11-05T11:00:00Z");
        UUID scan2Id = extractScanId(r2);

        MvcResult r3 = ingestTrivy("trace-app-1", "2026-11-05T12:00:00Z");
        UUID scan3Id = extractScanId(r3);

        // All three junctions should still exist (historical preservation)
        assertTrue(scanFindingRepository.existsByScanIdAndFindingId(scan1Id, findingId),
                "First scan junction preserved");
        assertTrue(scanFindingRepository.existsByScanIdAndFindingId(scan2Id, findingId),
                "Second scan junction preserved");
        assertTrue(scanFindingRepository.existsByScanIdAndFindingId(scan3Id, findingId),
                "Third scan junction preserved");

        assertEquals(3L, scanFindingRepository.countByFindingId(findingId),
                "Finding should be associated with 3 scans");
    }

    @Test
    void firstSeenAndLastSeenReflectScanHistory() throws Exception {
        // First scan at 10:00
        ingestTrivy("trace-app-1", "2026-11-05T10:00:00Z");

        // Read the finding
        MvcResult listResult = mockMvc.perform(get("/api/v1/security-findings")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn();
        var page = objectMapper.readValue(listResult.getResponse().getContentAsString(), Map.class);
        @SuppressWarnings("unchecked")
        var content = (List<Map<String, Object>>) page.get("content");
        String findingId = (String) content.get(0).get("id");
        String firstSeen = (String) content.get(0).get("firstSeen");
        String lastSeen1 = (String) content.get(0).get("lastSeen");

        // Second scan at 12:00
        ingestTrivy("trace-app-1", "2026-11-05T12:00:00Z");

        // Read the finding again
        MvcResult listResult2 = mockMvc.perform(get("/api/v1/security-findings/" + findingId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn();
        var finding = objectMapper.readValue(listResult2.getResponse().getContentAsString(), Map.class);
        String firstSeenAfter = (String) finding.get("firstSeen");
        String lastSeen2 = (String) finding.get("lastSeen");

        // firstSeen should be unchanged
        assertEquals(firstSeen, firstSeenAfter, "firstSeen should be preserved across scans");
        // lastSeen should be updated to the second scan's timestamp
        assertNotEquals(lastSeen1, lastSeen2, "lastSeen should be updated after second scan");
    }

    @Test
    void twoApplicationsWithSameVulnerabilityAreSeparateFindings() throws Exception {
        // Ingest the same Trivy report for both apps
        MvcResult r1 = ingestTrivy("trace-app-1", "2026-11-05T10:00:00Z");
        MvcResult r2 = ingestTrivy("trace-app-2", "2026-11-05T10:00:00Z");

        UUID scan1Id = extractScanId(r1);
        UUID scan2Id = extractScanId(r2);
        UUID finding1Id = extractFirstFindingId(r1);
        UUID finding2Id = extractFirstFindingId(r2);

        // Different finding IDs
        assertNotEquals(finding1Id, finding2Id,
                "Same vulnerability in different apps should be separate findings");

        // Different scan IDs
        assertNotEquals(scan1Id, scan2Id,
                "Each app should have its own scan record");

        // Each scan should link to its own finding
        assertTrue(scanFindingRepository.existsByScanIdAndFindingId(scan1Id, finding1Id),
                "App1 scan should link to app1 finding");
        assertTrue(scanFindingRepository.existsByScanIdAndFindingId(scan2Id, finding2Id),
                "App2 scan should link to app2 finding");

        // App1 scan should NOT link to app2 finding
        assertFalse(scanFindingRepository.existsByScanIdAndFindingId(scan1Id, finding2Id),
                "App1 scan should not link to app2 finding");

        // Different fingerprints (because appId is part of the fingerprint)
        var f1 = findingRepository.findById(finding1Id).orElseThrow();
        var f2 = findingRepository.findById(finding2Id).orElseThrow();
        assertNotEquals(f1.getFingerprint(), f2.getFingerprint(),
                "Fingerprints should differ because they include the application ID");
    }

    @Test
    void triageStatusPreservedAcrossRepeatedScans() throws Exception {
        // First scan
        ingestTrivy("trace-app-1", "2026-11-05T10:00:00Z");

        // Get finding ID
        MvcResult listResult = mockMvc.perform(get("/api/v1/security-findings")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn();
        var page = objectMapper.readValue(listResult.getResponse().getContentAsString(), Map.class);
        @SuppressWarnings("unchecked")
        var content = (List<Map<String, Object>>) page.get("content");
        String findingId = (String) content.get(0).get("id");

        // Triage the finding
        mockMvc.perform(patch("/api/v1/security-findings/" + findingId + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACKNOWLEDGED\"}"))
            .andExpect(status().isOk());

        // Second scan (same findings)
        ingestTrivy("trace-app-1", "2026-11-05T12:00:00Z");

        // Verify status is still ACKNOWLEDGED
        mockMvc.perform(get("/api/v1/security-findings/" + findingId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));
    }

    @Test
    void findingAssociatedWithScanAndApplication() throws Exception {
        MvcResult result = ingestTrivy("trace-app-1", "2026-11-05T10:00:00Z");
        UUID scanId = extractScanId(result);
        UUID findingId = extractFirstFindingId(result);

        // Verify the finding has the correct application
        var finding = findingRepository.findById(findingId).orElseThrow();
        assertEquals(app1Id, finding.getApplication().getId(),
                "Finding should be associated with the correct application");

        // Verify the junction links scan → finding
        var junctions = scanFindingRepository.findByFindingId(findingId);
        assertEquals(1, junctions.size(), "Finding should have 1 junction (from this scan)");
        assertEquals(scanId, junctions.get(0).getScanId(),
                "Junction should reference the correct scan");
    }

    // ── Helpers ────────────────────────────────────────────────

    private UUID createApp(String key, String name) {
        InventoryApplication app = new InventoryApplication();
        app.setApplicationKey(key);
        app.setName(name);
        app.setCriticality("HIGH");
        app.setLifecycleStatus("ACTIVE");
        app.setDataClassification("INTERNAL");
        app.setHostingEnvironment("AWS");
        applicationRepository.saveAndFlush(app);
        return app.getId();
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

    private MvcResult ingestTrivy(String appKey, String scanTimestamp) throws Exception {
        return mockMvc.perform(post("/api/v1/integrations/security-scans")
                .header(INGESTION_HEADER, INGESTION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "applicationKey":"%s",
                        "scanner":"trivy",
                        "reportFormat":"TRIVY_JSON",
                        "scanTimestamp":"%s",
                        "report":{
                            "Results":[{
                                "Target":"alpine:3.18",
                                "Vulnerabilities":[{
                                    "VulnerabilityID":"CVE-2023-0001",
                                    "PkgName":"libssl",
                                    "InstalledVersion":"1.1.1t",
                                    "FixedVersion":"1.1.1u",
                                    "Severity":"HIGH",
                                    "Title":"OpenSSL vuln",
                                    "Description":"Buffer overflow"
                                }]
                            }]
                        }
                    }
                    """.formatted(appKey, scanTimestamp)))
            .andExpect(status().isCreated())
            .andReturn();
    }

    @SuppressWarnings("unchecked")
    private UUID extractScanId(MvcResult result) throws Exception {
        Map<String, Object> resp = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return UUID.fromString((String) resp.get("scanId"));
    }

    @SuppressWarnings("unchecked")
    private UUID extractFirstFindingId(MvcResult result) throws Exception {
        Map<String, Object> resp = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        List<Map<String, Object>> findings = (List<Map<String, Object>>) resp.get("findings");
        return UUID.fromString((String) findings.get(0).get("id"));
    }

    // Static imports for JUnit assertions
    private static void assertTrue(boolean condition, String message) {
        org.junit.jupiter.api.Assertions.assertTrue(condition, message);
    }
    private static void assertFalse(boolean condition, String message) {
        org.junit.jupiter.api.Assertions.assertFalse(condition, message);
    }
    private static void assertEquals(Object expected, Object actual, String message) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual, message);
    }
    private static void assertNotEquals(Object expected, Object actual, String message) {
        org.junit.jupiter.api.Assertions.assertNotEquals(expected, actual, message);
    }
}
