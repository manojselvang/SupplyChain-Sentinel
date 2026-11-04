package com.supplychainsentinel.dto;

import java.time.Instant;

/**
 * Simple liveness response for the public ping endpoint.
 */
public record PingResponse(
        String status,
        String service,
        String version,
        Instant timestamp
) {
    public static PingResponse ok() {
        return new PingResponse("UP", "supplychain-sentinel", "0.1.0", Instant.now());
    }
}
