package com.staffsuite.entity;

import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Metadata only. The file itself lives behind the StorageService; {@code fileUrl} is its reference. */
@Entity
@Table(name = "employee_documents")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private DocumentCategory category;

    /** Original name as uploaded (sanitised); used for display and as the download name. */
    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    /** users.id of whoever uploaded it. */
    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentVisibility visibility = DocumentVisibility.SELF_AND_HR;

    /**
     * When the upload happened, taken from the server clock (never from the client) and never changed
     * afterwards. Stored with millisecond precision.
     */
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Safety net: the service sets this from its clock, but a row can never be saved without it. */
    @PrePersist
    void ensureUploadedAt() {
        if (uploadedAt == null) {
            uploadedAt = Instant.now();
        }
    }
}
