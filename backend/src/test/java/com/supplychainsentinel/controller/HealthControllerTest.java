package com.supplychainsentinel.controller;

import com.supplychainsentinel.dto.PingResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for the health controller and PingResponse DTO.
 */
class HealthControllerTest {

    @Test
    void pingReturnsUpStatus() {
        HealthController controller = new HealthController();
        PingResponse response = controller.ping();

        assertEquals("UP", response.status());
        assertEquals("supplychain-sentinel", response.service());
        assertEquals("0.1.0", response.version());
        assertNotNull(response.timestamp());
    }
}
