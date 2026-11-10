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
        List<RecentApplicationDto> recentApplications,

        // ── Pipeline KPI counts ──────────────────────────
        long totalPipelineRuns,
        long successfulPipelineRuns,
        long failedPipelineRuns,
        double pipelineSuccessRate,

        // ── Recent pipeline runs (top 5 by started_at desc) ─
        List<RecentPipelineRunDto> recentPipelineRuns,

        // ── Security finding KPI counts ──────────────────
        long openFindings,
        long criticalOpenFindings,
        long highOpenFindings,

        // ── Findings by severity (OPEN only) ─────────────
        List<DistributionEntry> openFindingsBySeverity,

        // ── Recent findings (top 5 by last_seen desc) ────
        List<RecentFindingDto> recentFindings,

        // ── SBOM KPI counts ──────────────────────────────
        long totalSbomDocuments,
        long totalSbomComponents,
        long verifiedArtifacts,
        long failedArtifactVerifications,

        // ── Recent SBOM documents (top 5 by created_at desc) ──
        List<RecentSbomDto> recentSbomDocuments,

        // ── Recent verifications (top 5 by created_at desc) ─
        List<RecentArtifactVerificationDto> recentArtifactVerifications,

        // ── Release assessment KPI counts ────────────────
        long totalReleaseAssessments,
        long passAssessments,
        long warnAssessments,
        long blockAssessments,
        long approvedReleases,
        long pendingApprovals,

        // ── Recent assessments (top 5 by assessed_at desc) ─
        List<RecentReleaseAssessmentDto> recentReleaseAssessments,

        // ── Deployment KPI counts ────────────────────────────
        long totalDeployments,
        long successfulDeployments,
        long failedDeployments,
        long degradedDeployments,

        // ── Recent deployments (top 5 by deployed_at desc) ──
        List<RecentDeploymentDto> recentDeployments
) {}
