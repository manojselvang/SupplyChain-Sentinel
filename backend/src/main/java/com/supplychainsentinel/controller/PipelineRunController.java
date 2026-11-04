package com.supplychainsentinel.controller;

import com.supplychainsentinel.dto.PipelineRunResponse;
import com.supplychainsentinel.service.PipelineRunService;
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
 * Pipeline run read endpoints (JWT-authenticated, any role).
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Pipeline Runs", description = "Read pipeline execution metadata")
public class PipelineRunController {

    private final PipelineRunService pipelineRunService;

    public PipelineRunController(PipelineRunService pipelineRunService) {
        this.pipelineRunService = pipelineRunService;
    }

    @GetMapping("/pipeline-runs")
    @Operation(summary = "List pipeline runs with filters and pagination")
    public ResponseEntity<Page<PipelineRunResponse>> listPipelineRuns(
            @RequestParam(name = "applicationId", required = false) UUID applicationId,
            @RequestParam(name = "provider", required = false) String provider,
            @RequestParam(name = "status", required = false) String status,
            @PageableDefault(size = 20, sort = "startedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(
                pipelineRunService.listPipelineRuns(applicationId, provider, status, pageable));
    }

    @GetMapping("/applications/{id}/pipeline-runs")
    @Operation(summary = "Get pipeline execution history for an application")
    public ResponseEntity<Page<PipelineRunResponse>> getApplicationPipelineRuns(
            @PathVariable UUID id,
            @PageableDefault(size = 20, sort = "startedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(
                pipelineRunService.getApplicationPipelineRuns(id, pageable));
    }
}
