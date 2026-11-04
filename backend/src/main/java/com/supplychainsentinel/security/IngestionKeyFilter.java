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
 * {@code /api/v1/integrations/**}.  Uses constant-time comparison to
 * prevent timing attacks.  Never logs the key value.
 *
 * <p>This filter runs <em>before</em> {@link JwtAuthenticationFilter} in the
 * Spring Security chain.  The ingestion routes are configured as
 * {@code permitAll()} in {@link SecurityConfig} — this filter is the
 * actual gatekeeper.</p>
 */
@Component
public class IngestionKeyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IngestionKeyFilter.class);
    private static final String INGESTION_PATH = "/api/v1/integrations/";
    private static final String KEY_HEADER = "X-CI-INGESTION-KEY";

    private final ObjectMapper objectMapper;
    private final String configuredKey;

    public IngestionKeyFilter(
            ObjectMapper objectMapper,
            @Value("${app.ci.ingestion-api-key:}") String configuredKey
    ) {
        this.objectMapper = objectMapper;
        this.configuredKey = configuredKey;
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

        String providedKey = request.getHeader(KEY_HEADER);

        if (configuredKey == null || configuredKey.isBlank()) {
            log.warn("CI ingestion key not configured — rejecting request to {}", path);
            sendUnauthorized(response, request, "CI ingestion is not configured on this server");
            return;
        }

        if (providedKey == null || providedKey.isBlank()) {
            sendUnauthorized(response, request, "Missing " + KEY_HEADER + " header");
            return;
        }

        // Constant-time comparison to prevent timing attacks
        byte[] providedBytes = providedKey.getBytes(StandardCharsets.UTF_8);
        byte[] configuredBytes = configuredKey.getBytes(StandardCharsets.UTF_8);

        if (!MessageDigest.isEqual(providedBytes, configuredBytes)) {
            sendUnauthorized(response, request, "Invalid CI ingestion key");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void sendUnauthorized(
            HttpServletResponse response,
            HttpServletRequest request,
            String message
    ) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError body = new ApiError(401, "Unauthorized", message, request.getRequestURI());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
