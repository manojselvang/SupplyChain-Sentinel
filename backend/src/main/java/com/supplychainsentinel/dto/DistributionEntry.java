package com.supplychainsentinel.dto;

/**
 * A single entry in a distribution (e.g. criticality → count).
 */
public record DistributionEntry(String key, Long count) {}
