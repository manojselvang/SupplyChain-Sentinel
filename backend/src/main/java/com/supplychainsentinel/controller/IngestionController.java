package com.supplychainsentinel.controller;

import com.supplychainsentinel.dto.PipelineRunIngestionRequest;
import com.supplychainsentinel.dto.PipelineRunResponse;
import com.supplychainsentinel.service.PipelineRunService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * CI/CD ingestion endpoint.
 *
 * <p>Authenticated via the {@code X-CI-INGESTION-KEY} header (validated by
 * {@link com.supplychainsentinel.security.IngestionKeyFilter}), not via JWT.
 * This route is configured as {@code permitAll()} in {@link
 * com.supplychainsentinel.security.SecurityConfig} — the filter is the
 * actual gatekeeper.</p>
 */
@RestController
@RequestMapping("/api/v1/integrations")
@Tag(name = "CI/CD Ingestion", description = "Pipeline run metadata ingestion from Jenkins and GitHub Actions")
public class IngestionController {

    private final PipelineRunService pipelineRunService;

    public IngestionController(PipelineRunService pipelineRunService) {
        this.pipelineRunService = pipelineRunService;
    }

    @PostMapping("/pipeline-runs")
    @Operation(summary = "Submit or update pipeline run metadata (API key auth)")
    public ResponseEntity<PipelineRunResponse> ingestPipelineRun(
            @Valid @RequestBody PipelineRunIngestionRequest request
    ) {
        PipelineRunService.IngestionResult result = pipelineRunService.ingest(request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.response());
    }
}
