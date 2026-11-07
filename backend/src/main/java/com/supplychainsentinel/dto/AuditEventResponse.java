package com.supplychainsentinel.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(
    UUID id,
    UUID actorId,
    String actorRole,
    String action,
    String entityType,
    UUID entityId,
    String eventDetails,
    Instant occurredAt
) {}
