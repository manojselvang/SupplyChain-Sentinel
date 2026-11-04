package com.supplychainsentinel.service;

import com.supplychainsentinel.dto.DashboardSummaryDto;
import com.supplychainsentinel.dto.DistributionEntry;
import com.supplychainsentinel.dto.RecentApplicationDto;
import com.supplychainsentinel.dto.RecentPipelineRunDto;
import com.supplychainsentinel.entity.InventoryApplication;
import com.supplychainsentinel.entity.PipelineRun;
import com.supplychainsentinel.repository.ApplicationRepository;
import com.supplychainsentinel.repository.PipelineRunRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Dashboard business logic.
 *
 * <p>Uses database-level aggregation queries (GROUP BY + COUNT) rather than
 * loading the entire application table into memory.  All metrics originate
 * from PostgreSQL — no mock or hardcoded values.</p>
 */
@Service
public class DashboardService {

    private final ApplicationRepository applicationRepository;
    private final PipelineRunRepository pipelineRunRepository;

    public DashboardService(
            ApplicationRepository applicationRepository,
            PipelineRunRepository pipelineRunRepository
    ) {
        this.applicationRepository = applicationRepository;
        this.pipelineRunRepository = pipelineRunRepository;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryDto getSummary() {
        // ── KPI counts (single aggregate queries) ────────────
        long total = applicationRepository.count();
        long active = applicationRepository.countActive();
        long critical = applicationRepository.countCritical();
        long highCriticality = applicationRepository.countHighCriticality();
        long internetFacing = applicationRepository.countInternetFacing();
        long highCriticalInternetFacing = applicationRepository.countHighCriticalInternetFacing();

        // ── Distribution metrics (GROUP BY queries) ───────────
        List<DistributionEntry> criticalityDist = applicationRepository.distributionByCriticality();
        List<DistributionEntry> lifecycleDist = applicationRepository.distributionByLifecycleStatus();
        List<DistributionEntry> hostingDist = applicationRepository.distributionByHostingEnvironment();
        List<DistributionEntry> businessUnitDist = applicationRepository.distributionByBusinessUnit();

        // ── Recent applications (top 5 by updatedAt desc) ──────
        List<InventoryApplication> recent = applicationRepository
                .findAll(PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "updatedAt")))
                .getContent();

        List<RecentApplicationDto> recentApps = recent.stream()
                .map(app -> new RecentApplicationDto(
                        app.getId(),
                        app.getName(),
                        app.getApplicationKey(),
                        app.getCriticality(),
                        app.getLifecycleStatus(),
                        app.getUpdatedAt()
                ))
                .toList();

        // ── Pipeline KPI counts ────────────────────────────────
        long totalPipelineRuns = pipelineRunRepository.countAllRuns();
        long successfulPipelineRuns = pipelineRunRepository.countSuccessful();
        long failedPipelineRuns = pipelineRunRepository.countFailed();
        double successRate = totalPipelineRuns > 0
                ? (successfulPipelineRuns * 100.0 / totalPipelineRuns)
                : 0.0;

        // ── Recent pipeline runs (top 5 by started_at desc) ─────
        List<PipelineRun> recentRuns = pipelineRunRepository
                .findAll(PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "startedAt")))
                .getContent();

        List<RecentPipelineRunDto> recentPipelineDtos = recentRuns.stream()
                .map(run -> new RecentPipelineRunDto(
                        run.getId(),
                        run.getApplication().getName(),
                        run.getProvider(),
                        run.getWorkflowName(),
                        run.getStatus(),
                        run.getBranchName(),
                        run.getStartedAt(),
                        run.getCompletedAt()
                ))
                .toList();

        return new DashboardSummaryDto(
                total, active, critical, highCriticality, internetFacing, highCriticalInternetFacing,
                criticalityDist, lifecycleDist, hostingDist, businessUnitDist,
                recentApps,
                totalPipelineRuns, successfulPipelineRuns, failedPipelineRuns, successRate,
                recentPipelineDtos
        );
    }
}
