package com.supplychainsentinel.controller;

import com.supplychainsentinel.dto.DeploymentRecordResponse;
import com.supplychainsentinel.service.DeploymentRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Deployment record read endpoints (JWT-authenticated, any role).
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Deployments", description = "Read Argo CD deployment records")
public class DeploymentController {

    private final DeploymentRecordService deploymentService;

    public DeploymentController(DeploymentRecordService deploymentService) {
        this.deploymentService = deploymentService;
    }

    @GetMapping("/deployments")
    @Operation(summary = "List deployment records with filters and pagination")
    public ResponseEntity<Page<DeploymentRecordResponse>> listDeployments(
            @RequestParam(required = false) UUID applicationId,
            @RequestParam(required = false) String syncStatus,
            @RequestParam(required = false) String healthStatus,
            @RequestParam(required = false) String environment,
            @PageableDefault(size = 20, sort = "deployedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(deploymentService.listDeployments(applicationId, syncStatus, healthStatus, environment, pageable));
    }

    @GetMapping("/applications/{id}/deployments")
    @Operation(summary = "Get deployment history for an application")
    public ResponseEntity<Page<DeploymentRecordResponse>> getApplicationDeployments(
            @PathVariable UUID id,
            @PageableDefault(size = 20, sort = "deployedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(deploymentService.getApplicationDeployments(id, pageable));
    }
}
