package com.supplychainsentinel.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the <strong>security_scans</strong> table (V5).
 * Stores one record per scanner report submission.
 */
@Entity
@Table(name = "security_scans")
public class SecurityScan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private InventoryApplication application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pipeline_run_id")
    private PipelineRun pipelineRun;

    @Column(nullable = false)
    private String scanner;

    @Column(name = "report_format", nullable = false)
    private String reportFormat;

    @Column(name = "artifact_digest")
    private String artifactDigest;

    @Column(name = "scan_timestamp", nullable = false)
    private Instant scanTimestamp;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public SecurityScan() { }

    public UUID getId() { return id; }
    public InventoryApplication getApplication() { return application; }
    public void setApplication(InventoryApplication application) { this.application = application; }
    public PipelineRun getPipelineRun() { return pipelineRun; }
    public void setPipelineRun(PipelineRun pipelineRun) { this.pipelineRun = pipelineRun; }
    public String getScanner() { return scanner; }
    public void setScanner(String scanner) { this.scanner = scanner; }
    public String getReportFormat() { return reportFormat; }
    public void setReportFormat(String reportFormat) { this.reportFormat = reportFormat; }
    public String getArtifactDigest() { return artifactDigest; }
    public void setArtifactDigest(String artifactDigest) { this.artifactDigest = artifactDigest; }
    public Instant getScanTimestamp() { return scanTimestamp; }
    public void setScanTimestamp(Instant scanTimestamp) { this.scanTimestamp = scanTimestamp; }
    public Instant getCreatedAt() { return createdAt; }
}
