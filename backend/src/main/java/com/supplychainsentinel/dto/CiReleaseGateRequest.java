package com.supplychainsentinel.dto;

import jakarta.validation.constraints.NotBlank;

public record CiReleaseGateRequest(
    @NotBlank(message = "applicationKey is required")
    String applicationKey,
    @NotBlank(message = "pipelineRunId is required")
    String pipelineRunId
) {}
