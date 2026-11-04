package com.supplychainsentinel.controller;

import com.supplychainsentinel.dto.ApplicationResponse;
import com.supplychainsentinel.dto.CreateApplicationRequest;
import com.supplychainsentinel.dto.UpdateApplicationRequest;
import com.supplychainsentinel.service.ApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Application inventory REST endpoints.
 *
 * <p>RBAC policy:</p>
 * <ul>
 *   <li><b>ADMIN</b> — full CRUD</li>
 *   <li><b>SECURITY_ENGINEER</b> — create, read, update (no delete)</li>
 *   <li><b>DEVELOPER / RELEASE_MANAGER / AUDITOR</b> — read-only</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/applications")
@Tag(name = "Application Inventory", description = "Register and manage applications in the security inventory")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SECURITY_ENGINEER')")
    @Operation(summary = "Register a new application (ADMIN, SECURITY_ENGINEER)")
    public ResponseEntity<ApplicationResponse> createApplication(
            @Valid @RequestBody CreateApplicationRequest request
    ) {
        ApplicationResponse created = applicationService.createApplication(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @Operation(summary = "List applications with search, filtering and pagination")
    public ResponseEntity<Page<ApplicationResponse>> listApplications(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "criticality", required = false) String criticality,
            @RequestParam(name = "lifecycleStatus", required = false) String lifecycleStatus,
            @PageableDefault(size = 20, sort = "name") Pageable pageable
    ) {
        Page<ApplicationResponse> page = applicationService
                .listApplications(search, criticality, lifecycleStatus, pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single application by ID")
    public ResponseEntity<ApplicationResponse> getApplication(@PathVariable UUID id) {
        return ResponseEntity.ok(applicationService.getApplicationById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECURITY_ENGINEER')")
    @Operation(summary = "Update an application (ADMIN, SECURITY_ENGINEER)")
    public ResponseEntity<ApplicationResponse> updateApplication(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateApplicationRequest request
    ) {
        return ResponseEntity.ok(applicationService.updateApplication(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete an application (ADMIN only)")
    public ResponseEntity<Void> deleteApplication(@PathVariable UUID id) {
        applicationService.deleteApplication(id);
        return ResponseEntity.noContent().build();
    }
}
