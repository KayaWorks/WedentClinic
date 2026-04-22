package com.wedent.clinic.common.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wedent.clinic.common.audit.entity.AuditLog;
import com.wedent.clinic.common.audit.event.AuditEvent;
import com.wedent.clinic.common.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * Async, after-commit writer for {@link AuditEvent}s.
 *
 * <h3>Why {@code @TransactionalEventListener(AFTER_COMMIT)} plus {@code @Async}?</h3>
 * <ul>
 *   <li><b>AFTER_COMMIT</b> — we only persist audit rows for actions that
 *       actually took effect. If the source transaction rolls back (e.g. an
 *       appointment create fails after we publish), we don't want a phantom
 *       audit row claiming it happened. {@code fallbackExecution=true} lets
 *       non-transactional callers (login, rate-limit blocks) still be audited.</li>
 *   <li><b>@Async</b> — audit writes must never block the user-facing
 *       request. They run on {@code auditExecutor} (see {@code AsyncConfig}).</li>
 *   <li><b>REQUIRES_NEW</b> — the listener starts its own transaction; it's
 *       on a different thread from the source so there's nothing to join
 *       anyway, but being explicit protects against future reshuffles.</li>
 * </ul>
 *
 * <h3>Failure handling</h3>
 * If persistence fails we log at WARN and swallow the exception. An audit
 * write failing must not propagate back into the business flow (which has
 * already committed). For higher-stakes deployments, this is where you'd
 * plug in a fallback sink (e.g. Kafka topic, S3 append, syslog).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditEventListener {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Async("auditExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAuditEvent(AuditEvent event) {
        try {
            AuditLog row = AuditLog.builder()
                    .eventType(event.type().name())
                    .actorUserId(event.actorUserId())
                    .actorEmail(event.actorEmail())
                    .companyId(event.companyId())
                    .clinicId(event.clinicId())
                    .targetType(event.targetType())
                    .targetId(event.targetId())
                    .detail(serializeDetail(event.detail()))
                    .ipAddress(event.ipAddress())
                    .traceId(event.traceId())
                    .occurredAt(event.occurredAt())
                    .build();
            auditLogRepository.save(row);
        } catch (RuntimeException ex) {
            // Never let audit failure kill the request. Operators will
            // see these in logs; paired with the traceId in the log pattern
            // we can still reconstruct the event offline.
            log.warn("Failed to persist audit event {} traceId={}: {}",
                    event.type(), event.traceId(), ex.getMessage(), ex);
        }
    }

    private String serializeDetail(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize audit detail: {}", e.getMessage());
            return null;
        }
    }
}
