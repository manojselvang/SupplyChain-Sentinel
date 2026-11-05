package com.supplychainsentinel.service.scanner;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.supplychainsentinel.service.scanner.ScannerParserUtils.*;

/**
 * Parses SARIF 2.1.0 report files as produced by tools like GitHub CodeQL,
 * Semgrep, ESLint, Checkov, etc.
 *
 * <p>SARIF structure used:
 * <pre>
 * {
 *   "runs": [
 *     {
 *       "tool": { "driver": { "name": "...", "rules": [...] } },
 *       "results": [
 *         {
 *           "ruleId": "...",
 *           "level": "error|warning|note",
 *           "message": { "text": "..." },
 *           "locations": [ { "physicalLocation": { "artifactLocation": { "uri": "..." }, "region": { "startLine": 1 } } } ],
 *           "relatedLocations": [...],
 *           "properties": { "security-severity": "7.5" }
 *         }
 *       ]
 *     }
 *   ]
 * }
 * </pre>
 */
@Component
public class SarifParser implements ScannerParser {

    @Override
    public String reportFormat() {
        return "SARIF";
    }

    @Override
    public List<NormalizedFinding> parse(String scanner, JsonNode report, UUID applicationId) {
        List<NormalizedFinding> findings = new ArrayList<>();

        JsonNode runs = report.path("runs");
        if (!runs.isArray()) return findings;

        for (JsonNode run : runs) {
            // Build a rule lookup map for descriptions
            Map<String, JsonNode> ruleMap = buildRuleMap(run);

            JsonNode results = run.path("results");
            if (!results.isArray()) continue;

            for (JsonNode result : results) {
                String ruleId = blankToNull(result.path("ruleId").asText(null));
                String level = result.path("level").asText("warning");
                String messageText = result.path("message").path("text").asText("");

                // Location
                JsonNode locs = result.path("locations");
                String location = "";
                if (locs.isArray() && locs.size() > 0) {
                    JsonNode phys = locs.get(0).path("physicalLocation");
                    String uri = phys.path("artifactLocation").path("uri").asText("");
                    int line = phys.path("region").path("startLine").asInt(0);
                    location = line > 0 ? uri + ":" + line : uri;
                }

                // Rule description
                String title = null;
                String description = null;
                String cve = null;
                if (ruleId != null && ruleMap.containsKey(ruleId)) {
                    JsonNode rule = ruleMap.get(ruleId);
                    title = blankToNull(rule.path("name").asText(null));
                    if (title == null) title = blankToNull(rule.path("shortDescription").path("text").asText(null));
                    description = blankToNull(rule.path("fullDescription").path("text").asText(null));
                    // Some SARIF rules embed CVE in tags
                    JsonNode tags = rule.path("properties").path("tags");
                    if (tags.isArray()) {
                        for (JsonNode tag : tags) {
                            String t = tag.asText("");
                            if (t.startsWith("CVE-")) { cve = t; break; }
                        }
                    }
                }
                if (title == null) title = messageText.isBlank() ? (ruleId != null ? ruleId : "Unknown finding") : messageText;
                if (description == null && !messageText.equals(title)) description = messageText;

                // Severity from SARIF level or numeric security-severity property
                String severity = sarifLevelToSeverity(level, result.path("properties").path("security-severity").asText(null));

                String fp = fingerprint(
                        applicationId.toString(), "SARIF",
                        ruleId != null ? ruleId : "",
                        location
                );

                findings.add(new NormalizedFinding(
                        fp, scanner, cve, ruleId,
                        truncate(title, 500),
                        truncate(description, 4000),
                        severity,
                        null, null, null,
                        truncate(location, 500),
                        null
                ));
            }
        }
        return findings;
    }

    private Map<String, JsonNode> buildRuleMap(JsonNode run) {
        Map<String, JsonNode> map = new HashMap<>();
        JsonNode rules = run.path("tool").path("driver").path("rules");
        if (rules.isArray()) {
            for (JsonNode rule : rules) {
                String id = rule.path("id").asText(null);
                if (id != null) map.put(id, rule);
            }
        }
        return map;
    }

    private String sarifLevelToSeverity(String level, String numericSecurity) {
        // Use numeric security-severity when present (CVSS-like 0-10)
        if (numericSecurity != null && !numericSecurity.isBlank()) {
            try {
                double score = Double.parseDouble(numericSecurity);
                if (score >= 9.0) return "CRITICAL";
                if (score >= 7.0) return "HIGH";
                if (score >= 4.0) return "MEDIUM";
                if (score >  0.0) return "LOW";
                return "INFO";
            } catch (NumberFormatException ignored) { }
        }
        return switch (level.toLowerCase()) {
            case "error"   -> "HIGH";
            case "warning" -> "MEDIUM";
            case "note"    -> "LOW";
            default        -> "INFO";
        };
    }
}
