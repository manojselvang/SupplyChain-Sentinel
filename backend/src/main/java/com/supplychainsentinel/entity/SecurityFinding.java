package com.supplychainsentinel.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the <strong>security_findings</strong> table (V5).
 * One row per (application, fingerprint) — deduplicated across repeated scans.
 */
@Entity
@Table(name = "security_findings")
public class SecurityFinding {

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

    @Column(nullable = false)
    private String fingerprint;

    @Column(nullable = false)
    private String scanner;

    @Column(name = "vulnerability_id")
    private String vulnerabilityId;

    @Column(name = "rule_id")
    private String ruleId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String severity = "UNKNOWN";

    @Column(name = "affected_package")
    private String affectedPackage;

    @Column(name = "installed_version")
    private String installedVersion;

    @Column(name = "fixed_version")
    private String fixedVersion;

    @Column(name = "affected_location")
    private String affectedLocation;

    @Column(name = "remediation_url")
    private String remediationUrl;

    @Column(nullable = false)
    private String status = "OPEN";

    @Column(name = "first_seen", nullable = false)
    private Instant firstSeen;

    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public SecurityFinding() { }

    // ── Getters / Setters ─────────────────────────────────────

    public UUID getId() { return id; }
    public InventoryApplication getApplication() { return application; }
    public void setApplication(InventoryApplication app) { this.application = app; }
    public PipelineRun getPipelineRun() { return pipelineRun; }
    public void setPipelineRun(PipelineRun pipelineRun) { this.pipelineRun = pipelineRun; }
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }
    public String getScanner() { return scanner; }
    public void setScanner(String scanner) { this.scanner = scanner; }
    public String getVulnerabilityId() { return vulnerabilityId; }
    public void setVulnerabilityId(String vulnerabilityId) { this.vulnerabilityId = vulnerabilityId; }
    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getAffectedPackage() { return affectedPackage; }
    public void setAffectedPackage(String affectedPackage) { this.affectedPackage = affectedPackage; }
    public String getInstalledVersion() { return installedVersion; }
    public void setInstalledVersion(String installedVersion) { this.installedVersion = installedVersion; }
    public String getFixedVersion() { return fixedVersion; }
    public void setFixedVersion(String fixedVersion) { this.fixedVersion = fixedVersion; }
    public String getAffectedLocation() { return affectedLocation; }
    public void setAffectedLocation(String affectedLocation) { this.affectedLocation = affectedLocation; }
    public String getRemediationUrl() { return remediationUrl; }
    public void setRemediationUrl(String remediationUrl) { this.remediationUrl = remediationUrl; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getFirstSeen() { return firstSeen; }
    public void setFirstSeen(Instant firstSeen) { this.firstSeen = firstSeen; }
    public Instant getLastSeen() { return lastSeen; }
    public void setLastSeen(Instant lastSeen) { this.lastSeen = lastSeen; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
