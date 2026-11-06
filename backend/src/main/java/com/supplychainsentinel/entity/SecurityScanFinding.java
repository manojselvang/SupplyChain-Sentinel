package com.supplychainsentinel.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the <strong>security_scan_findings</strong> junction table (V6).
 *
 * <p>Records which findings were detected in which scan.  A finding that
 * appears in multiple scans will have multiple junction rows — one per
 * scan — preserving full historical traceability.</p>
 */
@Entity
@Table(name = "security_scan_findings",
       uniqueConstraints = @UniqueConstraint(name = "uq_scan_finding", columnNames = {"scan_id", "finding_id"}))
public class SecurityScanFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "scan_id", nullable = false)
    private UUID scanId;

    @Column(name = "finding_id", nullable = false)
    private UUID findingId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public SecurityScanFinding() { }

    public UUID getId() { return id; }
    public UUID getScanId() { return scanId; }
    public void setScanId(UUID scanId) { this.scanId = scanId; }
    public UUID getFindingId() { return findingId; }
    public void setFindingId(UUID findingId) { this.findingId = findingId; }
    public Instant getCreatedAt() { return createdAt; }
}
