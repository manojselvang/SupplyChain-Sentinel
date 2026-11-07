package com.supplychainsentinel.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReleaseAssessmentResponse(
    UUID id,
    UUID applicationId,
    String applicationName,
    UUID pipelineRunId,
    String artifactDigest,
    int riskScore,
    String decision,
    String policyVersion,
    String assessmentStatus,
    String hardBlockReasons,
    String scoreBreakdown,
    String evidenceSnapshot,
    String requestedBy,
    Instant assessedAt,
    Instant createdAt,
    List<ApprovalResponse> approvals,
    List<AuditEventResponse> auditHistory
) {}
