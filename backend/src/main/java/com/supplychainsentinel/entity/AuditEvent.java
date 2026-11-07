package com.supplychainsentinel.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
public class AuditEvent {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_role")
    private String actorRole;

    @Column(nullable = false)
    private String action;

    @Column(name = "entity_type")
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "event_details", columnDefinition = "jsonb")
    private String eventDetails;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public AuditEvent() {}

    public UUID getId() { return id; }
    public UUID getActorId() { return actorId; }
    public void setActorId(UUID a) { this.actorId = a; }
    public String getActorRole() { return actorRole; }
    public void setActorRole(String r) { this.actorRole = r; }
    public String getAction() { return action; }
    public void setAction(String a) { this.action = a; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String e) { this.entityType = e; }
    public UUID getEntityId() { return entityId; }
    public void setEntityId(UUID e) { this.entityId = e; }
    public String getEventDetails() { return eventDetails; }
    public void setEventDetails(String d) { this.eventDetails = d; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant o) { this.occurredAt = o; }
    public Instant getCreatedAt() { return createdAt; }
}
