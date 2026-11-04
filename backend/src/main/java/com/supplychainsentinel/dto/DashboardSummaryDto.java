package com.supplychainsentinel.dto;

import java.util.List;

/**
 * Executive dashboard summary — all metrics derived from the application
 * inventory in PostgreSQL.  No mock data.
 *
 * <p>As later phases (pipeline, findings, SBOM, releases) are implemented,
 * this DTO will be extended with additional fields.</p>
 */
public record DashboardSummaryDto(

        // ── KPI counts ────────────────────────────────────
        long totalApplications,
        long activeApplications,
        long criticalApplications,
        long highCriticalityApplications,
        long internetFacingApplications,
        long highCriticalInternetFacing,

        // ── Distribution metrics ─────────────────────────
        List<DistributionEntry> criticalityDistribution,
        List<DistributionEntry> lifecycleStatusDistribution,
        List<DistributionEntry> hostingEnvironmentDistribution,
        List<DistributionEntry> businessUnitDistribution,

        // ── Recent applications (top 5 by updated_at) ─────
        List<RecentApplicationDto> recentApplications
) {}
