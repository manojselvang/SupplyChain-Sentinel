package com.supplychainsentinel;

import com.supplychainsentinel.config.ProductionConfigValidator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Focused Spring Boot lifecycle test for {@link ProductionConfigValidator}.
 *
 * <h2>What this test proves</h2>
 * <p>That the {@code @EventListener(ApplicationStartedEvent.class)} annotation on
 * {@link ProductionConfigValidator} is actually triggered by Spring Boot's
 * lifecycle — not by any other component (DataSource, Flyway, Security, etc.).
 *
 * <h2>How it works</h2>
 * <p>Uses a <strong>minimal</strong> {@link SpringBootConfiguration} that imports
 * <strong>only</strong> {@code ProductionConfigValidator}. No DataSource, no JPA,
 * no Flyway, no Spring Security, no web server, no auto-configuration is loaded.
 * The only bean in the context is the validator itself.
 *
 * <p>{@code spring.config.name} is set to a non-existent name so that
 * {@code application.yml} and {@code application-prod.yml} are <strong>not</strong>
 * loaded. All properties are supplied explicitly via {@code .properties()}.
 * This avoids unresolved placeholder errors from the YAML files and makes the
 * test fully self-contained.
 *
 * <p>Each test calls {@code SpringApplication.run()} — the real entry point that
 * publishes {@code ApplicationStartedEvent} (which triggers the validator) and
 * {@code ApplicationReadyEvent} (which fires only if the validator passes).
 *
 * <h2>Test matrix</h2>
 * <ul>
 *   <li>7 invalid-config tests — one per mandatory setting</li>
 *   <li>1 valid-config test — full lifecycle with validation enabled</li>
 *   <li>1 secret-exposure test — verifies error messages don't leak secrets</li>
 * </ul>
 */
class ProductionConfigLifecycleTest {

    /**
     * Minimal Spring configuration. Imports ONLY the validator bean.
     * No component scanning, no auto-configuration, no database, no web server.
     *
     * <p>Uses plain {@code @Configuration} (not {@code @SpringBootConfiguration})
     * so that Spring Boot's {@code @SpringBootTest} auto-detection does not
     * pick up this class as a second configuration candidate — it only
     * searches for {@code @SpringBootConfiguration} classes. This prevents
     * the "multiple @SpringBootConfiguration" conflict that would break
     * all other integration test classes.
     *
     * <p>{@code SpringApplication.run()} works with any {@code @Configuration}
     * class, so the lifecycle is fully exercised.
     */
    @Configuration
    @Import(ProductionConfigValidator.class)
    static class MinimalApp {
    }

    // ── Base valid properties (all pass validation) ────────────────────────

    private static final String[] VALID_PROPS = {
            // Prevent loading application*.yml from the classpath
            "spring.config.name=minimal-lifecycle-test",
            // All mandatory production settings — valid values
            "DB_HOST=mydb.cluster-xyz.us-east-1.rds.amazonaws.com",
            "DB_PORT=5432",
            "DB_NAME=supplychain_sentinel",
            "DB_USERNAME=appuser",
            "DB_PASSWORD=MySecureRdsPassword123!",
            "JWT_SECRET=production-safe-jwt-secret-at-least-32-bytes-long-for-testing",
            "CI_INGESTION_API_KEY=production-safe-ingestion-api-key-at-least-32-bytes",
            "APP_CORS_ALLOWED_ORIGINS=https://sentinel.example.com",
            "APP_COOKIE_SECURE=true",
            // Ensure the validator runs (default is true, set explicitly for clarity)
            "app.production.validate-on-startup=true"
    };

    /**
     * Build a {@link SpringApplicationBuilder} with all valid properties plus
     * any overrides. Because {@code .properties()} uses a {@link java.util.HashMap}
     * internally, later entries with the same key override earlier ones.
     */
    private SpringApplicationBuilder buildApp(String... overrides) {
        List<String> props = new ArrayList<>(List.of(VALID_PROPS));
        Collections.addAll(props, overrides);

        return new SpringApplicationBuilder(MinimalApp.class)
                .web(WebApplicationType.NONE)
                .profiles("prod")
                .properties(props.toArray(new String[0]));
    }

