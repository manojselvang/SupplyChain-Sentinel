package com.supplychainsentinel.service.scanner;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Strategy interface for scanner-report parsers.
 * Each implementation handles one report format.
 */
public interface ScannerParser {
    /**
     * Returns the {@code reportFormat} string this parser handles.
     */
    String reportFormat();

    /**
     * Parses the raw scanner JSON and returns a list of normalized findings.
     * @param scanner the scanner name submitted by the caller (e.g. "trivy")
     * @param report the raw scanner JSON node
     * @param applicationId the application UUID (used in fingerprint generation)
     */
    List<NormalizedFinding> parse(String scanner, JsonNode report, java.util.UUID applicationId);
}
