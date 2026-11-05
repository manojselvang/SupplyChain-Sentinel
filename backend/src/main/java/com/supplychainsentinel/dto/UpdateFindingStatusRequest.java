package com.supplychainsentinel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for updating a finding's triage status.
 */
public record UpdateFindingStatusRequest(
        @NotBlank(message = "status is required")
        @Pattern(regexp = "OPEN|ACKNOWLEDGED|REMEDIATED|ACCEPTED",
                message = "status must be one of: OPEN, ACKNOWLEDGED, REMEDIATED, ACCEPTED")
        String status
) {}
