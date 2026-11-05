package com.supplychainsentinel.controller;

import com.supplychainsentinel.dto.PipelineRunIngestionRequest;
import com.supplychainsentinel.dto.PipelineRunResponse;
import com.supplychainsentinel.dto.SecurityScanIngestionRequest;
import com.supplychainsentinel.dto.SecurityScanResponse;
import com.supplychainsentinel.service.PipelineRunService;
import com.supplychainsentinel.service.SecurityFindingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * CI/CD ingestion endpoints.
 * Authenticated via the {@code X-CI-INGESTION-KEY} header (IngestionKeyFilter).
 */
@RestController
@RequestMapping("/api/v1/integrations")
@Tag(name = "CI/CD Ingestion", description = "Pipeline run and security scan ingestion")
public class IngestionController {

    private final PipelineRunService pipelineRunService;
    private final SecurityFindingService securityFindingService;

    public IngestionController(
            PipelineRunService pipelineRunService,
            SecurityFindingService securityFindingService
    ) {
        this.pipelineRunService = pipelineRunService;
        this.securityFindingService = securityFindingService;
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

    @PostMapping("/security-scans")
    @Operation(summary = "Submit scanner report (Trivy JSON or SARIF) (API key auth)")
    public ResponseEntity<SecurityScanResponse> ingestSecurityScan(
            @Valid @RequestBody SecurityScanIngestionRequest request
    ) {
        SecurityScanResponse result = securityFindingService.ingestScan(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}
