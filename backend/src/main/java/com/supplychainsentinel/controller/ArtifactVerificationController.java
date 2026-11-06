package com.supplychainsentinel.controller;

import com.supplychainsentinel.dto.ArtifactVerificationResponse;
import com.supplychainsentinel.service.ArtifactVerificationService;
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
 * Artifact verification read endpoints (JWT-authenticated, any role).
 * All results are CI-reported Cosign verification evidence.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Artifact Integrity", description = "Read CI-reported Cosign verification results")
public class ArtifactVerificationController {

    private final ArtifactVerificationService verificationService;

    public ArtifactVerificationController(ArtifactVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @GetMapping("/artifact-verifications")
    @Operation(summary = "List artifact verifications with filters and pagination")
    public ResponseEntity<Page<ArtifactVerificationResponse>> listVerifications(
            @RequestParam(required = false) UUID applicationId,
            @RequestParam(required = false) UUID pipelineRunId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String artifactDigest,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(
                verificationService.listVerifications(applicationId, pipelineRunId, status, artifactDigest, pageable));
    }

    @GetMapping("/applications/{id}/artifact-verifications")
    @Operation(summary = "Get artifact verification history for an application")
    public ResponseEntity<Page<ArtifactVerificationResponse>> getApplicationVerifications(
            @PathVariable UUID id,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(verificationService.getApplicationVerifications(id, pageable));
    }
}
