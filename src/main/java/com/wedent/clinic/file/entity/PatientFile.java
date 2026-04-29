package com.wedent.clinic.file.entity;

import com.wedent.clinic.clinic.entity.Clinic;
import com.wedent.clinic.common.entity.BaseEntity;
import com.wedent.clinic.company.entity.Company;
import com.wedent.clinic.patient.entity.Patient;
import com.wedent.clinic.user.entity.User;
import jakarta.persistence.Basic;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

/**
 * A binary attachment (X-ray, consent form, …) scoped to a patient.
 *
 * <p>The binary {@link #content} field is excluded from list queries via
 * explicit JPQL constructor expressions in {@link com.wedent.clinic.file.repository.PatientFileRepository}
 * — only the download endpoint fetches the bytes.</p>
 *
 * <p>Soft-delete via {@code active = false}; {@code @SQLRestriction} hides
 * inactive rows from every JPA query automatically.</p>
 */
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamicUpdate
@Table(name = "patient_files")
@SQLDelete(sql = "UPDATE patient_files SET active = false, updated_at = NOW() WHERE id = ?")
@SQLRestriction("active = true")
public class PatientFile extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinic_id", nullable = false)
    private Clinic clinic;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    /** User who uploaded the file. May be null if the account was deleted. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private PatientFileCategory category;

    /** The name shown to the user (sanitised on upload). */
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "description", length = 500)
    private String description;

    /**
     * Raw file bytes stored in PostgreSQL {@code bytea}.
     *
     * <p>List queries use JPQL constructor expressions that never reference
     * this field, so the bytes are only loaded when the download endpoint is hit.</p>
     */
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "content", nullable = false, columnDefinition = "bytea")
    private byte[] content;
}
