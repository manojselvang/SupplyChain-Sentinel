package com.supplychainsentinel;

import com.supplychainsentinel.config.ProductionConfigValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ProductionConfigValidator} — no Spring context required.
 * Tests that missing or placeholder production secrets cause
 * {@link IllegalStateException} at startup.
 */
class ProductionConfigValidatorTest {

    private static final String GOOD_SECRET = "valid-secret-key-at-least-32-bytes-long!!!";
    private static final String GOOD_KEY    = "valid-ingestion-key-at-least-32-bytes-long!";
    private static final String GOOD_HOST   = "mydb.cluster-xyz.us-east-1.rds.amazonaws.com";
    private static final String GOOD_PORT   = "5432";
    private static final String GOOD_CORS   = "https://sentinel.example.com";
    private static final String GOOD_DB     = "supplychain_sentinel";
    private static final String GOOD_USER   = "appuser";
    private static final String GOOD_PASS   = "MySecureRdsPassword123!";

    private ProductionConfigValidator buildValidator(
            String dbHost, String dbPort, String dbName, String dbUser, String dbPass,
            String jwtSecret, String ingestionKey, String corsOrigins,
            boolean cookieSecure) throws Exception {
        ProductionConfigValidator v = new ProductionConfigValidator();
        setField(v, "validateOnStartup", true);
        setField(v, "dbHost",       dbHost);
        setField(v, "dbPort",       dbPort);
        setField(v, "dbName",       dbName);
        setField(v, "dbUsername",   dbUser);
        setField(v, "dbPassword",   dbPass);
        setField(v, "jwtSecret",    jwtSecret);
        setField(v, "ingestionKey", ingestionKey);
        setField(v, "corsOrigins",  corsOrigins);
        setField(v, "cookieSecure", cookieSecure);
        return v;
    }

    @Test
    void validConfigPassesValidation() throws Exception {
        buildValidator(GOOD_HOST, GOOD_PORT, GOOD_DB, GOOD_USER, GOOD_PASS,
                GOOD_SECRET, GOOD_KEY, GOOD_CORS, true)
                .validateProductionConfig();
    }

    @Test
    void missingDbHostFailsValidation() throws Exception {
        assertThatThrownBy(() ->
                buildValidator("", GOOD_PORT, GOOD_DB, GOOD_USER, GOOD_PASS,
                        GOOD_SECRET, GOOD_KEY, GOOD_CORS, true)
                        .validateProductionConfig())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_HOST");
    }

    @Test
    void localhostDbHostFailsValidation() throws Exception {
        assertThatThrownBy(() ->
                buildValidator("localhost", GOOD_PORT, GOOD_DB, GOOD_USER, GOOD_PASS,
                        GOOD_SECRET, GOOD_KEY, GOOD_CORS, true)
                        .validateProductionConfig())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_HOST");
    }

    @Test
    void missingDbPortFailsValidation() throws Exception {
        assertThatThrownBy(() ->
                buildValidator(GOOD_HOST, "", GOOD_DB, GOOD_USER, GOOD_PASS,
                        GOOD_SECRET, GOOD_KEY, GOOD_CORS, true)
                        .validateProductionConfig())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_PORT");
    }

    @Test
    void invalidDbPortFailsValidation() throws Exception {
        assertThatThrownBy(() ->
                buildValidator(GOOD_HOST, "not-a-port", GOOD_DB, GOOD_USER, GOOD_PASS,
                        GOOD_SECRET, GOOD_KEY, GOOD_CORS, true)
                        .validateProductionConfig())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_PORT");
    }

    @Test
    void outOfRangeDbPortFailsValidation() throws Exception {
        assertThatThrownBy(() ->
                buildValidator(GOOD_HOST, "99999", GOOD_DB, GOOD_USER, GOOD_PASS,
                        GOOD_SECRET, GOOD_KEY, GOOD_CORS, true)
                        .validateProductionConfig())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_PORT");
    }

    @Test
    void placeholderDbPasswordScsentinelFails() throws Exception {
        assertThatThrownBy(() ->
                buildValidator(GOOD_HOST, GOOD_PORT, GOOD_DB, GOOD_USER, "scsentinel",
                        GOOD_SECRET, GOOD_KEY, GOOD_CORS, true)
                        .validateProductionConfig())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_PASSWORD");
    }

    @Test
    void placeholderDbPasswordChangeme123Fails() throws Exception {
        assertThatThrownBy(() ->
                buildValidator(GOOD_HOST, GOOD_PORT, GOOD_DB, GOOD_USER, "changeme123",
                        GOOD_SECRET, GOOD_KEY, GOOD_CORS, true)
                        .validateProductionConfig())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_PASSWORD");
    }

    @Test
    void shortJwtSecretFails() throws Exception {
        assertThatThrownBy(() ->
                buildValidator(GOOD_HOST, GOOD_PORT, GOOD_DB, GOOD_USER, GOOD_PASS,
                        "short", GOOD_KEY, GOOD_CORS, true)
                        .validateProductionConfig())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void devOnlyJwtSecretFails() throws Exception {
        assertThatThrownBy(() ->
                buildValidator(GOOD_HOST, GOOD_PORT, GOOD_DB, GOOD_USER, GOOD_PASS,
                        "dev-only-secret-key-change-me-in-production-at-least-32-characters-long",
                        GOOD_KEY, GOOD_CORS, true)
                        .validateProductionConfig())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void missingIngestionKeyFails() throws Exception {
        assertThatThrownBy(() ->
                buildValidator(GOOD_HOST, GOOD_PORT, GOOD_DB, GOOD_USER, GOOD_PASS,
                        GOOD_SECRET, "", GOOD_CORS, true)
                        .validateProductionConfig())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CI_INGESTION_API_KEY");
    }

    @Test
    void localhostCorsOriginFails() throws Exception {
        assertThatThrownBy(() ->
                buildValidator(GOOD_HOST, GOOD_PORT, GOOD_DB, GOOD_USER, GOOD_PASS,
                        GOOD_SECRET, GOOD_KEY, "http://localhost:5173", true)
                        .validateProductionConfig())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_CORS_ALLOWED_ORIGINS");
    }

    @Test
    void cookieSecureFalseFails() throws Exception {
        assertThatThrownBy(() ->
                buildValidator(GOOD_HOST, GOOD_PORT, GOOD_DB, GOOD_USER, GOOD_PASS,
                        GOOD_SECRET, GOOD_KEY, GOOD_CORS, false)
                        .validateProductionConfig())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_COOKIE_SECURE");
    }

    @Test
    void missingCorsOriginsFails() throws Exception {
        assertThatThrownBy(() ->
                buildValidator(GOOD_HOST, GOOD_PORT, GOOD_DB, GOOD_USER, GOOD_PASS,
                        GOOD_SECRET, GOOD_KEY, "", true)
                        .validateProductionConfig())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_CORS_ALLOWED_ORIGINS");
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field f = ProductionConfigValidator.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }
}
