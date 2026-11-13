package com.supplychainsentinel.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validates that all required production secrets and configuration are
 * explicitly provided, and that no development-only placeholder values
 * have been carried into the production environment.
 *
 * <p>Activated only in the {@code prod} profile. Fails fast at startup
 * so a misconfigured pod is detected immediately rather than silently
 * running with insecure defaults.</p>
 *
 * <h2>Permanent runtime requirements</h2>
 * <p>These must be present on every startup, not just the first deploy:
 * <ul>
 *   <li>{@code DB_HOST} — must not be blank or a localhost address</li>
 *   <li>{@code DB_PORT} — must be set and parseable as a valid port (1–65535)</li>
 *   <li>{@code DB_NAME} — must not be blank</li>
 *   <li>{@code DB_USERNAME} — must not be blank</li>
 *   <li>{@code DB_PASSWORD} — must not be blank or a known placeholder</li>
 *   <li>{@code JWT_SECRET} — must be ≥ 32 bytes; not a dev placeholder</li>
 *   <li>{@code CI_INGESTION_API_KEY} — must be ≥ 32 bytes; not a dev placeholder</li>
 *   <li>{@code APP_CORS_ALLOWED_ORIGINS} — must be set; must not be a localhost value</li>
 *   <li>{@code APP_COOKIE_SECURE} — must be {@code true}</li>
 * </ul>
 * </p>
 *
 * <h2>One-time bootstrap (not validated here)</h2>
 * <p>{@code INITIAL_ADMIN_USERNAME}, {@code INITIAL_ADMIN_PASSWORD}, and
 * {@code INITIAL_ADMIN_EMAIL} are required only on the very first deployment.
 * The {@code AdminProvisioningRunner} skips provisioning if the user already
 * exists. These values are not validated by this class because they are
 * intentionally optional after the initial bootstrap. Store them in Secrets
 * Manager and remove or clear them after the first admin login.</p>
 */
@Component
@Profile("prod")
public class ProductionConfigValidator {

    private static final Logger log = LoggerFactory.getLogger(ProductionConfigValidator.class);

    private static final int MIN_SECRET_BYTES = 32;

    /** Known placeholder passwords that must never appear in production. */
    private static final Set<String> FORBIDDEN_PASSWORDS = Set.of(
            "scsentinel",
            "changeme123",
            "password",
            "postgres",
            "change-me",
            "secret"
    );

    @Value("${DB_HOST:}")
    private String dbHost;

    @Value("${DB_PORT:}")
    private String dbPort;

    @Value("${DB_NAME:}")
    private String dbName;

    @Value("${DB_USERNAME:}")
    private String dbUsername;

    @Value("${DB_PASSWORD:}")
    private String dbPassword;

    @Value("${JWT_SECRET:}")
    private String jwtSecret;

    @Value("${CI_INGESTION_API_KEY:}")
    private String ingestionKey;

    @Value("${APP_CORS_ALLOWED_ORIGINS:}")
    private String corsOrigins;

    @Value("${APP_COOKIE_SECURE:false}")
    private boolean cookieSecure;

    @Value("${app.production.validate-on-startup:true}")
    private boolean validateOnStartup;

    @EventListener(ApplicationStartedEvent.class)
    public void validateProductionConfig() {
        if (!validateOnStartup) {
            log.debug("Production config validation skipped (app.production.validate-on-startup=false)");
            return;
        }
        List<String> errors = new ArrayList<>();

        // ── Database ──────────────────────────────────────────
        if (isBlank(dbHost)) {
            errors.add("DB_HOST must be set to the RDS endpoint");
        } else if (dbHost.contains("localhost") || dbHost.contains("127.0.0.1")) {
            errors.add("DB_HOST must not be a localhost address in production (got: " + dbHost + ")");
        }

        if (isBlank(dbPort)) {
            errors.add("DB_PORT must be set (e.g. 5432 for RDS PostgreSQL)");
        } else {
            try {
                int port = Integer.parseInt(dbPort.trim());
                if (port < 1 || port > 65535) {
                    errors.add("DB_PORT must be a valid port number (1–65535), got: " + port);
                }
            } catch (NumberFormatException e) {
                errors.add("DB_PORT must be a numeric port value, got: " + dbPort);
            }
        }

        if (isBlank(dbName)) {
            errors.add("DB_NAME must be set");
        }

        if (isBlank(dbUsername)) {
            errors.add("DB_USERNAME must be set");
        }

        if (isBlank(dbPassword)) {
            errors.add("DB_PASSWORD must be set (store in AWS Secrets Manager)");
        } else if (FORBIDDEN_PASSWORDS.contains(dbPassword.trim().toLowerCase())) {
            errors.add("DB_PASSWORD is a known placeholder value and must not be used in production");
        }

        // ── JWT secret ────────────────────────────────────────
        if (isBlank(jwtSecret)) {
            errors.add("JWT_SECRET must be set (store in AWS Secrets Manager, min 32 bytes)");
        } else if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            errors.add("JWT_SECRET must be at least " + MIN_SECRET_BYTES + " bytes");
        } else if (jwtSecret.contains("dev-only") || jwtSecret.contains("change-me")) {
            errors.add("JWT_SECRET appears to be a development placeholder; replace with a strong random secret");
        }

        // ── Ingestion API key ─────────────────────────────────
        if (isBlank(ingestionKey)) {
            errors.add("CI_INGESTION_API_KEY must be set (store in AWS Secrets Manager, min 32 bytes)");
        } else if (ingestionKey.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            errors.add("CI_INGESTION_API_KEY must be at least " + MIN_SECRET_BYTES + " bytes");
        } else if (ingestionKey.contains("dev-only") || ingestionKey.contains("change-me")) {
            errors.add("CI_INGESTION_API_KEY appears to be a development placeholder");
        }

        // ── CORS origins ──────────────────────────────────────
        if (isBlank(corsOrigins)) {
            errors.add("APP_CORS_ALLOWED_ORIGINS must be set to the CloudFront distribution hostname");
        } else {
            for (String origin : corsOrigins.split(",")) {
                String trimmed = origin.trim();
                if (trimmed.contains("localhost") || trimmed.contains("127.0.0.1")) {
                    errors.add("APP_CORS_ALLOWED_ORIGINS contains a localhost value '" + trimmed
                            + "' which is not valid in production");
                }
            }
        }

        // ── Cookie security ───────────────────────────────────
        if (!cookieSecure) {
            errors.add("APP_COOKIE_SECURE must be true in production. "
                    + "Without this, the HttpOnly refresh cookie is not marked Secure "
                    + "and will be sent over non-HTTPS connections.");
        }

        // ── Report ────────────────────────────────────────────
        if (!errors.isEmpty()) {
            log.error("===== PRODUCTION CONFIGURATION ERRORS =====");
            errors.forEach(e -> log.error("  ✗ {}", e));
            log.error("===========================================");
            String summary = "Production configuration validation failed with " + errors.size()
                    + " error(s):\n" + errors.stream().map(e -> "  - " + e)
                    .reduce("", (a, b) -> a.isBlank() ? b : a + "\n" + b);
            throw new IllegalStateException(summary);
        }

        log.info("Production configuration validated successfully.");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
