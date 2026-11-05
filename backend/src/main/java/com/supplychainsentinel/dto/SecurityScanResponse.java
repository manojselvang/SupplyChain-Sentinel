package com.supplychainsentinel.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response returned after a security scan ingestion.
 */
public record SecurityScanResponse(
        UUID scanId,
        UUID applicationId,
        String applicationName,
        UUID pipelineRunId,
        String scanner,
        String reportFormat,
        Instant scanTimestamp,
        int totalFindings,
        int newFindings,
        int updatedFindings,
        List<SecurityFindingResponse> findings
) {}
