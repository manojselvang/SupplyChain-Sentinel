package com.supplychainsentinel.controller;

import com.supplychainsentinel.dto.*;
import com.supplychainsentinel.service.ReleaseAssessmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Release Assurance", description = "Release risk assessment and approval workflow")
public class ReleaseAssessmentController {

    private final ReleaseAssessmentService svc;

    public ReleaseAssessmentController(ReleaseAssessmentService svc) {
        this.svc = svc;
    }

    @PostMapping("/release-assessments")
    @Operation(summary = "Create a new release risk assessment")
    public ResponseEntity<ReleaseAssessmentResponse> createAssessment(
            @Valid @RequestBody CreateAssessmentRequest req,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(svc.createAssessment(req, user.getUsername()));
    }

    @GetMapping("/release-assessments")
    @Operation(summary = "List release assessments with filters and pagination")
    public ResponseEntity<Page<ReleaseAssessmentResponse>> listAssessments(
            @RequestParam(required = false) UUID applicationId,
            @RequestParam(required = false) String decision,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "assessedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(svc.listAssessments(applicationId, decision, status, pageable));
    }

    @GetMapping("/release-assessments/{id}")
    @Operation(summary = "Get a release assessment with full evidence and audit history")
    public ResponseEntity<ReleaseAssessmentResponse> getAssessment(@PathVariable UUID id) {
        return ResponseEntity.ok(svc.getAssessment(id));
    }

    @GetMapping("/applications/{id}/release-assessments")
    @Operation(summary = "Get release assessment history for an application")
    public ResponseEntity<Page<ReleaseAssessmentResponse>> getApplicationAssessments(
            @PathVariable UUID id,
            @PageableDefault(size = 20, sort = "assessedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(svc.getApplicationAssessments(id, pageable));
    }

    @PostMapping("/release-assessments/{id}/approvals")
    @PreAuthorize("hasAnyRole('ADMIN','RELEASE_MANAGER')")
    @Operation(summary = "Approve or reject a release assessment (ADMIN, RELEASE_MANAGER)")
    public ResponseEntity<ApprovalResponse> submitApproval(
            @PathVariable UUID id,
            @Valid @RequestBody ApprovalRequest req,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(svc.submitApproval(id, req, user.getUsername()));
    }
}
