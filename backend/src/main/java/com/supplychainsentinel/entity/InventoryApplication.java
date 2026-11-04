package com.supplychainsentinel.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the <strong>applications</strong> table (created in V3).
 */
@Entity
@Table(name = "applications")
public class InventoryApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "application_key", nullable = false, unique = true)
    private String applicationKey;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "business_unit")
    private String businessUnit;

    @Column(name = "business_owner")
    private String businessOwner;

    @Column(name = "technical_owner")
    private String technicalOwner;

    @Column(nullable = false)
    private String criticality = "MEDIUM";

    @Column(name = "lifecycle_status", nullable = false)
    private String lifecycleStatus = "ACTIVE";

    @Column(name = "data_classification", nullable = false)
    private String dataClassification = "INTERNAL";

    @Column(name = "repository_url")
    private String repositoryUrl;

    @Column(name = "primary_language")
    private String primaryLanguage;

    @Column(name = "hosting_environment", nullable = false)
    private String hostingEnvironment = "ON_PREMISES";

    @Column(name = "internet_facing", nullable = false)
    private boolean internetFacing = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    // ── Constructors ──────────────────────────────────────────

    public InventoryApplication() { }

    // ── Getters / Setters ─────────────────────────────────────

    public UUID getId() { return id; }
    public String getApplicationKey() { return applicationKey; }
    public void setApplicationKey(String applicationKey) { this.applicationKey = applicationKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getBusinessUnit() { return businessUnit; }
    public void setBusinessUnit(String businessUnit) { this.businessUnit = businessUnit; }
    public String getBusinessOwner() { return businessOwner; }
    public void setBusinessOwner(String businessOwner) { this.businessOwner = businessOwner; }
    public String getTechnicalOwner() { return technicalOwner; }
    public void setTechnicalOwner(String technicalOwner) { this.technicalOwner = technicalOwner; }
    public String getCriticality() { return criticality; }
    public void setCriticality(String criticality) { this.criticality = criticality; }
    public String getLifecycleStatus() { return lifecycleStatus; }
    public void setLifecycleStatus(String lifecycleStatus) { this.lifecycleStatus = lifecycleStatus; }
    public String getDataClassification() { return dataClassification; }
    public void setDataClassification(String dataClassification) { this.dataClassification = dataClassification; }
    public String getRepositoryUrl() { return repositoryUrl; }
    public void setRepositoryUrl(String repositoryUrl) { this.repositoryUrl = repositoryUrl; }
    public String getPrimaryLanguage() { return primaryLanguage; }
    public void setPrimaryLanguage(String primaryLanguage) { this.primaryLanguage = primaryLanguage; }
    public String getHostingEnvironment() { return hostingEnvironment; }
    public void setHostingEnvironment(String hostingEnvironment) { this.hostingEnvironment = hostingEnvironment; }
    public boolean isInternetFacing() { return internetFacing; }
    public void setInternetFacing(boolean internetFacing) { this.internetFacing = internetFacing; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
