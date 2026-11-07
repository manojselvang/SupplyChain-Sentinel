package com.supplychainsentinel.dto;

import java.time.Instant;
import java.util.UUID;

public record ApprovalResponse(
    UUID id,
    UUID approverId,
    String decision,
    String justification,
    Instant approvedAt,
    Instant createdAt
) {}
