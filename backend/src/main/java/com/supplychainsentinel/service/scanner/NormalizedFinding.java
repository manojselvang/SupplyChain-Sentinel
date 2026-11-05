package com.supplychainsentinel.service.scanner;

/**
 * Intermediate normalized representation of a finding from any scanner.
 * Scanner-specific parsers produce these; the ingestion service persists them.
 */
public record NormalizedFinding(
        String fingerprint,       // deterministic SHA-256 of (appId + scanner + key fields)
        String scanner,
        String vulnerabilityId,   // CVE, GHSA, etc.
        String ruleId,            // SARIF rule ID
        String title,
        String description,
        String severity,          // normalized: CRITICAL|HIGH|MEDIUM|LOW|INFO|UNKNOWN
        String affectedPackage,
        String installedVersion,
        String fixedVersion,
        String affectedLocation,  // file path or target
        String remediationUrl
) {}
