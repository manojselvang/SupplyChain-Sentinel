package com.supplychainsentinel.controller;

import com.supplychainsentinel.dto.PingResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public health / liveness endpoint.
 * Deep health details are exposed via Spring Actuator /actuator/health.
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @GetMapping("/ping")
    public PingResponse ping() {
        return PingResponse.ok();
    }
}
