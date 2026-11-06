package com.supplychainsentinel.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.HttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link IngestionKeyFilter} that verify the four distinct
 * security conditions independently of the Spring context:
 * <ol>
 *   <li>Missing client API key → 401</li>
 *   <li>Invalid client API key → 401</li>
 *   <li>Missing server-side API key → 503 (misconfiguration)</li>
 *   <li>Server-side key shorter than 32 bytes → 503 (misconfiguration)</li>
 *   <li>Valid client key → request proceeds (filter chain continues)</li>
 * </ol>
 *
 * <p>Uses Spring's {@link MockHttpServletRequest}, {@link MockHttpServletResponse},
 * and {@link MockFilterChain} — real lightweight implementations, not Mockito mocks.</p>
 */
class IngestionKeyFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private static final String VALID_KEY = "a-valid-server-key-at-least-32-bytes-long!!!";
    private static final String INGESTION_PATH = "/api/v1/integrations/security-scans";

    private MockHttpServletRequest request(String path, String keyHeader) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", path);
        if (keyHeader != null) {
            req.addHeader("X-CI-INGESTION-KEY", keyHeader);
        }
        return req;
    }

    @Test
    void missingClientKeyReturns401() throws Exception {
        IngestionKeyFilter filter = new IngestionKeyFilter(objectMapper, VALID_KEY);
        MockHttpServletRequest req = request(INGESTION_PATH, null);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(req, resp, chain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, resp.getStatus());
        assertNull(chain.getRequest(), "Filter chain should not have been called");
    }

    @Test
    void blankClientKeyReturns401() throws Exception {
        IngestionKeyFilter filter = new IngestionKeyFilter(objectMapper, VALID_KEY);
        MockHttpServletRequest req = request(INGESTION_PATH, "  ");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(req, resp, chain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, resp.getStatus());
        assertNull(chain.getRequest(), "Filter chain should not have been called");
    }

    @Test
    void invalidClientKeyReturns401() throws Exception {
        IngestionKeyFilter filter = new IngestionKeyFilter(objectMapper, VALID_KEY);
        MockHttpServletRequest req = request(INGESTION_PATH, "wrong-key");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(req, resp, chain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, resp.getStatus());
        assertNull(chain.getRequest(), "Filter chain should not have been called");
    }

    @Test
    void missingServerKeyReturns503() throws Exception {
        IngestionKeyFilter filter = new IngestionKeyFilter(objectMapper, "");
        MockHttpServletRequest req = request(INGESTION_PATH, VALID_KEY);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(req, resp, chain);

        assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, resp.getStatus());
        assertNull(chain.getRequest(), "Filter chain should not have been called");
    }

    @Test
    void shortServerKeyReturns503() throws Exception {
        // Key is only 10 bytes — below the 32-byte minimum
        IngestionKeyFilter filter = new IngestionKeyFilter(objectMapper, "shortkey10");
        MockHttpServletRequest req = request(INGESTION_PATH, VALID_KEY);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(req, resp, chain);

        assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, resp.getStatus());
        assertNull(chain.getRequest(), "Filter chain should not have been called");
    }

    @Test
    void validKeyProceedsThroughFilterChain() throws Exception {
        IngestionKeyFilter filter = new IngestionKeyFilter(objectMapper, VALID_KEY);
        MockHttpServletRequest req = request(INGESTION_PATH, VALID_KEY);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(req, resp, chain);

        assertEquals(HttpServletResponse.SC_OK, resp.getStatus());
        assertNotNull(chain.getRequest(), "Filter chain should have been called");
    }

    @Test
    void nonIngestionPathBypassesFilter() throws Exception {
        IngestionKeyFilter filter = new IngestionKeyFilter(objectMapper, "");
        MockHttpServletRequest req = request("/api/v1/applications", null);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(req, resp, chain);

        // Should bypass filter for non-ingestion paths
        assertNotNull(chain.getRequest(), "Filter chain should have been called for non-ingestion path");
    }
}
