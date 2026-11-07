package com.supplychainsentinel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supplychainsentinel.dto.AuditEventResponse;
import com.supplychainsentinel.entity.AuditEvent;
import com.supplychainsentinel.repository.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Append-only audit trail.  Never records passwords, tokens, or signing keys.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository auditRepo;
    private final ObjectMapper objectMapper;

    public AuditService(AuditEventRepository auditRepo, ObjectMapper objectMapper) {
        this.auditRepo = auditRepo;
        this.objectMapper = objectMapper;
    }

    /** Record an audit event in a new transaction so it is committed even if the caller rolls back. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID actorId, String actorRole, String action,
                       String entityType, UUID entityId, Map<String, Object> details) {
        AuditEvent ev = new AuditEvent();
        ev.setActorId(actorId);
        ev.setActorRole(actorRole);
        ev.setAction(action);
        ev.setEntityType(entityType);
        ev.setEntityId(entityId);
        ev.setOccurredAt(Instant.now());
        try {
            ev.setEventDetails(objectMapper.writeValueAsString(details));
        } catch (Exception e) {
            log.warn("Could not serialise audit details for action {}", action);
        }
        auditRepo.save(ev);
    }

    @Transactional(readOnly = true)
    public List<AuditEventResponse> getForEntity(String entityType, UUID entityId) {
        return auditRepo.findByEntityTypeAndEntityIdOrderByOccurredAtAsc(entityType, entityId)
                .stream()
                .map(ev -> new AuditEventResponse(
                        ev.getId(), ev.getActorId(), ev.getActorRole(),
                        ev.getAction(), ev.getEntityType(), ev.getEntityId(),
                        ev.getEventDetails(), ev.getOccurredAt()))
                .toList();
    }
}
