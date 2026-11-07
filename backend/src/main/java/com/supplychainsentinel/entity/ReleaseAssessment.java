package com.supplychainsentinel.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "release_assessments")
public class ReleaseAssessment {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "application_id", nullable = false)
    private InventoryApplication application;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "pipeline_run_id", nullable = false)
    private PipelineRun pipelineRun;

    @Column(name = "artifact_digest", nullable = false)
    private String artifactDigest;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Column(nullable = false)
    private String decision;

    @Column(name = "policy_version", nullable = false)
    private String policyVersion;

    @Column(name = "assessment_status", nullable = false)
    private String assessmentStatus = "PENDING_APPROVAL";

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "hard_block_reasons", columnDefinition = "jsonb")
    private String hardBlockReasons;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "score_breakdown", columnDefinition = "jsonb")
    private String scoreBreakdown;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "evidence_snapshot", columnDefinition = "jsonb")
    private String evidenceSnapshot;

    @Column(name = "requested_by")
    private String requestedBy;

    @Column(name = "assessed_at", nullable = false)
    private Instant assessedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public ReleaseAssessment() {}

    public UUID getId() { return id; }
    public InventoryApplication getApplication() { return application; }
    public void setApplication(InventoryApplication a) { this.application = a; }
    public PipelineRun getPipelineRun() { return pipelineRun; }
    public void setPipelineRun(PipelineRun p) { this.pipelineRun = p; }
    public String getArtifactDigest() { return artifactDigest; }
    public void setArtifactDigest(String d) { this.artifactDigest = d; }
    public int getRiskScore() { return riskScore; }
    public void setRiskScore(int s) { this.riskScore = s; }
    public String getDecision() { return decision; }
    public void setDecision(String d) { this.decision = d; }
    public String getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(String p) { this.policyVersion = p; }
    public String getAssessmentStatus() { return assessmentStatus; }
    public void setAssessmentStatus(String s) { this.assessmentStatus = s; }
    public String getHardBlockReasons() { return hardBlockReasons; }
    public void setHardBlockReasons(String h) { this.hardBlockReasons = h; }
    public String getScoreBreakdown() { return scoreBreakdown; }
    public void setScoreBreakdown(String s) { this.scoreBreakdown = s; }
    public String getEvidenceSnapshot() { return evidenceSnapshot; }
    public void setEvidenceSnapshot(String e) { this.evidenceSnapshot = e; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String r) { this.requestedBy = r; }
    public Instant getAssessedAt() { return assessedAt; }
    public void setAssessedAt(Instant a) { this.assessedAt = a; }
    public Instant getCreatedAt() { return createdAt; }
}
