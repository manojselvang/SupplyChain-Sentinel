package com.supplychainsentinel;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies that the Spring application context loads successfully.
 */
@SpringBootTest
@ActiveProfiles("test")
class SupplyChainSentinelApplicationTests {

    @Test
    void contextLoads() {
        // If the context fails to load, this test will fail before reaching here.
        assertNotNull(SupplyChainSentinelApplication.class);
    }
}
