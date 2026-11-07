package com.supplychainsentinel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ApprovalRequest(
    @NotBlank(message = "decision is required")
    @Pattern(regexp = "APPROVED|REJECTED", message = "decision must be APPROVED or REJECTED")
    String decision,
    String justification
) {}
