package com.supplychainsentinel.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the <strong>sbom_documents</strong> table (V7).
 * Stores one record per SBOM document submission (CycloneDX JSON).
 */
@Entity
@Table(name = "sbom_documents")
public class SbomDocument {

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

    @Column(name = "artifact_digest")
    private String artifactDigest;

    @Column(nullable = false)
    private String format = "CYCLONEDX_JSON";

    @Column(name = "spec_version")
    private String specVersion;

    @Column(name = "serial_number")
    private String serialNumber;

    @Column(name = "document_version")
    private Integer documentVersion;

    @Column(name = "document_sha256", nullable = false)
    private String documentSha256;

    @Column(name = "component_count", nullable = false)
    private int componentCount;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "raw_document", columnDefinition = "jsonb")
    private String rawDocument;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public SbomDocument() { }

    public UUID getId() { return id; }
    public InventoryApplication getApplication() { return application; }
    public void setApplication(InventoryApplication application) { this.application = application; }
    public PipelineRun getPipelineRun() { return pipelineRun; }
    public void setPipelineRun(PipelineRun pipelineRun) { this.pipelineRun = pipelineRun; }
    public String getArtifactDigest() { return artifactDigest; }
    public void setArtifactDigest(String artifactDigest) { this.artifactDigest = artifactDigest; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public String getSpecVersion() { return specVersion; }
    public void setSpecVersion(String specVersion) { this.specVersion = specVersion; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    public Integer getDocumentVersion() { return documentVersion; }
    public void setDocumentVersion(Integer documentVersion) { this.documentVersion = documentVersion; }
    public String getDocumentSha256() { return documentSha256; }
    public void setDocumentSha256(String documentSha256) { this.documentSha256 = documentSha256; }
    public int getComponentCount() { return componentCount; }
    public void setComponentCount(int componentCount) { this.componentCount = componentCount; }
    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
    public Instant getIngestedAt() { return ingestedAt; }
    public void setIngestedAt(Instant ingestedAt) { this.ingestedAt = ingestedAt; }
    public String getRawDocument() { return rawDocument; }
    public void setRawDocument(String rawDocument) { this.rawDocument = rawDocument; }
    public Instant getCreatedAt() { return createdAt; }
}