    /**
     * Assert that {@code SpringApplication.run()} fails, {@code ApplicationReadyEvent}
     * is never published, and the exception chain contains the validator's
     * {@link IllegalStateException} with the expected message fragment.
     */
    private void assertStartupFails(String expectedMessage, String... overrides) {
        AtomicBoolean readyFired = new AtomicBoolean(false);

        var app = buildApp(overrides).build();
        app.addListeners((ApplicationListener<ApplicationReadyEvent>)
                event -> readyFired.set(true));

        Throwable thrown = catchThrowable(app::run);

        // ── Startup must fail ────────────────────────────────────────────────
        assertThat(thrown)
                .as("SpringApplication.run() must throw when config validation fails")
                .isNotNull();

        // ── ApplicationReadyEvent must NOT fire ──────────────────────────────
        assertThat(readyFired.get())
                .as("ApplicationReadyEvent must NOT be published when validation fails")
                .isFalse();

        // ── The validator's IllegalStateException must be in the cause chain ──
        String allMessages = collectMessages(thrown);
        assertThat(allMessages)
                .as("Exception chain must contain the expected validation message")
                .contains(expectedMessage);

        assertThat(hasValidatorExceptionInChain(thrown))
                .as("Exception chain must contain the ProductionConfigValidator's "
                        + "IllegalStateException (proves the validator was triggered by "
                        + "ApplicationStartedEvent)")
                .isTrue();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Invalid configuration tests — one per mandatory setting
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    void missingDbHostPreventsStartup() {
        assertStartupFails("DB_HOST", "DB_HOST=");
    }

    @Test
    void missingDbPortPreventsStartup() {
        assertStartupFails("DB_PORT", "DB_PORT=");
    }

    @Test
    void placeholderDbPasswordPreventsStartup() {
        assertStartupFails("DB_PASSWORD", "DB_PASSWORD=scsentinel");
    }

    @Test
    void shortJwtSecretPreventsStartup() {
        assertStartupFails("JWT_SECRET", "JWT_SECRET=short");
    }

    @Test
    void missingIngestionKeyPreventsStartup() {
        assertStartupFails("CI_INGESTION_API_KEY", "CI_INGESTION_API_KEY=");
    }

    @Test
    void localhostCorsPreventsStartup() {
        assertStartupFails("APP_CORS_ALLOWED_ORIGINS",
                "APP_CORS_ALLOWED_ORIGINS=http://localhost:5173");
    }

    @Test
    void cookieSecureFalsePreventsStartup() {
        assertStartupFails("APP_COOKIE_SECURE", "APP_COOKIE_SECURE=false");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Valid configuration — full lifecycle succeeds, validation passes
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * All mandatory settings are provided with valid values. The validator
     * runs on {@code ApplicationStartedEvent} and passes. {@code ApplicationReadyEvent}
     * fires and {@code SpringApplication.run()} returns a live context.
     *
     * <p>This proves the validator is actually executed by Spring Boot's lifecycle
     * — if it were not, the valid-config test would be indistinguishable from
     * running without the validator at all. The invalid-config tests prove the
     * negative side; this test proves the positive side.
     */
    @Test
    void validConfigAllowsStartupAndReadyEventFires() {
        AtomicBoolean readyFired = new AtomicBoolean(false);

        var app = buildApp().build();
        app.addListeners((ApplicationListener<ApplicationReadyEvent>)
                event -> readyFired.set(true));

        try (ConfigurableApplicationContext context = app.run()) {
            assertThat(context.isActive())
                    .as("Spring context must be active when startup succeeds")
                    .isTrue();

            assertThat(readyFired.get())
                    .as("ApplicationReadyEvent must fire when validation passes")
                    .isTrue();
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Secret exposure — error messages must not disclose secret values
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * The validator's error messages must name the missing or invalid config key
     * (e.g. "JWT_SECRET must be at least 32 bytes") but must never print the
     * actual value of any secret (e.g. the DB_PASSWORD).
     */
    @Test
    void errorMessagesDoNotExposeSecretValues() {
        String secretDbPassword = "my-super-secret-password-value-XYZ";
        String secretIngestionKey = "my-secret-ingestion-api-key-ABC123";

        var app = buildApp(
                "DB_PASSWORD=" + secretDbPassword,
                "CI_INGESTION_API_KEY=" + secretIngestionKey,
                "JWT_SECRET=short"  // too short — triggers failure
        ).build();

        Throwable thrown = catchThrowable(app::run);
        assertThat(thrown).isNotNull();

        String messages = collectMessages(thrown);

        // Must mention the config key name so operators can fix it
        assertThat(messages)
                .as("Error must name the invalid config key")
                .contains("JWT_SECRET");

        // Must NOT contain actual secret values
        assertThat(messages)
                .as("Error must NOT contain the DB_PASSWORD value")
                .doesNotContain(secretDbPassword);

        assertThat(messages)
                .as("Error must NOT contain the CI_INGESTION_API_KEY value")
                .doesNotContain(secretIngestionKey);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Traverse the full exception cause chain and collect all messages.
     * {@code SpringApplication.run()} may wrap the validator's
     * {@link IllegalStateException} in another exception, so we must
     * check the entire chain.
     */
    private static String collectMessages(Throwable thrown) {
        StringBuilder sb = new StringBuilder();
        Throwable current = thrown;
        while (current != null) {
            if (current.getMessage() != null) {
                sb.append(current.getMessage()).append('\n');
            }
            current = current.getCause();
        }
        return sb.toString();
    }

    /**
     * Check if the validator's {@link IllegalStateException} (identified by the
     * "Production configuration" prefix in its message) is anywhere in the
     * exception cause chain. This proves the validator was actually executed
     * by the {@code ApplicationStartedEvent} listener mechanism.
     */
    private static boolean hasValidatorExceptionInChain(Throwable thrown) {
        Throwable current = thrown;
        while (current != null) {
            if (current instanceof IllegalStateException
                    && current.getMessage() != null
                    && current.getMessage().contains("Production configuration")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
