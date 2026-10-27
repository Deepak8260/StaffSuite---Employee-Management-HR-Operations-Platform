package com.staffsuite.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.staffsuite.entity.EmploymentStatus;
import com.staffsuite.entity.Role;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

public final class EmployeeDtos {

    private EmployeeDtos() {
    }

    public record UserAccountRequest(
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotNull Role role) {
    }

    /** Used for both create and full update. {@code userAccount} is only valid on create. */
    public record EmployeeRequest(
            @NotBlank @Size(max = 160) String fullName,
            @NotBlank @Email @Size(max = 150) String email,
            @Size(max = 25) String phone,
            @Size(max = 500) String address,
            @Size(max = 150) String emergencyContact,
            LocalDate dateOfBirth,
            @NotNull LocalDate joiningDate,
            @NotBlank @Size(max = 100) String jobTitle,
            @NotNull EmploymentStatus employmentStatus,
            @NotNull Long departmentId,
            Long designationId,
            Long managerId,
            @Valid UserAccountRequest userAccount) {
    }

    /**
     * Self-service edit. Only the three fields below are editable; anything else in the body
     * is captured so the service can reject the request instead of silently ignoring it.
     */
    @Getter
    @Setter
    public static class SelfProfileRequest {

        @Size(max = 25)
        @Pattern(regexp = "^[0-9+()\\- ]*$", message = "may only contain digits, spaces and + ( ) -")
        private String phone;

        @Size(max = 500)
        private String address;

        @Size(max = 150)
        private String emergencyContact;

        @JsonIgnore
        private final Map<String, Object> unknownFields = new LinkedHashMap<>();

        @JsonAnySetter
        public void captureUnknown(String name, Object value) {
            unknownFields.put(name, value);
        }

        @JsonIgnore
        public Set<String> rejectedFields() {
            return unknownFields.keySet();
        }
    }

    public record EmployeeResponse(
            Long id,
            String employeeCode,
            String fullName,
            String email,
            String phone,
            String address,
            String emergencyContact,
            LocalDate dateOfBirth,
            LocalDate joiningDate,
            String jobTitle,
            EmploymentStatus employmentStatus,
            Long departmentId,
            String departmentName,
            Long designationId,
            String designationName,
            Long managerId,
            String managerName,
            boolean deleted,
            Instant createdAt,
            Instant updatedAt) {
    }

    /** Basic directory info visible to every authenticated user. */
    public record DirectoryEntry(Long id, String fullName, String jobTitle, String departmentName) {
    }
}
