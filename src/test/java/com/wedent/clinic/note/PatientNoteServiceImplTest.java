package com.wedent.clinic.note;

import com.wedent.clinic.clinic.entity.Clinic;
import com.wedent.clinic.common.audit.AuditEventPublisher;
import com.wedent.clinic.common.audit.event.AuditEvent;
import com.wedent.clinic.common.audit.event.AuditEventType;
import com.wedent.clinic.common.exception.ResourceNotFoundException;
import com.wedent.clinic.company.entity.Company;
import com.wedent.clinic.note.dto.PatientNoteCreateRequest;
import com.wedent.clinic.note.dto.PatientNoteResponse;
import com.wedent.clinic.note.dto.PatientNoteUpdateRequest;
import com.wedent.clinic.note.entity.NoteType;
import com.wedent.clinic.note.entity.PatientNote;
import com.wedent.clinic.note.mapper.PatientNoteMapper;
import com.wedent.clinic.note.repository.PatientNoteRepository;
import com.wedent.clinic.note.service.impl.PatientNoteServiceImpl;
import com.wedent.clinic.patient.entity.Patient;
import com.wedent.clinic.patient.repository.PatientRepository;
import com.wedent.clinic.security.AuthenticatedUser;
import com.wedent.clinic.user.entity.User;
import com.wedent.clinic.user.entity.UserStatus;
import com.wedent.clinic.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PatientNoteServiceImplTest {

    private static final Long COMPANY_ID = 100L;
    private static final Long CLINIC_ID  = 10L;
    private static final Long PATIENT_ID = 200L;
    private static final Long USER_ID    = 7L;
    private static final Long NOTE_ID    = 300L;

    private final PatientNoteRepository noteRepository  = Mockito.mock(PatientNoteRepository.class);
    private final PatientRepository     patientRepository = Mockito.mock(PatientRepository.class);
    private final UserRepository        userRepository  = Mockito.mock(UserRepository.class);
    private final PatientNoteMapper     mapper          = Mockito.mock(PatientNoteMapper.class);
    private final AuditEventPublisher   auditPublisher  = Mockito.mock(AuditEventPublisher.class);

    private final PatientNoteServiceImpl service = new PatientNoteServiceImpl(
            noteRepository, patientRepository, userRepository, mapper, auditPublisher);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    // ─── create ─────────────────────────────────────────────────────────────

    @Test
    void create_happyPath_wiresRelations_appliesDefaults_andAudits() {
        authenticate(USER_ID, COMPANY_ID, CLINIC_ID, "DOCTOR");
        Patient patient = patient();
        User author = user();
        when(patientRepository.findByIdAndCompanyId(PATIENT_ID, COMPANY_ID)).thenReturn(Optional.of(patient));
        when(userRepository.findByIdAndCompanyId(USER_ID, COMPANY_ID)).thenReturn(Optional.of(author));

        PatientNote partial = new PatientNote();
        partial.setContent("Hasta penisilin alerjisi var.");
        when(mapper.toEntity(any(PatientNoteCreateRequest.class))).thenReturn(partial);
        when(noteRepository.save(any(PatientNote.class))).thenAnswer(inv -> {
            PatientNote n = inv.getArgument(0);
            n.setId(NOTE_ID);
            return n;
        });
        when(mapper.toResponse(any(PatientNote.class))).thenReturn(stubResponse(NOTE_ID));

        PatientNoteResponse response = service.create(PATIENT_ID,
                new PatientNoteCreateRequest(NoteType.ALLERGY, "Alerji", "Penisilin alerjisi", true));

        assertThat(response.id()).isEqualTo(NOTE_ID);

        ArgumentCaptor<PatientNote> savedCaptor = ArgumentCaptor.forClass(PatientNote.class);
        verify(noteRepository).save(savedCaptor.capture());
        PatientNote saved = savedCaptor.getValue();
        assertThat(saved.getPatient()).isSameAs(patient);
        assertThat(saved.getAuthor()).isSameAs(author);
        assertThat(saved.getCompany()).isSameAs(patient.getCompany());
        assertThat(saved.getClinic()).isSameAs(patient.getClinic());

        ArgumentCaptor<AuditEvent> evCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher).publish(evCaptor.capture());
        assertThat(evCaptor.getValue().type()).isEqualTo(AuditEventType.PATIENT_NOTE_CREATED);
        assertThat(evCaptor.getValue().detail()).containsEntry("patientId", PATIENT_ID);
    }

    @Test
    void create_defaultNoteType_isGeneral() {
        authenticate(USER_ID, COMPANY_ID, CLINIC_ID, "DOCTOR");
        when(patientRepository.findByIdAndCompanyId(PATIENT_ID, COMPANY_ID)).thenReturn(Optional.of(patient()));
        when(userRepository.findByIdAndCompanyId(USER_ID, COMPANY_ID)).thenReturn(Optional.of(user()));

        PatientNote partial = new PatientNote(); // noteType is null from mapper
        partial.setContent("Genel not");
        when(mapper.toEntity(any(PatientNoteCreateRequest.class))).thenReturn(partial);
        when(noteRepository.save(any(PatientNote.class))).thenAnswer(inv -> {
            PatientNote n = inv.getArgument(0);
            n.setId(NOTE_ID);
            return n;
        });
        when(mapper.toResponse(any(PatientNote.class))).thenReturn(stubResponse(NOTE_ID));

        service.create(PATIENT_ID, new PatientNoteCreateRequest(null, null, "Genel not", null));

        ArgumentCaptor<PatientNote> captor = ArgumentCaptor.forClass(PatientNote.class);
        verify(noteRepository).save(captor.capture());
        assertThat(captor.getValue().getNoteType()).isEqualTo(NoteType.GENERAL);
    }

    @Test
    void create_patientNotFound_throwsResourceNotFound() {
        authenticate(USER_ID, COMPANY_ID, CLINIC_ID, "DOCTOR");
        when(patientRepository.findByIdAndCompanyId(PATIENT_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(PATIENT_ID,
                new PatientNoteCreateRequest(null, null, "content", null)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(noteRepository, never()).save(any());
        verify(auditPublisher, never()).publish(any());
    }

    // ─── update ─────────────────────────────────────────────────────────────

    @Test
    void update_happyPath_delegatesToMapperAndAudits() {
        authenticate(USER_ID, COMPANY_ID, CLINIC_ID, "DOCTOR");
        PatientNote existing = existingNote();
        when(noteRepository.findByIdAndCompanyId(NOTE_ID, COMPANY_ID)).thenReturn(Optional.of(existing));
        when(mapper.toResponse(any(PatientNote.class))).thenReturn(stubResponse(NOTE_ID));

        service.update(NOTE_ID, new PatientNoteUpdateRequest(null, "Updated title", null, null));

        verify(mapper).updateEntity(any(PatientNoteUpdateRequest.class), any(PatientNote.class));
        ArgumentCaptor<AuditEvent> evCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher).publish(evCaptor.capture());
        assertThat(evCaptor.getValue().type()).isEqualTo(AuditEventType.PATIENT_NOTE_UPDATED);
    }

    @Test
    void update_noteNotFound_throwsResourceNotFound() {
        authenticate(USER_ID, COMPANY_ID, CLINIC_ID, "DOCTOR");
        when(noteRepository.findByIdAndCompanyId(NOTE_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(NOTE_ID,
                new PatientNoteUpdateRequest(null, null, "content", null)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(auditPublisher, never()).publish(any());
    }

    // ─── delete ─────────────────────────────────────────────────────────────

    @Test
    void delete_happyPath_softDeletesAndAudits() {
        authenticate(USER_ID, COMPANY_ID, CLINIC_ID, "MANAGER");
        PatientNote existing = existingNote();
        when(noteRepository.findByIdAndCompanyId(NOTE_ID, COMPANY_ID)).thenReturn(Optional.of(existing));

        service.delete(NOTE_ID);

        verify(noteRepository).delete(existing);
        ArgumentCaptor<AuditEvent> evCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher).publish(evCaptor.capture());
        assertThat(evCaptor.getValue().type()).isEqualTo(AuditEventType.PATIENT_NOTE_DELETED);
    }

    @Test
    void delete_noteNotFound_throwsResourceNotFound() {
        authenticate(USER_ID, COMPANY_ID, CLINIC_ID, "MANAGER");
        when(noteRepository.findByIdAndCompanyId(NOTE_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(NOTE_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(noteRepository, never()).delete(any(PatientNote.class));
        verify(auditPublisher, never()).publish(any());
    }

    // ─── builders ──────────────────────────────────────────────────────────

    private static Patient patient() {
        Company company = Company.builder().name("Acme").build();
        company.setId(COMPANY_ID);
        Clinic clinic = Clinic.builder().name("Main").company(company).build();
        clinic.setId(CLINIC_ID);
        Patient p = Patient.builder()
                .firstName("Ali").lastName("Veli").phone("+905550000000")
                .company(company).clinic(clinic)
                .build();
        p.setId(PATIENT_ID);
        return p;
    }

    private static User user() {
        Patient p = patient();
        User u = User.builder()
                .email("doctor@acme.com")
                .firstName("Ayşe").lastName("Demir")
                .passwordHash("x")
                .status(UserStatus.ACTIVE)
                .company(p.getCompany())
                .build();
        u.setId(USER_ID);
        return u;
    }

    private static PatientNote existingNote() {
        Patient p = patient();
        User author = user();
        PatientNote n = PatientNote.builder()
                .patient(p).author(author)
                .company(p.getCompany()).clinic(p.getClinic())
                .noteType(NoteType.GENERAL)
                .content("Mevcut not")
                .pinned(false)
                .build();
        n.setId(NOTE_ID);
        return n;
    }

    private static PatientNoteResponse stubResponse(Long id) {
        return new PatientNoteResponse(
                id, PATIENT_ID, CLINIC_ID, COMPANY_ID,
                USER_ID, "Ayşe Demir",
                NoteType.GENERAL, "Başlık", "İçerik",
                false, null, null);
    }

    private static void authenticate(Long userId, Long companyId, Long clinicId, String role) {
        AuthenticatedUser principal = new AuthenticatedUser(
                userId, "user@example.com", companyId, clinicId, Set.of(role), List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }
}
