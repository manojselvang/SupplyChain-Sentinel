package com.supplychainsentinel.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Artifact verification response DTO — CI-reported Cosign results.
 */
public record ArtifactVerificationResponse(
        UUID id,
        UUID applicationId,
        String applicationName,
        UUID pipelineRunId,
        String artifactReference,
        String artifactDigest,
        String verificationTool,
        String verificationMode,
        String verificationStatus,
        String signerIdentity,
        String oidcIssuer,
        String publicKeyFingerprint,
        String cosignVersion,
        Instant verifiedAt,
        Instant reportedAt,
        Instant createdAt
) {}
