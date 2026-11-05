package com.supplychainsentinel.service.scanner;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Shared utilities for scanner parsers.
 */
public final class ScannerParserUtils {

    private ScannerParserUtils() { }

    /**
     * Normalizes raw scanner severity labels to our canonical set.
     */
    public static String normalizeSeverity(String raw) {
        if (raw == null) return "UNKNOWN";
        return switch (raw.trim().toUpperCase()) {
            case "CRITICAL" -> "CRITICAL";
            case "HIGH"     -> "HIGH";
            case "MEDIUM", "MODERATE", "WARNING", "WARN" -> "MEDIUM";
            case "LOW"      -> "LOW";
            case "INFO", "INFORMATIONAL", "NOTE", "NONE" -> "INFO";
            default         -> "UNKNOWN";
        };
    }

    /**
     * Creates a deterministic SHA-256 fingerprint from the key identifying fields.
     * The fingerprint is stable: the same finding from repeated scans produces the same value.
     */
    public static String fingerprint(String... parts) {
        String input = String.join("|", parts);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** Truncate a string safely for DB column length limits. */
    public static String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    /** Return text(value) or null if null/blank. */
    public static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
