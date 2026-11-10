package com.supplychainsentinel.service;

import com.supplychainsentinel.dto.DashboardSummaryDto;
import com.supplychainsentinel.dto.DistributionEntry;
import com.supplychainsentinel.dto.RecentApplicationDto;
import com.supplychainsentinel.dto.RecentArtifactVerificationDto;
import com.supplychainsentinel.dto.RecentDeploymentDto;
import com.supplychainsentinel.dto.RecentFindingDto;
import com.supplychainsentinel.dto.RecentPipelineRunDto;
import com.supplychainsentinel.dto.RecentReleaseAssessmentDto;
import com.supplychainsentinel.dto.RecentSbomDto;
import com.supplychainsentinel.entity.ArtifactVerification;
import com.supplychainsentinel.entity.DeploymentRecord;
import com.supplychainsentinel.entity.InventoryApplication;
import com.supplychainsentinel.entity.PipelineRun;
import com.supplychainsentinel.entity.ReleaseAssessment;
import com.supplychainsentinel.entity.SbomDocument;
import com.supplychainsentinel.entity.SecurityFinding;
import com.supplychainsentinel.repository.ApplicationRepository;
import com.supplychainsentinel.repository.ArtifactVerificationRepository;
import com.supplychainsentinel.repository.DeploymentRecordRepository;
import com.supplychainsentinel.repository.PipelineRunRepository;
import com.supplychainsentinel.repository.ReleaseAssessmentRepository;
import com.supplychainsentinel.repository.SbomDocumentRepository;
import com.supplychainsentinel.repository.SecurityFindingRepository;
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
    private final SecurityFindingRepository findingRepository;
    private final SbomDocumentRepository sbomDocRepository;
    private final ArtifactVerificationRepository verificationRepository;
    private final ReleaseAssessmentRepository assessmentRepository;
    private final DeploymentRecordRepository deploymentRepository;

    public DashboardService(
            ApplicationRepository applicationRepository,
            PipelineRunRepository pipelineRunRepository,
            SecurityFindingRepository findingRepository,
            SbomDocumentRepository sbomDocRepository,
            ArtifactVerificationRepository verificationRepository,
            ReleaseAssessmentRepository assessmentRepository,
            DeploymentRecordRepository deploymentRepository
    ) {
        this.applicationRepository = applicationRepository;
        this.pipelineRunRepository = pipelineRunRepository;
        this.findingRepository = findingRepository;
        this.sbomDocRepository = sbomDocRepository;
        this.verificationRepository = verificationRepository;
        this.assessmentRepository = assessmentRepository;
        this.deploymentRepository = deploymentRepository;
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

        // ── Security finding KPI counts ────────────────────────
        long openFindings = findingRepository.countOpenFindings();
        long criticalOpenFindings = findingRepository.countOpenCritical();
        long highOpenFindings = findingRepository.countOpenHigh();
        List<DistributionEntry> openBySeverity = findingRepository.openFindingsBySeverity();

        // ── Recent findings (top 5 by lastSeen desc) ──────────
        List<SecurityFinding> recentFindingsList = findingRepository
                .findAll(PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "lastSeen")))
                .getContent();

        List<RecentFindingDto> recentFindingDtos = recentFindingsList.stream()
                .map(f -> new RecentFindingDto(
                        f.getId(),
                        f.getApplication().getName(),
                        f.getTitle(),
                        f.getVulnerabilityId(),
                        f.getSeverity(),
                        f.getScanner(),
                        f.getStatus(),
                        f.getLastSeen()
                ))
                .toList();

        // ── SBOM KPI counts ────────────────────────────────────
        long totalSbomDocuments = sbomDocRepository.count();
        long totalSbomComponents = sbomDocRepository.sumAllComponents();
        long verifiedArtifacts = verificationRepository.countVerified();
        long failedVerifications = verificationRepository.countFailed();

        // ── Recent SBOM documents (top 5 by createdAt desc) ────
        List<SbomDocument> recentSboms = sbomDocRepository
                .findAll(PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();

        List<RecentSbomDto> recentSbomDtos = recentSboms.stream()
                .map(s -> new RecentSbomDto(
                        s.getId(),
                        s.getApplication().getName(),
                        s.getSpecVersion(),
                        s.getComponentCount(),
                        s.getArtifactDigest(),
                        s.getIngestedAt()
                ))
                .toList();

        // ── Recent artifact verifications (top 5 by createdAt desc) ─
        List<ArtifactVerification> recentVerifications = verificationRepository
                .findAll(PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();

        List<RecentArtifactVerificationDto> recentVerificationDtos = recentVerifications.stream()
                .map(av -> new RecentArtifactVerificationDto(
                        av.getId(),
                        av.getApplication().getName(),
                        av.getArtifactReference(),
                        av.getVerificationStatus(),
                        av.getVerificationMode(),
                        av.getReportedAt()
                ))
                .toList();

        // ── Release assessment KPI counts ─────────────────────
        long totalAssessments = assessmentRepository.count();
        long passCount = assessmentRepository.findAll((root, q, cb) ->
                cb.equal(root.get("decision"), "PASS")).size();
        long warnCount = assessmentRepository.findAll((root, q, cb) ->
                cb.equal(root.get("decision"), "WARN")).size();
        long blockCount = assessmentRepository.findAll((root, q, cb) ->
                cb.equal(root.get("decision"), "BLOCK")).size();
        long approvedCount = assessmentRepository.findAll((root, q, cb) ->
                cb.equal(root.get("assessmentStatus"), "READY_FOR_DEPLOYMENT")).size();
        long pendingApprovalCount = assessmentRepository.findAll((root, q, cb) ->
                cb.equal(root.get("assessmentStatus"), "PENDING_APPROVAL")).size();

        // ── Recent assessments (top 5 by assessedAt desc) ──────
        List<ReleaseAssessment> recentAssessments = assessmentRepository
                .findAll(PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "assessedAt")))
                .getContent();
        List<RecentReleaseAssessmentDto> recentAssessmentDtos = recentAssessments.stream()
                .map(a -> new RecentReleaseAssessmentDto(
                        a.getId(), a.getApplication().getName(),
                        a.getDecision(), a.getRiskScore(), a.getAssessmentStatus(),
                        a.getArtifactDigest(), a.getAssessedAt()))
                .toList();

        // ── Deployment KPI counts ─────────────────────────────
        long totalDeployments = deploymentRepository.countAllDeployments();
        long successfulDeployments = deploymentRepository.countSuccessful();
        long failedDeployments = deploymentRepository.countFailed();
        long degradedDeployments = deploymentRepository.countDegraded();

        // ── Recent deployments (top 5 by deployedAt desc) ──────
        List<DeploymentRecord> recentDeploymentsList = deploymentRepository
                .findAll(PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "deployedAt")))
                .getContent();
        List<RecentDeploymentDto> recentDeploymentDtos = recentDeploymentsList.stream()
                .map(d -> new RecentDeploymentDto(
                        d.getId(), d.getApplication().getName(),
                        d.getArgocdApplication(), d.getEnvironment(),
                        d.getSyncStatus(), d.getHealthStatus(),
                        d.getImageDigest(), d.getDeployedAt()))
                .toList();

        return new DashboardSummaryDto(
                total, active, critical, highCriticality, internetFacing, highCriticalInternetFacing,
                criticalityDist, lifecycleDist, hostingDist, businessUnitDist,
                recentApps,
                totalPipelineRuns, successfulPipelineRuns, failedPipelineRuns, successRate,
                recentPipelineDtos,
                openFindings, criticalOpenFindings, highOpenFindings,
                openBySeverity, recentFindingDtos,
                totalSbomDocuments, totalSbomComponents, verifiedArtifacts, failedVerifications,
                recentSbomDtos, recentVerificationDtos,
                totalAssessments, passCount, warnCount, blockCount,
                approvedCount, pendingApprovalCount, recentAssessmentDtos,
                totalDeployments, successfulDeployments, failedDeployments, degradedDeployments,
                recentDeploymentDtos
        );
    }
}
