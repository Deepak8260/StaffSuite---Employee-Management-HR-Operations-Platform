package com.staffsuite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.staffsuite.entity.AttendanceRecord;
import com.staffsuite.entity.AttendanceStatus;
import com.staffsuite.entity.Department;
import com.staffsuite.entity.Employee;
import com.staffsuite.entity.EmploymentStatus;
import com.staffsuite.entity.LeaveRequest;
import com.staffsuite.entity.LeaveStatus;
import com.staffsuite.entity.LeaveType;
import com.staffsuite.entity.PublicHoliday;
import com.staffsuite.entity.Role;
import com.staffsuite.entity.User;
import com.staffsuite.repository.AttendanceRecordRepository;
import com.staffsuite.repository.AuditLogRepository;
import com.staffsuite.repository.DepartmentRepository;
import com.staffsuite.repository.EmployeeRepository;
import com.staffsuite.repository.LeaveBalanceRepository;
import com.staffsuite.repository.LeaveRequestRepository;
import com.staffsuite.repository.LeaveTypeRepository;
import com.staffsuite.repository.NotificationRepository;
import com.staffsuite.repository.PublicHolidayRepository;
import com.staffsuite.repository.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The expected numbers in these tests were worked out by hand from the data set built in seed(), not
 * copied from the code's output. See the comments next to each assertion for the arithmetic.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportFlowTests {

    static final LocalDate MON = LocalDate.of(2026, 3, 2);
    static final LocalDate TUE = LocalDate.of(2026, 3, 3);
    static final LocalDate WED = LocalDate.of(2026, 3, 4);   // a public holiday
    static final LocalDate THU = LocalDate.of(2026, 3, 5);
    static final LocalDate FRI = LocalDate.of(2026, 3, 6);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @Autowired UserRepository users;
    @Autowired EmployeeRepository employees;
    @Autowired DepartmentRepository departments;
    @Autowired LeaveTypeRepository leaveTypes;
    @Autowired LeaveBalanceRepository leaveBalances;
    @Autowired LeaveRequestRepository leaveRequests;
    @Autowired PublicHolidayRepository holidays;
    @Autowired AttendanceRecordRepository attendance;
    @Autowired NotificationRepository notifications;
    @Autowired AuditLogRepository audits;
    @Autowired PasswordEncoder encoder;

    Department eng, sales, support, evil;
    LeaveType annual, sick, study;

    @BeforeEach
    void seed() {
        wipe();
        eng = department("Engineering");
        sales = department("Sales");
        support = department("Support");                  // has nobody in it
        evil = department("=EVIL, Ops");                  // a name that would run as a formula in a spreadsheet

        annual = leaveType("Annual");
        sick = leaveType("Sick");
        study = leaveType("Study");                       // never used

        Employee e1 = employee("Eve One", "e1@t.com", eng, EmploymentStatus.ACTIVE, "2025-11-03", false);
        Employee e2 = employee("Eli Two", "e2@t.com", eng, EmploymentStatus.PROBATION, "2025-11-20", false);
        Employee e3 = employee("Ena Three", "e3@t.com", eng, EmploymentStatus.ON_LEAVE, "2025-12-01", false);
        Employee s1 = employee("Sam One", "s1@t.com", sales, EmploymentStatus.ACTIVE, "2026-01-05", false);
        Employee s2 = employee("Sue Two", "s2@t.com", sales, EmploymentStatus.ACTIVE, "2026-03-05", false);
        employee("Xavi Gone", "x@t.com", sales, EmploymentStatus.TERMINATED, "2026-01-15", true);   // offboarded
        employee("Nora None", "n@t.com", null, EmploymentStatus.ACTIVE, "2026-03-16", false);        // no department
        Employee gone = employees.findAll().stream().filter(e -> e.isDeleted()).findFirst().orElseThrow();

        account("admin@t.com", Role.SYSTEM_ADMIN);
        account("hr@t.com", Role.HR_ADMIN);
        account("mgr@t.com", Role.MANAGER);
        account("emp@t.com", Role.EMPLOYEE);

        holiday(WED);                                      // Wed 4 Mar: nobody is expected at work
        holiday(LocalDate.of(2026, 3, 18));                // Wed 18 Mar: falls inside E1's leave below

        // Mon: E1 present, E2 late, E3 present, S1 present
        punch(e1, MON, 9, 0, AttendanceStatus.PRESENT);
        punch(e2, MON, 9, 45, AttendanceStatus.LATE);
        punch(e3, MON, 9, 0, AttendanceStatus.PRESENT);
        punch(s1, MON, 9, 0, AttendanceStatus.PRESENT);
        // Tue: E1 present, E2 present, S1 half day; E3 has no record -> absent
        punch(e1, TUE, 9, 0, AttendanceStatus.PRESENT);
        punch(e2, TUE, 9, 0, AttendanceStatus.PRESENT);
        punch(s1, TUE, 9, 0, AttendanceStatus.HALF_DAY);
        // Thu: E1, S1, S2 present; E2 on approved leave (below); E3 no record -> absent
        punch(e1, THU, 9, 0, AttendanceStatus.PRESENT);
        punch(s1, THU, 9, 0, AttendanceStatus.PRESENT);
        punch(s2, THU, 9, 0, AttendanceStatus.PRESENT);
        // Fri: E2, E3, S1 present, S2 late; E1 no record -> absent
        punch(e2, FRI, 9, 0, AttendanceStatus.PRESENT);
        punch(e3, FRI, 9, 0, AttendanceStatus.PRESENT);
        punch(s1, FRI, 9, 0, AttendanceStatus.PRESENT);
        punch(s2, FRI, 9, 45, AttendanceStatus.LATE);

        leave(e2, sick, THU, THU, LeaveStatus.APPROVED);                                         // 1 day, also shows in attendance
        leave(e1, annual, LocalDate.of(2026, 3, 16), LocalDate.of(2026, 3, 20), LeaveStatus.APPROVED);   // 5 weekdays, 18th is a holiday -> 4 days
        leave(s1, annual, LocalDate.of(2026, 3, 30), LocalDate.of(2026, 4, 3), LeaveStatus.APPROVED);    // 5 days: 2 in March, 3 in April
        leave(s2, annual, LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 9), LeaveStatus.PENDING);      // not approved: never counted
        leave(e3, annual, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 10), LeaveStatus.REJECTED);   // rejected: never counted
        leave(gone, sick, LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 9), LeaveStatus.APPROVED);     // offboarded: not counted
    }

    @AfterEach
    void cleanUp() {
        wipe();
    }

    private void wipe() {
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        notifications.deleteAllInBatch();
        attendance.deleteAllInBatch();
        leaveRequests.deleteAllInBatch();
        leaveBalances.deleteAllInBatch();
        leaveTypes.deleteAllInBatch();
        holidays.deleteAllInBatch();
        audits.deleteAllInBatch();
        users.deleteAllInBatch();
        employees.deleteAllInBatch();
        departments.deleteAllInBatch();
        jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }

    // ------------------------------------------------------------------ helpers

    private Department department(String name) {
        Department d = new Department();
        d.setName(name);
        return departments.save(d);
    }

    private LeaveType leaveType(String name) {
        LeaveType t = new LeaveType();
        t.setName(name);
        t.setAnnualEntitlementDays(new BigDecimal("18"));
        return leaveTypes.save(t);
    }

    private Employee employee(String name, String email, Department dept, EmploymentStatus status, String joined, boolean deleted) {
        Employee e = new Employee();
        e.setEmployeeCode("EMP-T" + Math.abs(email.hashCode()));
        e.setFullName(name);
        e.setEmail(email);
        e.setJoiningDate(LocalDate.parse(joined));
        e.setJobTitle("Staff");
        e.setEmploymentStatus(status);
        e.setDepartment(dept);
        e.setDeleted(deleted);
        return employees.save(e);
    }

    private void account(String email, Role role) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(encoder.encode("Passw0rd!x"));
        u.setRole(role);
        users.save(u);
    }

    private void holiday(LocalDate date) {
        PublicHoliday h = new PublicHoliday();
        h.setName("Holiday " + date);
        h.setHolidayDate(date);
        holidays.save(h);
    }

    private void punch(Employee e, LocalDate date, int h, int m, AttendanceStatus status) {
        AttendanceRecord r = new AttendanceRecord();
        r.setEmployeeId(e.getId());
        r.setAttendanceDate(date);
        r.setCheckInTime(LocalDateTime.of(date, java.time.LocalTime.of(h, m)));
        r.setStatus(status);
        attendance.save(r);
    }

    private void leave(Employee e, LeaveType type, LocalDate from, LocalDate to, LeaveStatus status) {
        LeaveRequest l = new LeaveRequest();
        l.setEmployee(e);
        l.setLeaveType(type);
        l.setStartDate(from);
        l.setEndDate(to);
        l.setTotalDays(BigDecimal.ONE);
        l.setStatus(status);
        leaveRequests.save(l);
    }

    private String bearer(String email) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"Passw0rd!x\"}")).andReturn();
        return "Bearer " + json.readTree(res.getResponse().getContentAsString()).get("token").asString();
    }

    private JsonNode report(String path) throws Exception {
        MvcResult res = mvc.perform(get("/api/v1/reports/" + path).header("Authorization", bearer("hr@t.com")))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(res.getResponse().getContentAsString());
    }

    private MvcResult csv(String path) throws Exception {
        return mvc.perform(get("/api/v1/reports/" + path).header("Authorization", bearer("hr@t.com")))
                .andExpect(status().isOk()).andReturn();
    }

    /** CSV text without the BOM, as rows of cells (the data in these tests has no embedded quotes). */
    private List<List<String>> rows(MvcResult res) {
        String text = new String(res.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(text).startsWith("﻿").endsWith("\r\n");
        List<List<String>> out = new ArrayList<>();
        for (String line : text.substring(1).split("\r\n")) {
            out.add(List.of(line.split(",", -1)));
        }
        return out;
    }

    private static JsonNode named(JsonNode items, String name) {
        for (JsonNode n : items) {
            if (n.get("name").asString().equals(name)) {
                return n;
            }
        }
        throw new AssertionError("no item named " + name + " in " + items);
    }

    private static int[] counts(JsonNode p) {
        return new int[] { p.get("present").asInt(), p.get("late").asInt(), p.get("halfDay").asInt(),
                p.get("absent").asInt(), p.get("onLeave").asInt() };
    }

    // ---------------------------------------------------------------- headcount

    @Test
    void headcountCountsCurrentEmployeesByDepartmentAndStatus() throws Exception {
        JsonNode r = report("headcount");
        // current employees: E1, E2, E3, S1, S2, Nora = 6 (Xavi is offboarded and not headcount)
        assertThat(r.get("total").asLong()).isEqualTo(6);
        assertThat(named(r.get("byDepartment"), "Engineering").get("count").asLong()).isEqualTo(3);
        assertThat(named(r.get("byDepartment"), "Sales").get("count").asLong()).isEqualTo(2);       // not 3: Xavi has left
        assertThat(named(r.get("byDepartment"), "Support").get("count").asLong()).isZero();           // empty departments still listed
        assertThat(named(r.get("byDepartment"), "No department").get("count").asLong()).isEqualTo(1);
        long departmentsTotal = 0;
        for (JsonNode d : r.get("byDepartment")) {
            departmentsTotal += d.get("count").asLong();
        }
        assertThat(departmentsTotal).isEqualTo(6);                                                   // the breakdown adds up to the total

        List<String> statuses = new ArrayList<>();
        r.get("byStatus").forEach(s -> statuses.add(s.get("status").asString() + "=" + s.get("count").asLong()));
        assertThat(statuses).containsExactly("ACTIVE=4", "ON_LEAVE=1", "PROBATION=1");   // E1+S1+S2+Nora, E3, E2; no TERMINATED row

        JsonNode inEng = report("headcount?departmentId=" + eng.getId());
        assertThat(inEng.get("total").asLong()).isEqualTo(3);
        assertThat(inEng.get("byDepartment")).hasSize(1);
        List<String> engStatuses = new ArrayList<>();
        inEng.get("byStatus").forEach(s -> engStatuses.add(s.get("status").asString() + "=" + s.get("count").asLong()));
        assertThat(engStatuses).containsExactly("ACTIVE=1", "ON_LEAVE=1", "PROBATION=1");
        assertThat(report("headcount?departmentId=" + support.getId()).get("total").asLong()).isZero();
    }

    // --------------------------------------------------------- attendance trend

    @Test
    void attendanceTrendMatchesTheRawRecordsDayByDay() throws Exception {
        JsonNode r = report("attendance-trend?from=2026-03-02&to=2026-03-06&granularity=DAILY");
        JsonNode p = r.get("points");
        assertThat(p).hasSize(5);
        assertThat(r.get("employees").asInt()).isEqualTo(6);       // current employees; Xavi excluded

        // present, late, half day, absent, on leave -- worked out from seed()
        assertThat(counts(p.get(0))).containsExactly(3, 1, 0, 0, 0);   // Mon: E1 E3 S1 present, E2 late
        assertThat(counts(p.get(1))).containsExactly(2, 0, 1, 1, 0);   // Tue: E1 E2 present, S1 half day, E3 absent
        assertThat(counts(p.get(2))).containsExactly(0, 0, 0, 0, 0);   // Wed: public holiday, nobody expected
        assertThat(counts(p.get(3))).containsExactly(3, 0, 0, 1, 1);   // Thu: E1 S1 S2 present, E3 absent, E2 on leave (not absent)
        assertThat(counts(p.get(4))).containsExactly(3, 1, 0, 1, 0);   // Fri: E2 E3 S1 present, S2 late, E1 absent
        assertThat(counts(r.get("totals"))).containsExactly(11, 2, 1, 3, 1);
        assertThat(11 + 2 + 1 + 3 + 1).isEqualTo(18);                   // 4 + 4 + 0 + 5 + 5 employee-days were expected at work
        assertThat(p.get(0).get("periodStart").asString()).isEqualTo("2026-03-02");
        assertThat(p.get(0).get("periodEnd").asString()).isEqualTo("2026-03-02");

        // Sue (S2) only joined on Thu 5 Mar, and Nora on 16 Mar: neither is counted before they started
        // The department filter keeps just Engineering (E1, E2, E3)
        JsonNode e = report("attendance-trend?from=2026-03-02&to=2026-03-06&granularity=DAILY&departmentId=" + eng.getId());
        assertThat(e.get("employees").asInt()).isEqualTo(3);
        assertThat(counts(e.get("points").get(0))).containsExactly(2, 1, 0, 0, 0);
        assertThat(counts(e.get("points").get(1))).containsExactly(2, 0, 0, 1, 0);
        assertThat(counts(e.get("points").get(3))).containsExactly(1, 0, 0, 1, 1);
        assertThat(counts(e.get("points").get(4))).containsExactly(2, 0, 0, 1, 0);
        assertThat(counts(e.get("totals"))).containsExactly(7, 1, 0, 3, 1);
    }

    @Test
    void attendanceTrendWeeklyAndMonthlyBucketsAddUpToTheSameTotals() throws Exception {
        JsonNode weekly = report("attendance-trend?from=2026-03-02&to=2026-03-08&granularity=WEEKLY");
        assertThat(weekly.get("points")).hasSize(1);
        assertThat(weekly.get("points").get(0).get("periodStart").asString()).isEqualTo("2026-03-02");
        assertThat(weekly.get("points").get(0).get("periodEnd").asString()).isEqualTo("2026-03-08");
        assertThat(counts(weekly.get("points").get(0))).containsExactly(11, 2, 1, 3, 1);

        // a range that starts mid-week still gets that week's bucket, clipped to the range
        JsonNode midWeek = report("attendance-trend?from=2026-03-04&to=2026-03-12&granularity=WEEKLY");
        assertThat(midWeek.get("points")).hasSize(2);
        assertThat(midWeek.get("points").get(0).get("periodStart").asString()).isEqualTo("2026-03-04");
        assertThat(midWeek.get("points").get(1).get("periodStart").asString()).isEqualTo("2026-03-09");

        JsonNode monthly = report("attendance-trend?from=2026-03-02&to=2026-03-06&granularity=MONTHLY");
        assertThat(monthly.get("points")).hasSize(1);
        assertThat(monthly.get("points").get(0).get("periodStart").asString()).isEqualTo("2026-03-02");
        assertThat(monthly.get("points").get(0).get("periodEnd").asString()).isEqualTo("2026-03-06");
        assertThat(counts(monthly.get("points").get(0))).containsExactly(11, 2, 1, 3, 1);
    }

    @Test
    void attendanceTrendHandlesTheFutureAndDefaults() throws Exception {
        JsonNode future = report("attendance-trend?from=2099-01-01&to=2099-01-31&granularity=DAILY");
        assertThat(future.get("points")).isEmpty();                    // nothing has happened yet
        assertThat(counts(future.get("totals"))).containsExactly(0, 0, 0, 0, 0);

        JsonNode defaults = report("attendance-trend");
        LocalDate today = LocalDate.now(clock);
        assertThat(defaults.get("from").asString()).isEqualTo(today.withDayOfMonth(1).toString());
        assertThat(defaults.get("to").asString()).isEqualTo(today.toString());
        assertThat(defaults.get("granularity").asString()).isEqualTo("DAILY");
    }

    // -------------------------------------------------------- leave utilisation

    @Test
    void leaveUtilizationCountsApprovedWorkingDaysInsideTheRange() throws Exception {
        JsonNode r = report("leave-utilization?from=2026-03-01&to=2026-03-31");
        // Annual: E1 16-20 Mar = 5 weekdays - the 18th holiday = 4, plus S1 30-31 Mar = 2 -> 6 days, 2 requests
        assertThat(named(r.get("byType"), "Annual").get("days").asLong()).isEqualTo(6);
        assertThat(named(r.get("byType"), "Annual").get("requests").asLong()).isEqualTo(2);
        // Sick: only E2's 1 day (Xavi's is excluded because he has left)
        assertThat(named(r.get("byType"), "Sick").get("days").asLong()).isEqualTo(1);
        assertThat(named(r.get("byType"), "Study").get("days").asLong()).isZero();                    // unused types are still listed
        assertThat(named(r.get("byDepartment"), "Engineering").get("days").asLong()).isEqualTo(5);     // 4 + 1
        assertThat(named(r.get("byDepartment"), "Engineering").get("requests").asLong()).isEqualTo(2);
        assertThat(named(r.get("byDepartment"), "Sales").get("days").asLong()).isEqualTo(2);
        assertThat(r.get("totalDays").asLong()).isEqualTo(7);                                           // 6 + 1 = 5 + 2
        assertThat(r.get("totalRequests").asLong()).isEqualTo(3);                                       // pending, rejected and Xavi's are not counted

        // the same S1 request, seen through April, contributes the other 3 days (1, 2 and 3 April)
        JsonNode april = report("leave-utilization?from=2026-04-01&to=2026-04-30");
        assertThat(named(april.get("byType"), "Annual").get("days").asLong()).isEqualTo(3);
        assertThat(april.get("totalDays").asLong()).isEqualTo(3);
        assertThat(april.get("totalRequests").asLong()).isEqualTo(1);

        // a range that is entirely a holiday: the request overlaps it but contributes no working days
        JsonNode holidayOnly = report("leave-utilization?from=2026-03-18&to=2026-03-18");
        assertThat(holidayOnly.get("totalDays").asLong()).isZero();
        assertThat(holidayOnly.get("totalRequests").asLong()).isEqualTo(1);

        // department filter
        JsonNode inEng = report("leave-utilization?from=2026-03-01&to=2026-03-31&departmentId=" + eng.getId());
        assertThat(inEng.get("totalDays").asLong()).isEqualTo(5);
        assertThat(named(inEng.get("byType"), "Annual").get("days").asLong()).isEqualTo(4);
        assertThat(named(inEng.get("byType"), "Sick").get("days").asLong()).isEqualTo(1);
        assertThat(inEng.get("byDepartment")).hasSize(1);
        JsonNode inSales = report("leave-utilization?from=2026-03-01&to=2026-03-31&departmentId=" + sales.getId());
        assertThat(inSales.get("totalDays").asLong()).isEqualTo(2);
    }

    // --------------------------------------------------------------- onboarding

    @Test
    void onboardingCountsHiresPerMonthIncludingPeopleWhoHaveSinceLeft() throws Exception {
        JsonNode r = report("onboarding-stats?from=2025-11-01&to=2026-03-31");
        JsonNode m = r.get("months");
        assertThat(m).hasSize(5);
        // hires by joining month: Nov E1 E2, Dec E3, Jan S1 + Xavi (who has since left), Feb nobody, Mar S2 + Nora
        long[] hires = { 2, 1, 2, 0, 2 };
        long[] still = { 2, 1, 1, 0, 2 };
        String[] starts = { "2025-11-01", "2025-12-01", "2026-01-01", "2026-02-01", "2026-03-01" };
        for (int i = 0; i < 5; i++) {
            assertThat(m.get(i).get("monthStart").asString()).isEqualTo(starts[i]);
            assertThat(m.get(i).get("hires").asLong()).as("hires %s", starts[i]).isEqualTo(hires[i]);
            assertThat(m.get(i).get("stillEmployed").asLong()).as("still employed %s", starts[i]).isEqualTo(still[i]);
        }
        assertThat(r.get("totalHires").asLong()).isEqualTo(7);
        assertThat(r.get("totalStillEmployed").asLong()).isEqualTo(6);

        JsonNode inEng = report("onboarding-stats?from=2025-11-01&to=2026-03-31&departmentId=" + eng.getId());
        assertThat(inEng.get("totalHires").asLong()).isEqualTo(3);        // E1, E2, E3; Nora (no department) is not Engineering
        assertThat(inEng.get("months").get(0).get("hires").asLong()).isEqualTo(2);
        assertThat(inEng.get("months").get(2).get("hires").asLong()).isZero();

        // narrowing the dates narrows the answer
        assertThat(report("onboarding-stats?from=2026-03-01&to=2026-03-31").get("totalHires").asLong()).isEqualTo(2);
        JsonNode defaults = report("onboarding-stats");
        assertThat(defaults.get("months")).hasSize(12);                  // the last 12 months by default
    }

    // ------------------------------------------------------------------- export

    @Test
    void csvExportsContainExactlyWhatTheReportsShow() throws Exception {
        // attendance trend
        MvcResult attendance = csv("attendance-trend/export?from=2026-03-02&to=2026-03-06&granularity=DAILY");
        assertThat(attendance.getResponse().getContentType()).startsWith("text/csv");
        assertThat(attendance.getResponse().getHeader("Content-Disposition"))
                .startsWith("attachment").contains("attendance-trend_2026-03-02_to_2026-03-06.csv");
        List<List<String>> a = rows(attendance);
        assertThat(a.get(0)).containsExactly("Period start", "Period end", "Present", "Late", "Half day", "Absent", "On leave");
        assertThat(a.get(1)).containsExactly("2026-03-02", "2026-03-02", "3", "1", "0", "0", "0");   // Mon
        assertThat(a.get(2)).containsExactly("2026-03-03", "2026-03-03", "2", "0", "1", "1", "0");   // Tue
        assertThat(a.get(4)).containsExactly("2026-03-05", "2026-03-05", "3", "0", "0", "1", "1");   // Thu
        assertThat(a.get(6)).containsExactly("Total", "", "11", "2", "1", "3", "1");
        assertThat(a).hasSize(7);

        // the department filter is honoured in the file, too
        List<List<String>> aEng = rows(csv("attendance-trend/export?from=2026-03-02&to=2026-03-06&granularity=DAILY&departmentId=" + eng.getId()));
        assertThat(aEng.get(aEng.size() - 1)).containsExactly("Total", "", "7", "1", "0", "3", "1");

        // leave utilisation
        List<List<String>> leave = rows(csv("leave-utilization/export?from=2026-03-01&to=2026-03-31"));
        assertThat(leave.get(0)).containsExactly("Breakdown", "Name", "Leave days", "Requests");
        assertThat(leave).contains(List.of("Total", "All approved leave", "7", "3"),
                List.of("Leave type", "Annual", "6", "2"), List.of("Leave type", "Sick", "1", "1"),
                List.of("Leave type", "Study", "0", "0"), List.of("Department", "Engineering", "5", "2"),
                List.of("Department", "Sales", "2", "1"));

        // onboarding
        List<List<String>> hires = rows(csv("onboarding-stats/export?from=2025-11-01&to=2026-03-31"));
        assertThat(hires).containsExactly(List.of("Month", "New hires", "Still employed"), List.of("2025-11", "2", "2"),
                List.of("2025-12", "1", "1"), List.of("2026-01", "2", "1"), List.of("2026-02", "0", "0"),
                List.of("2026-03", "2", "2"), List.of("Total", "7", "6"));

        // headcount (this one has a trickier cell, checked as raw text below)
        MvcResult headcount = csv("headcount/export");
        assertThat(headcount.getResponse().getHeader("Content-Disposition")).contains("headcount_" + LocalDate.now(clock) + ".csv");
        String text = new String(headcount.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(text).contains("Total,All employees,6\r\n", "Department,Engineering,3\r\n", "Department,Sales,2\r\n",
                "Department,Support,0\r\n", "Department,No department,1\r\n", "Status,ACTIVE,4\r\n",
                "Status,ON_LEAVE,1\r\n", "Status,PROBATION,1\r\n");
        assertThat(text).doesNotContain("TERMINATED");
    }

    @Test
    void csvCellsThatCouldRunAsFormulasAreDefusedAndCommasAreQuoted() throws Exception {
        String text = new String(csv("headcount/export").getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        // "=EVIL, Ops" would be executed by Excel; the leading apostrophe stops that, and the comma forces quotes
        assertThat(text).contains("Department,\"'=EVIL, Ops\",0\r\n");
        assertThat(text).doesNotContain(",=EVIL");
    }

    @Test
    void csvAndJsonAgreeForTheSameFilters() throws Exception {
        JsonNode json = report("headcount?departmentId=" + eng.getId());
        List<String> lines = List.of(new String(csv("headcount/export?departmentId=" + eng.getId()).getResponse()
                .getContentAsByteArray(), StandardCharsets.UTF_8).substring(1).split("\r\n"));
        assertThat(lines.get(1)).isEqualTo("Total,All employees," + json.get("total").asLong());
        for (JsonNode s : json.get("byStatus")) {
            assertThat(lines).contains("Status," + s.get("status").asString() + "," + s.get("count").asLong());
        }
    }

    // ------------------------------------------------------------ access control

    @Test
    void onlyHrAndAdminCanReadOrExportReports() throws Exception {
        String[] paths = { "headcount", "attendance-trend", "leave-utilization", "onboarding-stats",
                "headcount/export", "attendance-trend/export", "leave-utilization/export", "onboarding-stats/export" };
        for (String path : paths) {
            mvc.perform(get("/api/v1/reports/" + path)).andExpect(status().isUnauthorized());
            for (String who : new String[] { "emp@t.com", "mgr@t.com" }) {
                mvc.perform(get("/api/v1/reports/" + path).header("Authorization", bearer(who)))
                        .andExpect(status().isForbidden());
            }
            for (String who : new String[] { "hr@t.com", "admin@t.com" }) {
                mvc.perform(get("/api/v1/reports/" + path).header("Authorization", bearer(who)))
                        .andExpect(status().isOk());
            }
        }
    }

    @Test
    void badInputIsRefusedWithClearErrors() throws Exception {
        String hr = bearer("hr@t.com");
        for (String path : new String[] {
                "attendance-trend?from=2026-03-06&to=2026-03-02",             // end before start
                "attendance-trend?from=2024-01-01&to=2026-01-01",             // longer than 366 days
                "attendance-trend?granularity=HOURLY",                        // not a valid granularity
                "attendance-trend?from=yesterday",                            // not a date
                "attendance-trend?departmentId=99999",                        // no such department
                "leave-utilization?from=2020-01-01&to=2026-01-01",            // longer than three years
                "onboarding-stats?from=2026-03-31&to=2026-03-01",
                "headcount?departmentId=99999",
                "attendance-trend/export?from=2026-03-06&to=2026-03-02",      // exports validate the same way
                "headcount/export?departmentId=99999" }) {
            mvc.perform(get("/api/v1/reports/" + path).header("Authorization", hr)).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/v1/reports/payroll/export").header("Authorization", hr)).andExpect(status().isNotFound());
    }
}
