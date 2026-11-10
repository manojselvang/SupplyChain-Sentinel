package com.supplychainsentinel.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the <strong>deployment_records</strong> table (V9).
 * Tracks Argo CD deployment events reported via webhook.
 */
@Entity
@Table(name = "deployment_records")
public class DeploymentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "application_id", nullable = false)
    private InventoryApplication application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pipeline_run_id")
    private PipelineRun pipelineRun;

    @Column(name = "release_assessment_id")
    private UUID releaseAssessmentId;

    @Column(name = "argocd_application", nullable = false)
    private String argocdApplication;

    @Column(name = "argocd_project")
    private String argocdProject;

    @Column(nullable = false)
    private String environment;

    @Column
    private String cluster;

    @Column
    private String namespace;

    @Column(name = "git_repo_url")
    private String gitRepoUrl;

    @Column(name = "git_revision")
    private String gitRevision;

    @Column(name = "image_reference", nullable = false)
    private String imageReference;

    @Column(name = "image_digest")
    private String imageDigest;

    @Column(name = "sync_status", nullable = false)
    private String syncStatus = "UNKNOWN";

    @Column(name = "health_status", nullable = false)
    private String healthStatus = "UNKNOWN";

    @Column(name = "operation_phase")
    private String operationPhase;

    @Column(name = "deployed_at", nullable = false)
    private Instant deployedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "external_event_id", nullable = false)
    private String externalEventId;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "webhook_evidence", columnDefinition = "jsonb")
    private String webhookEvidence;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public DeploymentRecord() { }

    public UUID getId() { return id; }
    public InventoryApplication getApplication() { return application; }
    public void setApplication(InventoryApplication a) { this.application = a; }
    public PipelineRun getPipelineRun() { return pipelineRun; }
    public void setPipelineRun(PipelineRun p) { this.pipelineRun = p; }
    public UUID getReleaseAssessmentId() { return releaseAssessmentId; }
    public void setReleaseAssessmentId(UUID r) { this.releaseAssessmentId = r; }
    public String getArgocdApplication() { return argocdApplication; }
    public void setArgocdApplication(String a) { this.argocdApplication = a; }
    public String getArgocdProject() { return argocdProject; }
    public void setArgocdProject(String a) { this.argocdProject = a; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String e) { this.environment = e; }
    public String getCluster() { return cluster; }
    public void setCluster(String c) { this.cluster = c; }
    public String getNamespace() { return namespace; }
    public void setNamespace(String n) { this.namespace = n; }
    public String getGitRepoUrl() { return gitRepoUrl; }
    public void setGitRepoUrl(String g) { this.gitRepoUrl = g; }
    public String getGitRevision() { return gitRevision; }
    public void setGitRevision(String g) { this.gitRevision = g; }
    public String getImageReference() { return imageReference; }
    public void setImageReference(String i) { this.imageReference = i; }
    public String getImageDigest() { return imageDigest; }
    public void setImageDigest(String i) { this.imageDigest = i; }
    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(String s) { this.syncStatus = s; }
    public String getHealthStatus() { return healthStatus; }
    public void setHealthStatus(String h) { this.healthStatus = h; }
    public String getOperationPhase() { return operationPhase; }
    public void setOperationPhase(String o) { this.operationPhase = o; }
    public Instant getDeployedAt() { return deployedAt; }
    public void setDeployedAt(Instant d) { this.deployedAt = d; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant c) { this.completedAt = c; }
    public String getExternalEventId() { return externalEventId; }
    public void setExternalEventId(String e) { this.externalEventId = e; }
    public String getWebhookEvidence() { return webhookEvidence; }
    public void setWebhookEvidence(String w) { this.webhookEvidence = w; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
