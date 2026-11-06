package com.supplychainsentinel.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the <strong>artifact_verifications</strong> table (V7).
 * Stores CI-reported Cosign verification results.
 */
@Entity
@Table(name = "artifact_verifications")
public class ArtifactVerification {

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

    @Column(name = "artifact_reference", nullable = false)
    private String artifactReference;

    @Column(name = "artifact_digest")
    private String artifactDigest;

    @Column(name = "verification_tool", nullable = false)
    private String verificationTool = "COSIGN";

    @Column(name = "verification_mode", nullable = false)
    private String verificationMode;

    @Column(name = "verification_status", nullable = false)
    private String verificationStatus;

    @Column(name = "signer_identity")
    private String signerIdentity;

    @Column(name = "oidc_issuer")
    private String oidcIssuer;

    @Column(name = "public_key_fingerprint")
    private String publicKeyFingerprint;

    @Column(name = "cosign_version")
    private String cosignVersion;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "reported_at", nullable = false)
    private Instant reportedAt;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "verification_evidence", columnDefinition = "jsonb")
    private String verificationEvidence;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public ArtifactVerification() { }

    public UUID getId() { return id; }
    public InventoryApplication getApplication() { return application; }
    public void setApplication(InventoryApplication application) { this.application = application; }
    public PipelineRun getPipelineRun() { return pipelineRun; }
    public void setPipelineRun(PipelineRun pipelineRun) { this.pipelineRun = pipelineRun; }
    public String getArtifactReference() { return artifactReference; }
    public void setArtifactReference(String artifactReference) { this.artifactReference = artifactReference; }
    public String getArtifactDigest() { return artifactDigest; }
    public void setArtifactDigest(String artifactDigest) { this.artifactDigest = artifactDigest; }
    public String getVerificationTool() { return verificationTool; }
    public void setVerificationTool(String verificationTool) { this.verificationTool = verificationTool; }
    public String getVerificationMode() { return verificationMode; }
    public void setVerificationMode(String verificationMode) { this.verificationMode = verificationMode; }
    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }
    public String getSignerIdentity() { return signerIdentity; }
    public void setSignerIdentity(String signerIdentity) { this.signerIdentity = signerIdentity; }
    public String getOidcIssuer() { return oidcIssuer; }
    public void setOidcIssuer(String oidcIssuer) { this.oidcIssuer = oidcIssuer; }
    public String getPublicKeyFingerprint() { return publicKeyFingerprint; }
    public void setPublicKeyFingerprint(String publicKeyFingerprint) { this.publicKeyFingerprint = publicKeyFingerprint; }
    public String getCosignVersion() { return cosignVersion; }
    public void setCosignVersion(String cosignVersion) { this.cosignVersion = cosignVersion; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }
    public Instant getReportedAt() { return reportedAt; }
    public void setReportedAt(Instant reportedAt) { this.reportedAt = reportedAt; }
    public String getVerificationEvidence() { return verificationEvidence; }
    public void setVerificationEvidence(String verificationEvidence) { this.verificationEvidence = verificationEvidence; }
    public Instant getCreatedAt() { return createdAt; }
}
