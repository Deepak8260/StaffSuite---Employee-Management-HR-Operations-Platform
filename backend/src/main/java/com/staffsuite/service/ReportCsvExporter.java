package com.staffsuite.service;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.staffsuite.dto.ReportDtos.AttendanceTrendReport;
import com.staffsuite.dto.ReportDtos.HeadcountReport;
import com.staffsuite.dto.ReportDtos.LeaveUtilizationReport;
import com.staffsuite.dto.ReportDtos.OnboardingReport;
import com.staffsuite.util.Csv;

/**
 * Turns a report into CSV. It is given the very same report objects the screen is drawn from, so an
 * export can never disagree with what the dashboard shows for the same filters.
 */
@Component
public class ReportCsvExporter {

    public byte[] headcount(HeadcountReport r) {
        Csv csv = new Csv().row("Section", "Name", "Count");
        csv.row("Total", "All employees", r.total());
        r.byDepartment().forEach(d -> csv.row("Department", d.name(), d.count()));
        r.byStatus().forEach(s -> csv.row("Status", s.status().name(), s.count()));
        return csv.bytes();
    }

    public byte[] attendanceTrend(AttendanceTrendReport r) {
        Csv csv = new Csv().row("Period start", "Period end", "Present", "Late", "Half day", "Absent", "On leave");
        r.points().forEach(p -> csv.row(p.periodStart(), p.periodEnd(), p.present(), p.late(), p.halfDay(), p.absent(),
                p.onLeave()));
        csv.row("Total", "", r.totals().present(), r.totals().late(), r.totals().halfDay(), r.totals().absent(),
                r.totals().onLeave());
        return csv.bytes();
    }

    public byte[] leaveUtilization(LeaveUtilizationReport r) {
        Csv csv = new Csv().row("Breakdown", "Name", "Leave days", "Requests");
        csv.row("Total", "All approved leave", r.totalDays(), r.totalRequests());
        r.byType().forEach(t -> csv.row("Leave type", t.name(), t.days(), t.requests()));
        r.byDepartment().forEach(d -> csv.row("Department", d.name(), d.days(), d.requests()));
        return csv.bytes();
    }

    public byte[] onboarding(OnboardingReport r) {
        Csv csv = new Csv().row("Month", "New hires", "Still employed");
        r.months().forEach(m -> csv.row(m.monthStart().toString().substring(0, 7), m.hires(), m.stillEmployed()));
        csv.row("Total", r.totalHires(), r.totalStillEmployed());
        return csv.bytes();
    }

    /** A filename that says what is inside, e.g. attendance-trend_2026-03-02_to_2026-03-06.csv */
    public static String fileName(String slug, LocalDate from, LocalDate to, LocalDate today) {
        return from != null && to != null ? slug + "_" + from + "_to_" + to + ".csv" : slug + "_" + today + ".csv";
    }
}
