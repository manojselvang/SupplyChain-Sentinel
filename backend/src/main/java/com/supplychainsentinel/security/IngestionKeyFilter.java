package com.supplychainsentinel.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supplychainsentinel.dto.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Validates the {@code X-CI-INGESTION-KEY} header for requests to
 * {@code /api/v1/integrations/**}.
 *
 * <p>Security properties:
 * <ul>
 *   <li>Uses constant-time comparison to prevent timing attacks.</li>
 *   <li>Never logs the key value.</li>
 *   <li>Requires a minimum configured key length of 32 bytes; a shorter or
 *       absent key causes the server to respond 503 (misconfiguration) for
 *       all ingestion paths — callers see a configuration failure, not 401.</li>
 *   <li>An invalid client-supplied key returns 401.</li>
 * </ul>
 */
@Component
public class IngestionKeyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IngestionKeyFilter.class);
    private static final String INGESTION_PATH = "/api/v1/integrations/";
    private static final String KEY_HEADER = "X-CI-INGESTION-KEY";
    static final int MINIMUM_KEY_BYTES = 32;

    private final ObjectMapper objectMapper;
    private final String configuredKey;

    public IngestionKeyFilter(
            ObjectMapper objectMapper,
            @Value("${app.ci.ingestion-api-key:}") String configuredKey
    ) {
        this.objectMapper = objectMapper;
        this.configuredKey = configuredKey;

        // Fail-fast validation on startup
        if (configuredKey == null || configuredKey.isBlank()) {
            log.warn("CI_INGESTION_API_KEY is not configured. " +
                     "All ingestion endpoints will return 503 until a key of at least {} bytes is set.",
                     MINIMUM_KEY_BYTES);
        } else if (configuredKey.getBytes(StandardCharsets.UTF_8).length < MINIMUM_KEY_BYTES) {
            log.warn("CI_INGESTION_API_KEY is configured but shorter than {} bytes. " +
                     "All ingestion endpoints will return 503 until a stronger key is set.",
                     MINIMUM_KEY_BYTES);
        }
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Only intercept ingestion endpoints
        if (!path.startsWith(INGESTION_PATH)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Server-side misconfiguration: key absent or too short → 503
        if (configuredKey == null || configuredKey.isBlank() ||
                configuredKey.getBytes(StandardCharsets.UTF_8).length < MINIMUM_KEY_BYTES) {
            sendError(response, request, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Service Unavailable",
                    "CI ingestion key is not configured or does not meet minimum strength requirements");
            return;
        }

        String providedKey = request.getHeader(KEY_HEADER);

        if (providedKey == null || providedKey.isBlank()) {
            sendError(response, request, HttpServletResponse.SC_UNAUTHORIZED,
                    "Unauthorized", "Missing " + KEY_HEADER + " header");
            return;
        }

        // Constant-time comparison to prevent timing attacks
        byte[] providedBytes = providedKey.getBytes(StandardCharsets.UTF_8);
        byte[] configuredBytes = configuredKey.getBytes(StandardCharsets.UTF_8);

        if (!MessageDigest.isEqual(providedBytes, configuredBytes)) {
            sendError(response, request, HttpServletResponse.SC_UNAUTHORIZED,
                    "Unauthorized", "Invalid CI ingestion key");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void sendError(
            HttpServletResponse response,
            HttpServletRequest request,
            int status,
            String error,
            String message
    ) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError body = new ApiError(status, error, message, request.getRequestURI());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
