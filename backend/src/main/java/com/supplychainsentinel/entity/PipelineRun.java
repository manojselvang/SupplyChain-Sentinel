package com.supplychainsentinel.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the <strong>pipeline_runs</strong> table (created in V4).
 *
 * Stores CI/CD build metadata submitted by Jenkins or GitHub Actions
 * via the ingestion API.
 */
@Entity
@Table(name = "pipeline_runs")
public class PipelineRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "application_id", nullable = false)
    private InventoryApplication application;

    @Column(nullable = false)
    private String provider;

    @Column(name = "external_run_id", nullable = false)
    private String externalRunId;

    @Column(name = "workflow_name")
    private String workflowName;

    @Column(name = "repository_url", nullable = false)
    private String repositoryUrl;

    @Column(name = "branch_name")
    private String branchName;

    @Column(name = "commit_sha")
    private String commitSha;

    @Column(name = "commit_message", columnDefinition = "TEXT")
    private String commitMessage;

    @Column(name = "run_number")
    private String runNumber;

    @Column(nullable = false)
    private String status = "QUEUED";

    @Column(name = "trigger_event")
    private String triggerEvent;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "artifact_name")
    private String artifactName;

    @Column(name = "artifact_version")
    private String artifactVersion;

    @Column(name = "artifact_digest")
    private String artifactDigest;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public PipelineRun() { }

    // ── Getters / Setters ─────────────────────────────────────

    public UUID getId() { return id; }
    public InventoryApplication getApplication() { return application; }
    public void setApplication(InventoryApplication application) { this.application = application; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getExternalRunId() { return externalRunId; }
    public void setExternalRunId(String externalRunId) { this.externalRunId = externalRunId; }
    public String getWorkflowName() { return workflowName; }
    public void setWorkflowName(String workflowName) { this.workflowName = workflowName; }
    public String getRepositoryUrl() { return repositoryUrl; }
    public void setRepositoryUrl(String repositoryUrl) { this.repositoryUrl = repositoryUrl; }
    public String getBranchName() { return branchName; }
    public void setBranchName(String branchName) { this.branchName = branchName; }
    public String getCommitSha() { return commitSha; }
    public void setCommitSha(String commitSha) { this.commitSha = commitSha; }
    public String getCommitMessage() { return commitMessage; }
    public void setCommitMessage(String commitMessage) { this.commitMessage = commitMessage; }
    public String getRunNumber() { return runNumber; }
    public void setRunNumber(String runNumber) { this.runNumber = runNumber; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTriggerEvent() { return triggerEvent; }
    public void setTriggerEvent(String triggerEvent) { this.triggerEvent = triggerEvent; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public String getArtifactName() { return artifactName; }
    public void setArtifactName(String artifactName) { this.artifactName = artifactName; }
    public String getArtifactVersion() { return artifactVersion; }
    public void setArtifactVersion(String artifactVersion) { this.artifactVersion = artifactVersion; }
    public String getArtifactDigest() { return artifactDigest; }
    public void setArtifactDigest(String artifactDigest) { this.artifactDigest = artifactDigest; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
