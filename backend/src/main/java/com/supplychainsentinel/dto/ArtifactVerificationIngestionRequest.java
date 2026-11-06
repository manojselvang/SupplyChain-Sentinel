package com.supplychainsentinel.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.UUID;

/**
 * Request body for CI-reported Cosign artifact verification.
 */
public record ArtifactVerificationIngestionRequest(
        @NotBlank(message = "applicationKey is required")
        String applicationKey,

        UUID pipelineRunId,

        @NotBlank(message = "artifactReference is required")
        String artifactReference,

        @Pattern(regexp = "[a-fA-F0-9]{64}",
                message = "artifactDigest must be a valid SHA-256 hex string")
        String artifactDigest,

        @NotBlank(message = "verificationMode is required")
        @Pattern(regexp = "KEY|KEYLESS",
                message = "verificationMode must be KEY or KEYLESS")
        String verificationMode,

        @NotBlank(message = "verificationStatus is required")
        @Pattern(regexp = "VERIFIED|FAILED|MISSING_SIGNATURE|ERROR",
                message = "verificationStatus must be one of: VERIFIED, FAILED, MISSING_SIGNATURE, ERROR")
        String verificationStatus,

        String signerIdentity,
        String oidcIssuer,
        String publicKeyFingerprint,
        String cosignVersion,
        Instant verifiedAt,
        Instant reportedAt,
        JsonNode verificationEvidence
) {}
