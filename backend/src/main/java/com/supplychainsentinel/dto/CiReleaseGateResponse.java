package com.supplychainsentinel.dto;

import java.util.List;
import java.util.UUID;

public record CiReleaseGateResponse(
    UUID assessmentId,
    String decision,
    int riskScore,
    List<String> hardBlockReasons,
    String summary
) {}
