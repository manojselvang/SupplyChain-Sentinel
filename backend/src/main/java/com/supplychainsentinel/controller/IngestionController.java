package com.supplychainsentinel.controller;

import com.supplychainsentinel.dto.*;
import com.supplychainsentinel.service.*;
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
@Tag(name = "CI/CD Ingestion", description = "Pipeline run, security scan, SBOM and artifact verification ingestion")
public class IngestionController {

    private final PipelineRunService pipelineRunService;
    private final SecurityFindingService securityFindingService;
    private final SbomService sbomService;
    private final ArtifactVerificationService artifactVerificationService;

    public IngestionController(
            PipelineRunService pipelineRunService,
            SecurityFindingService securityFindingService,
            SbomService sbomService,
            ArtifactVerificationService artifactVerificationService
    ) {
        this.pipelineRunService = pipelineRunService;
        this.securityFindingService = securityFindingService;
        this.sbomService = sbomService;
        this.artifactVerificationService = artifactVerificationService;
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

    @PostMapping("/sboms")
    @Operation(summary = "Submit CycloneDX SBOM document (API key auth)")
    public ResponseEntity<SbomIngestionResponse> ingestSbom(
            @Valid @RequestBody SbomIngestionRequest request
    ) {
        SbomIngestionResponse result = sbomService.ingestSbom(request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result);
    }

    @PostMapping("/artifact-verifications")
    @Operation(summary = "Submit CI-reported Cosign verification result (API key auth)")
    public ResponseEntity<ArtifactVerificationResponse> ingestVerification(
            @Valid @RequestBody ArtifactVerificationIngestionRequest request
    ) {
        ArtifactVerificationResponse result = artifactVerificationService.ingestVerification(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}
