package com.staffsuite.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.staffsuite.dto.ReportDtos.AttendanceTrendReport;
import com.staffsuite.dto.ReportDtos.Granularity;
import com.staffsuite.dto.ReportDtos.HeadcountReport;
import com.staffsuite.dto.ReportDtos.LeaveUtilizationReport;
import com.staffsuite.dto.ReportDtos.OnboardingReport;
import com.staffsuite.exception.ApiException;
import com.staffsuite.service.ReportCsvExporter;
import com.staffsuite.service.ReportService;

import lombok.RequiredArgsConstructor;

/** HR analytics. HR and Admin only: enforced here and again at URL level in the security configuration. */
@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN')")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final ReportCsvExporter exporter;

    /** A snapshot of today's workforce. Date parameters do not apply; the department filter does. */
    @GetMapping("/headcount")
    public HeadcountReport headcount(@RequestParam(required = false) Long departmentId) {
        return reportService.headcount(departmentId);
    }

    @GetMapping("/attendance-trend")
    public AttendanceTrendReport attendanceTrend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Granularity granularity) {
        return reportService.attendanceTrend(from, to, departmentId, granularity);
    }

    @GetMapping("/leave-utilization")
    public LeaveUtilizationReport leaveUtilization(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long departmentId) {
        return reportService.leaveUtilization(from, to, departmentId);
    }

    @GetMapping("/onboarding-stats")
    public OnboardingReport onboardingStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long departmentId) {
        return reportService.onboarding(from, to, departmentId);
    }

    /**
     * The same report as a CSV file, for the same filters. {@code reportType} is one of: headcount,
     * attendance-trend, leave-utilization, onboarding-stats.
     */
    @GetMapping("/{reportType}/export")
    public ResponseEntity<byte[]> export(@PathVariable String reportType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Granularity granularity) {
        byte[] csv;
        LocalDate fileFrom;
        LocalDate fileTo;
        switch (reportType) {
            case "headcount" -> {
                csv = exporter.headcount(reportService.headcount(departmentId));
                fileFrom = null;
                fileTo = null;
            }
            case "attendance-trend" -> {
                AttendanceTrendReport r = reportService.attendanceTrend(from, to, departmentId, granularity);
                csv = exporter.attendanceTrend(r);
                fileFrom = r.from();
                fileTo = r.to();
            }
            case "leave-utilization" -> {
                LeaveUtilizationReport r = reportService.leaveUtilization(from, to, departmentId);
                csv = exporter.leaveUtilization(r);
                fileFrom = r.from();
                fileTo = r.to();
            }
            case "onboarding-stats" -> {
                OnboardingReport r = reportService.onboarding(from, to, departmentId);
                csv = exporter.onboarding(r);
                fileFrom = r.from();
                fileTo = r.to();
            }
            default -> throw new ApiException(HttpStatus.NOT_FOUND, "Unknown report type '" + reportType + "'");
        }
        String fileName = ReportCsvExporter.fileName(reportType, fileFrom, fileTo, reportService.today());
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Cache-Control", "no-store")
                .body(csv);
    }
}
