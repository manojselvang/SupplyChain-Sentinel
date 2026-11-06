package com.supplychainsentinel.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the <strong>sbom_components</strong> table (V7).
 * Normalized component extracted from a CycloneDX SBOM document.
 */
@Entity
@Table(name = "sbom_components")
public class SbomComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "sbom_id", nullable = false)
    private UUID sbomId;

    @Column(name = "bom_ref")
    private String bomRef;

    @Column(name = "component_type")
    private String componentType;

    @Column(name = "group_name")
    private String groupName;

    @Column(nullable = false)
    private String name;

    @Column
    private String version;

    @Column
    private String purl;

    @Column
    private String cpe;

    @Column
    private String scope;

    @Column
    private String supplier;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "licenses", columnDefinition = "jsonb")
    private String licenses;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "hashes", columnDefinition = "jsonb")
    private String hashes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public SbomComponent() { }

    public UUID getId() { return id; }
    public UUID getSbomId() { return sbomId; }
    public void setSbomId(UUID sbomId) { this.sbomId = sbomId; }
    public String getBomRef() { return bomRef; }
    public void setBomRef(String bomRef) { this.bomRef = bomRef; }
    public String getComponentType() { return componentType; }
    public void setComponentType(String componentType) { this.componentType = componentType; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getPurl() { return purl; }
    public void setPurl(String purl) { this.purl = purl; }
    public String getCpe() { return cpe; }
    public void setCpe(String cpe) { this.cpe = cpe; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getSupplier() { return supplier; }
    public void setSupplier(String supplier) { this.supplier = supplier; }
    public String getLicenses() { return licenses; }
    public void setLicenses(String licenses) { this.licenses = licenses; }
    public String getHashes() { return hashes; }
    public void setHashes(String hashes) { this.hashes = hashes; }
    public Instant getCreatedAt() { return createdAt; }
}
