package com.supplychainsentinel.controller;

import com.supplychainsentinel.dto.DashboardSummaryDto;
import com.supplychainsentinel.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Executive dashboard endpoint.
 *
 * <p>Available to all authenticated roles (ADMIN, SECURITY_ENGINEER,
 * DEVELOPER, RELEASE_MANAGER, AUDITOR).  Unauthenticated requests return 401
 * via the Spring Security filter chain.</p>
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Executive dashboard metrics from the application inventory")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Get dashboard summary metrics (any authenticated role)")
    public ResponseEntity<DashboardSummaryDto> getSummary() {
        return ResponseEntity.ok(dashboardService.getSummary());
    }
}
