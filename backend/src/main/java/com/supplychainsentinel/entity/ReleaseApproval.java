package com.supplychainsentinel.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "release_approvals")
public class ReleaseApproval {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "assessment_id", nullable = false)
    private UUID assessmentId;

    @Column(name = "approver_id")
    private UUID approverId;

    @Column(nullable = false)
    private String decision;

    @Column(columnDefinition = "TEXT")
    private String justification;

    @Column(name = "approved_at", nullable = false)
    private Instant approvedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public ReleaseApproval() {}

    public UUID getId() { return id; }
    public UUID getAssessmentId() { return assessmentId; }
    public void setAssessmentId(UUID a) { this.assessmentId = a; }
    public UUID getApproverId() { return approverId; }
    public void setApproverId(UUID a) { this.approverId = a; }
    public String getDecision() { return decision; }
    public void setDecision(String d) { this.decision = d; }
    public String getJustification() { return justification; }
    public void setJustification(String j) { this.justification = j; }
    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant a) { this.approvedAt = a; }
    public Instant getCreatedAt() { return createdAt; }
}
