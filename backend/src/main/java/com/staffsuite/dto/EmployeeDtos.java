package com.staffsuite.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.staffsuite.dto.DocumentDtos.DocumentResponse;
import com.staffsuite.dto.LeaveDtos.LeaveBalanceResponse;
import com.staffsuite.entity.AttendanceStatus;
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
            /** API path the photo is served from, or null when the employee has no photo. */
            String profilePhotoUrl,
            Instant createdAt,
            Instant updatedAt) {
    }

    /** Basic directory info visible to every authenticated user. */
    public record DirectoryEntry(Long id, String fullName, String jobTitle, String departmentName) {
    }

    /** One rejected row of a bulk upload; {@code row} is the spreadsheet row number (row 1 is the header). */
    public record BulkUploadRowError(int row, String email, String fullName, String reason) {
    }

    public record BulkUploadResult(int totalRows, int created, int failed, List<BulkUploadRowError> errors) {
    }

    public record ProfileCompletionItem(String key, String label, boolean done) {
    }

    /** What the employee can still fill in. {@code percent} is 0-100. */
    public record ProfileCompletion(int percent, boolean complete, List<ProfileCompletionItem> items) {
    }

    public record ProfileLeaveSummary(List<LeaveBalanceResponse> balances, long pendingRequests) {
    }

    /** Counts are days of the current month up to today; {@code todayStatus} is null when it cannot be derived. */
    public record ProfileAttendanceSummary(String month, int present, int absent, int late, AttendanceStatus todayStatus) {
    }

    /**
     * Everything the profile page shows. {@code accessLevel} is FULL for HR/Admin, the employee themself and their
     * direct manager. Anyone else gets DIRECTORY, which carries only what the directory already shows (name, job title,
     * department, photo); every other field is then null.
     */
    public record EmployeeProfile(
            Long id,
            String accessLevel,
            String fullName,
            String jobTitle,
            String departmentName,
            String profilePhotoUrl,
            String employeeCode,
            String email,
            String phone,
            LocalDate dateOfBirth,
            LocalDate joiningDate,
            EmploymentStatus employmentStatus,
            String designationName,
            Long managerId,
            String managerName,
            ProfileLeaveSummary leave,
            ProfileAttendanceSummary attendance,
            List<DocumentResponse> documents,
            List<DirectoryEntry> directReports,
            ProfileCompletion completion) {
    }
}
