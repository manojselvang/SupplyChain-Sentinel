package com.supplychainsentinel.service.scanner;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.supplychainsentinel.service.scanner.ScannerParserUtils.*;

/**
 * Parses Trivy JSON report output ({@code trivy image/fs/repo --format json}).
 *
 * <p>Trivy JSON structure:
 * <pre>
 * {
 *   "Results": [
 *     {
 *       "Target": "...",
 *       "Type": "...",
 *       "Vulnerabilities": [
 *         {
 *           "VulnerabilityID": "CVE-2024-...",
 *           "PkgName": "libssl",
 *           "InstalledVersion": "1.1.1",
 *           "FixedVersion": "1.1.2",
 *           "Severity": "HIGH",
 *           "Title": "...",
 *           "Description": "...",
 *           "PrimaryURL": "https://..."
 *         }
 *       ]
 *     }
 *   ]
 * }
 * </pre>
 */
@Component
public class TrivyJsonParser implements ScannerParser {

    @Override
    public String reportFormat() {
        return "TRIVY_JSON";
    }

    @Override
    public List<NormalizedFinding> parse(String scanner, JsonNode report, UUID applicationId) {
        List<NormalizedFinding> findings = new ArrayList<>();

        JsonNode results = report.path("Results");
        if (!results.isArray()) return findings;

        for (JsonNode result : results) {
            String target = result.path("Target").asText("");
            JsonNode vulns = result.path("Vulnerabilities");
            if (!vulns.isArray()) continue;

            for (JsonNode vuln : vulns) {
                String cve = blankToNull(vuln.path("VulnerabilityID").asText(null));
                String pkgName = blankToNull(vuln.path("PkgName").asText(null));
                String installed = blankToNull(vuln.path("InstalledVersion").asText(null));
                String fixed = blankToNull(vuln.path("FixedVersion").asText(null));
                String rawSeverity = vuln.path("Severity").asText("UNKNOWN");
                String title = blankToNull(vuln.path("Title").asText(null));
                String description = blankToNull(vuln.path("Description").asText(null));
                String url = blankToNull(vuln.path("PrimaryURL").asText(null));

                if (title == null) title = cve != null ? cve : "Unknown vulnerability";

                // Fingerprint: stable across repeated scans of the same vuln in the same app
                String fp = fingerprint(
                        applicationId.toString(), "TRIVY",
                        cve != null ? cve : "",
                        pkgName != null ? pkgName : "",
                        target
                );

                findings.add(new NormalizedFinding(
                        fp, scanner, cve, null,
                        truncate(title, 500),
                        truncate(description, 4000),
                        normalizeSeverity(rawSeverity),
                        truncate(pkgName, 255),
                        truncate(installed, 100),
                        truncate(fixed, 100),
                        truncate(target, 500),
                        truncate(url, 500)
                ));
            }
        }
        return findings;
    }
}
