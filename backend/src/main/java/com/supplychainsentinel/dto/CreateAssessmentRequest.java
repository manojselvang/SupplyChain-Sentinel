package com.supplychainsentinel.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record CreateAssessmentRequest(
    @NotBlank(message = "applicationId is required")
    String applicationId,
    @NotBlank(message = "pipelineRunId is required")
    String pipelineRunId
) {}
