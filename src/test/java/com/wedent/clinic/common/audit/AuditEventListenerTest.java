package com.wedent.clinic.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wedent.clinic.common.audit.entity.AuditLog;
import com.wedent.clinic.common.audit.event.AuditEvent;
import com.wedent.clinic.common.audit.event.AuditEventType;
import com.wedent.clinic.common.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the audit writer.
 *
 * <p>We invoke {@link AuditEventListener#onAuditEvent(AuditEvent)} directly
 * rather than going through Spring's event machinery — the point of these
 * tests is to pin the row-building and serialization behavior, not Spring's
 * own {@code @TransactionalEventListener}/{@code @Async} plumbing which is
 * already covered by the framework's own test suite.</p>
 */
class AuditEventListenerTest {

    private AuditLogRepository repository;
    private ObjectMapper objectMapper;
    private AuditEventListener listener;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(AuditLogRepository.class);
        objectMapper = new ObjectMapper();
        listener = new AuditEventListener(repository, objectMapper);
    }

    @Test
    void persistsEventWithAllFieldsAndSerializedJsonDetail() {
        AuditEvent event = AuditEvent.builder(AuditEventType.LOGIN_SUCCESS)
                .actorUserId(42L)
                .actorEmail("dr.smith@example.com")
                .companyId(100L)
                .clinicId(200L)
                .ipAddress("203.0.113.7")
                .traceId("trace-abc")
                .detail(Map.of("mfa", "NONE"))
                .build();

        listener.onAuditEvent(event);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        AuditLog saved = captor.getValue();

        assertThat(saved.getEventType()).isEqualTo("LOGIN_SUCCESS");
        assertThat(saved.getActorUserId()).isEqualTo(42L);
        assertThat(saved.getActorEmail()).isEqualTo("dr.smith@example.com");
        assertThat(saved.getCompanyId()).isEqualTo(100L);
        assertThat(saved.getClinicId()).isEqualTo(200L);
        assertThat(saved.getIpAddress()).isEqualTo("203.0.113.7");
        assertThat(saved.getTraceId()).isEqualTo("trace-abc");
        assertThat(saved.getOccurredAt()).isNotNull();
        // Detail is serialized JSON — we don't care about key order here.
        assertThat(saved.getDetail()).contains("\"mfa\":\"NONE\"");
    }

    @Test
    void nullDetailIsPassedThroughAsNull() {
        AuditEvent event = AuditEvent.builder(AuditEventType.LOGIN_FAILURE)
                .actorEmail("ghost@example.com")
                .ipAddress("203.0.113.7")
                .build();

        listener.onAuditEvent(event);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getDetail()).isNull();
    }

    @Test
    void repositoryFailureIsSwallowed() {
        // Audit writes must never propagate back and break the business flow.
        when(repository.save(any(AuditLog.class)))
                .thenThrow(new RuntimeException("DB down"));

        AuditEvent event = AuditEvent.builder(AuditEventType.APPOINTMENT_CREATED)
                .actorUserId(1L)
                .targetType("Appointment")
                .targetId(99L)
                .build();

        // No throw expected.
        listener.onAuditEvent(event);

        // Exception was caught inside the listener — no propagation.
        verify(repository).save(any(AuditLog.class));
    }

    @Test
    void emptyDetailMapIsTreatedAsNull() {
        AuditEvent event = AuditEvent.builder(AuditEventType.APPOINTMENT_DELETED)
                .targetType("Appointment")
                .targetId(7L)
                .detail(Map.of())
                .build();

        listener.onAuditEvent(event);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getDetail()).isNull();
    }

    @Test
    void sparselyPopulatedEventStillPersists() {
        // Guards against a future refactor where we accidentally start
        // rejecting events with null fields. An audit row with only
        // {eventType, occurredAt} is still informative.
        AuditEvent event = AuditEvent.builder(AuditEventType.USER_DISABLED).build();

        listener.onAuditEvent(event);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo("USER_DISABLED");
        assertThat(saved.getActorUserId()).isNull();
        assertThat(saved.getCompanyId()).isNull();
        assertThat(saved.getDetail()).isNull();
        assertThat(saved.getOccurredAt()).isNotNull();
    }
}
