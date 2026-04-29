package com.wedent.clinic.common.audit;

import com.wedent.clinic.common.audit.event.AuditEvent;
import com.wedent.clinic.common.web.WedentRequestContextFilter;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Thin indirection over Spring's {@link ApplicationEventPublisher} that:
 *
 * <ul>
 *   <li>Auto-enriches each event with the current request's {@code traceId}
 *       from MDC so audit rows correlate 1:1 with log lines.</li>
 *   <li>Never throws — publishing an audit event must not break the flow
 *       that triggered it. Any failure here is swallowed and logged by the
 *       async listener side; the sync side (this class) is best-effort.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AuditEventPublisher {

    private final ApplicationEventPublisher delegate;

    public void publish(AuditEvent event) {
        AuditEvent enriched = withTraceIdFromMdc(event);
        delegate.publishEvent(enriched);
    }

    private static AuditEvent withTraceIdFromMdc(AuditEvent event) {
        if (event.traceId() != null && !event.traceId().isBlank()) {
            return event;
        }
        String traceId = MDC.get(WedentRequestContextFilter.MDC_TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            return event;
        }
        return new AuditEvent(
                event.type(),
                event.actorUserId(),
                event.actorEmail(),
                event.companyId(),
                event.clinicId(),
                event.patientId(),
                event.targetType(),
                event.targetId(),
                event.detail(),
                event.ipAddress(),
                traceId,
                event.occurredAt()
        );
    }
}
