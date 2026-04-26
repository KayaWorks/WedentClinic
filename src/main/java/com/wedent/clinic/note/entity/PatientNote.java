package com.wedent.clinic.note.entity;

import com.wedent.clinic.clinic.entity.Clinic;
import com.wedent.clinic.common.entity.BaseEntity;
import com.wedent.clinic.company.entity.Company;
import com.wedent.clinic.patient.entity.Patient;
import com.wedent.clinic.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * A clinical note attached to a patient. Notes are immutable once created
 * except for the title/content fields — soft-delete via active=false.
 *
 * <p>{@link #pinned} allows the UI to surface critical notes (e.g. allergies)
 * at the top of the list independent of creation order.</p>
 */
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamicUpdate
@Table(name = "patient_notes")
@SQLDelete(sql = "UPDATE patient_notes SET active = false, updated_at = NOW() WHERE id = ?")
@SQLRestriction("active = true")
public class PatientNote extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinic_id", nullable = false)
    private Clinic clinic;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    /** User who created this note. Never re-assigned after creation. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_user_id", nullable = false, updatable = false)
    private User author;

    @Enumerated(EnumType.STRING)
    @Column(name = "note_type", nullable = false, length = 30)
    private NoteType noteType;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** When true the UI surfaces this note at the top regardless of creation order. */
    @Column(name = "pinned", nullable = false)
    private boolean pinned;
}
