package com.staffsuite.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.staffsuite.entity.DocumentVisibility;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class DocumentDtos {

    private DocumentDtos() {
    }

    public record CategoryRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 500) String description,
            Boolean active) {
    }

    public record CategoryResponse(Long id, String name, String description, boolean active) {
    }

    /**
     * Deliberately has no storage reference: clients download through the access-checked
     * download endpoint, never by path.
     */
    public record DocumentResponse(
            Long id,
            Long employeeId,
            String employeeCode,
            String employeeName,
            String departmentName,
            Long categoryId,
            String categoryName,
            String fileName,
            String contentType,
            Long fileSize,
            LocalDate expiryDate,
            Integer daysRemaining,
            boolean expired,
            DocumentVisibility visibility,
            String uploadedByName,
            Instant createdAt) {
    }
}
