package com.supplychainsentinel.controller;

import com.supplychainsentinel.dto.*;
import com.supplychainsentinel.service.SecurityFindingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Security findings read and status-update endpoints (JWT-authenticated).
 *
 * <ul>
 *   <li>Read — any authenticated role</li>
 *   <li>Status update — ADMIN and SECURITY_ENGINEER only</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Security Findings", description = "Read and triage security findings")
public class SecurityFindingController {

    private final SecurityFindingService findingService;

    public SecurityFindingController(SecurityFindingService findingService) {
        this.findingService = findingService;
    }

    @GetMapping("/security-findings")
    @Operation(summary = "List security findings with filters and pagination")
    public ResponseEntity<Page<SecurityFindingResponse>> listFindings(
            @RequestParam(required = false) UUID applicationId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String scanner,
            @RequestParam(required = false) String vulnerabilityId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "lastSeen", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(
                findingService.listFindings(applicationId, severity, status, scanner, vulnerabilityId, search, pageable));
    }

    @GetMapping("/security-findings/{id}")
    @Operation(summary = "Get a single security finding by ID")
    public ResponseEntity<SecurityFindingResponse> getFinding(@PathVariable UUID id) {
        return ResponseEntity.ok(findingService.getFinding(id));
    }

    @PatchMapping("/security-findings/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECURITY_ENGINEER')")
    @Operation(summary = "Update finding triage status (ADMIN, SECURITY_ENGINEER)")
    public ResponseEntity<SecurityFindingResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateFindingStatusRequest request
    ) {
        return ResponseEntity.ok(findingService.updateStatus(id, request));
    }

    @GetMapping("/applications/{id}/security-findings")
    @Operation(summary = "Get security findings for an application")
    public ResponseEntity<Page<SecurityFindingResponse>> getApplicationFindings(
            @PathVariable UUID id,
            @PageableDefault(size = 20, sort = "lastSeen", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(findingService.getApplicationFindings(id, pageable));
    }
}
