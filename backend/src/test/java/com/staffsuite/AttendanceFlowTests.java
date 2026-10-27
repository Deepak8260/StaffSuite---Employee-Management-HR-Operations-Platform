package com.staffsuite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.staffsuite.entity.AttendanceCorrection;
import com.staffsuite.entity.AttendanceRecord;
import com.staffsuite.entity.AttendanceStatus;
import com.staffsuite.entity.Department;
import com.staffsuite.entity.Employee;
import com.staffsuite.entity.EmploymentStatus;
import com.staffsuite.entity.LeaveRequest;
import com.staffsuite.entity.LeaveStatus;
import com.staffsuite.entity.LeaveType;
import com.staffsuite.entity.Role;
import com.staffsuite.entity.User;
import com.staffsuite.repository.AttendanceCorrectionRepository;
import com.staffsuite.repository.AttendanceRecordRepository;
import com.staffsuite.repository.AuditLogRepository;
import com.staffsuite.repository.DepartmentRepository;
import com.staffsuite.repository.EmployeeRepository;
import com.staffsuite.repository.LeaveBalanceRepository;
import com.staffsuite.repository.LeaveRequestRepository;
import com.staffsuite.repository.LeaveTypeRepository;
import com.staffsuite.repository.PublicHolidayRepository;
import com.staffsuite.repository.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AttendanceFlowTests.ClockConfig.class)
class AttendanceFlowTests {

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(ZoneId.of("UTC"), LocalDateTime.of(2026, 3, 11, 9, 0));
        }
    }

    static final LocalDate WED = LocalDate.of(2026, 3, 11);
    static final LocalDate FRI = LocalDate.of(2026, 3, 13);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired UserRepository users;
    @Autowired EmployeeRepository employees;
    @Autowired DepartmentRepository departments;
    @Autowired AttendanceRecordRepository records;
    @Autowired AttendanceCorrectionRepository corrections;
    @Autowired LeaveTypeRepository leaveTypes;
    @Autowired LeaveRequestRepository leaveRequests;
    @Autowired LeaveBalanceRepository leaveBalances;
    @Autowired PublicHolidayRepository holidays;
    @Autowired AuditLogRepository audits;
    @Autowired PasswordEncoder encoder;

    Department eng;
    Department ops;
    Employee hrEmp;
    Employee mgrEmp;
    Employee empA;   // reports to mgrEmp
    Employee empB;   // no manager
    LeaveType annual;

    @BeforeEach
    void seed() {
        wipe();
        clock.set(WED.atTime(9, 0));
        eng = department("Engineering");
        ops = department("Operations");
        annual = new LeaveType();
        annual.setName("Annual");
        annual.setAnnualEntitlementDays(new BigDecimal("18"));
        annual = leaveTypes.save(annual);

        hrEmp = employee("Hana HR", "hana@test.com", ops, null);
        mgrEmp = employee("Mark Manager", "mark@test.com", ops, null);
        empA = employee("Asha Employee", "asha@test.com", eng, mgrEmp);
        empB = employee("Bela Employee", "bela@test.com", eng, null);

        account("admin@test.com", Role.SYSTEM_ADMIN, null);
        account("hr@test.com", Role.HR_ADMIN, hrEmp);
        account("mgr@test.com", Role.MANAGER, mgrEmp);
        account("a@test.com", Role.EMPLOYEE, empA);
        account("b@test.com", Role.EMPLOYEE, empB);
    }

    @AfterEach
    void cleanUp() {
        wipe();
    }

    private void wipe() {
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        corrections.deleteAllInBatch();
        records.deleteAllInBatch();
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

    private Employee employee(String name, String email, Department dept, Employee manager) {
        Employee e = new Employee();
        e.setEmployeeCode("EMP-T" + Math.abs(email.hashCode()));
        e.setFullName(name);
        e.setEmail(email);
        e.setJoiningDate(LocalDate.of(2025, 1, 1));
        e.setJobTitle("Staff");
        e.setEmploymentStatus(EmploymentStatus.ACTIVE);
        e.setDepartment(dept);
        e.setManager(manager);
        return employees.save(e);
    }

    private void account(String email, Role role, Employee employee) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(encoder.encode("Passw0rd!x"));
        u.setRole(role);
        u.setEmployeeId(employee == null ? null : employee.getId());
        users.save(u);
    }

    private String bearer(String email) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"Passw0rd!x\"}")).andReturn();
        return "Bearer " + json.readTree(res.getResponse().getContentAsString()).get("token").asString();
    }

    private ResultActions send(String method, String url, String token, String body) throws Exception {
        MockHttpServletRequestBuilder req = switch (method) {
            case "POST" -> post(url);
            case "PUT" -> put(url);
            default -> get(url);
        };
        req.header("Authorization", token);
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(req);
    }

    private JsonNode body(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private void at(LocalDate date, int hour, int minute) {
        clock.set(date.atTime(hour, minute));
    }

    private AttendanceRecord record(Employee e, LocalDate date, int inH, int inM, Integer outH, Integer outM,
            AttendanceStatus status, String hours) {
        AttendanceRecord r = new AttendanceRecord();
        r.setEmployeeId(e.getId());
        r.setAttendanceDate(date);
        r.setCheckInTime(date.atTime(inH, inM));
        if (outH != null) {
            r.setCheckOutTime(date.atTime(outH, outM));
        }
        r.setStatus(status);
        r.setWorkingHours(hours == null ? null : new BigDecimal(hours));
        return records.save(r);
    }

    private void approvedLeave(Employee e, LocalDate from, LocalDate to) {
        LeaveRequest l = new LeaveRequest();
        l.setEmployee(e);
        l.setLeaveType(annual);
        l.setStartDate(from);
        l.setEndDate(to);
        l.setTotalDays(new BigDecimal("1"));
        l.setStatus(LeaveStatus.APPROVED);
        leaveRequests.save(l);
    }

    private JsonNode day(String token, Employee e, LocalDate d) throws Exception {
        return body(send("GET", "/api/v1/attendance/" + e.getId() + "?from=" + d + "&to=" + d, token, null)
                .andExpect(status().isOk())).get(0);
    }

    private static String dt(LocalDate d, int h, int m) {
        return "\"" + d.atTime(h, m) + ":00\"";
    }

    private String correctionBody(LocalDate d, String in, String out) {
        return "{\"attendanceDate\":\"" + d + "\"" + (in == null ? "" : ",\"requestedCheckIn\":" + in)
                + (out == null ? "" : ",\"requestedCheckOut\":" + out) + ",\"reason\":\"Forgot to punch\"}";
    }

    private long submitCorrection(String token, String payload) throws Exception {
        return body(send("POST", "/api/v1/attendance-corrections", token, payload).andExpect(status().isCreated()))
                .get("id").asLong();
    }

    // -------------------------------------------------------- check-in / check-out

    @Test
    void checkInCheckOutComputesHoursAndStatusAndRejectsDuplicates() throws Exception {
        String t = bearer("a@test.com");
        at(WED, 9, 10);
        send("POST", "/api/v1/attendance/check-in", t, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PRESENT"))
                .andExpect(jsonPath("$.checkInTime").value("2026-03-11T09:10:00"));

        at(WED, 9, 40);
        MvcResult dup = send("POST", "/api/v1/attendance/check-in", t, null).andExpect(status().isConflict()).andReturn();
        assertThat(dup.getResponse().getContentAsString()).contains("already checked in today at 09:10");
        assertThat(records.findByEmployeeIdAndAttendanceDate(empA.getId(), WED).orElseThrow().getCheckInTime())
                .isEqualTo(WED.atTime(9, 10));   // the first check-in is untouched

        at(WED, 18, 10);
        send("POST", "/api/v1/attendance/check-out", t, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.workingHours").value(9.0))
                .andExpect(jsonPath("$.status").value("PRESENT"))
                .andExpect(jsonPath("$.checkOutTime").value("2026-03-11T18:10:00"));
        send("POST", "/api/v1/attendance/check-out", t, null).andExpect(status().isConflict());
        send("POST", "/api/v1/attendance/check-in", t, null).andExpect(status().isConflict());
        assertThat(records.findAll()).hasSize(1);
    }

    @Test
    void statusIsDerivedFromCutoffAndHalfDayThreshold() throws Exception {
        LocalDate d2 = WED.plusDays(1);

        // exactly on the 09:30 cutoff is not late; a minute past it is
        String onTime = bearer("admin@test.com");   // no employee: used only to prove punches need one
        send("POST", "/api/v1/attendance/check-in", onTime, null).andExpect(status().isForbidden());
        at(WED, 9, 30);
        send("POST", "/api/v1/attendance/check-in", bearer("a@test.com"), null).andExpect(jsonPath("$.status").value("PRESENT"));
        at(WED, 9, 31);
        send("POST", "/api/v1/attendance/check-in", bearer("b@test.com"), null).andExpect(jsonPath("$.status").value("LATE"));

        // 09:45 in, 18:00 out -> LATE, 8.25h
        String t = bearer("a@test.com");
        at(d2, 9, 45);
        send("POST", "/api/v1/attendance/check-in", t, null).andExpect(jsonPath("$.status").value("LATE"));
        at(d2, 18, 0);
        send("POST", "/api/v1/attendance/check-out", t, null)
                .andExpect(jsonPath("$.workingHours").value(8.25)).andExpect(jsonPath("$.status").value("LATE"));

        // under 4h -> HALF_DAY (3.5h); exactly 4h is a normal day
        String b = bearer("b@test.com");
        at(d2, 9, 0);
        send("POST", "/api/v1/attendance/check-in", b, null).andExpect(status().isOk());
        at(d2, 12, 30);
        send("POST", "/api/v1/attendance/check-out", b, null)
                .andExpect(jsonPath("$.workingHours").value(3.5)).andExpect(jsonPath("$.status").value("HALF_DAY"));

        String m = bearer("mgr@test.com");
        at(d2, 9, 0);
        send("POST", "/api/v1/attendance/check-in", m, null).andExpect(status().isOk());
        at(d2, 13, 0);
        send("POST", "/api/v1/attendance/check-out", m, null)
                .andExpect(jsonPath("$.workingHours").value(4.0)).andExpect(jsonPath("$.status").value("PRESENT"));

        // late and short: the short day (HALF_DAY) takes precedence over LATE
        String h = bearer("hr@test.com");
        at(d2, 10, 0);
        send("POST", "/api/v1/attendance/check-in", h, null).andExpect(jsonPath("$.status").value("LATE"));
        at(d2, 12, 0);
        send("POST", "/api/v1/attendance/check-out", h, null).andExpect(jsonPath("$.status").value("HALF_DAY"));
    }

    @Test
    void cannotCheckOutWithoutCheckingInAndUsersWithoutAnEmployeeCannotPunch() throws Exception {
        send("POST", "/api/v1/attendance/check-out", bearer("a@test.com"), null).andExpect(status().isConflict());
        send("POST", "/api/v1/attendance/check-in", bearer("admin@test.com"), null).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/attendance/check-in")).andExpect(status().isUnauthorized());
    }

    @Test
    void approvedLeaveShowsAsOnLeaveNotAbsentAndBlocksCheckIn() throws Exception {
        clock.set(FRI.atTime(12, 0));
        holidays.save(holiday(LocalDate.of(2026, 3, 9), false));
        approvedLeave(empA, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 11));
        String t = bearer("a@test.com");

        JsonNode days = body(send("GET", "/api/v1/attendance/" + empA.getId() + "?from=2026-03-07&to=2026-03-13", t, null)
                .andExpect(status().isOk()));
        assertThat(days).hasSize(7);
        assertThat(days.get(0).get("dayType").asString()).isEqualTo("WEEKEND");       // Sat 7
        assertThat(days.get(1).get("dayType").asString()).isEqualTo("WEEKEND");       // Sun 8
        assertThat(days.get(2).get("dayType").asString()).isEqualTo("HOLIDAY");       // Mon 9
        assertThat(days.get(2).get("holidayName").asString()).isEqualTo("Test Holiday");
        assertThat(days.get(2).get("status").isNull()).isTrue();
        assertThat(days.get(3).get("status").asString()).isEqualTo("ON_LEAVE");       // Tue 10
        assertThat(days.get(3).get("leaveTypeName").asString()).isEqualTo("Annual");
        assertThat(days.get(4).get("status").asString()).isEqualTo("ON_LEAVE");       // Wed 11
        assertThat(days.get(5).get("status").asString()).isEqualTo("ABSENT");         // Thu 12: nothing recorded
        assertThat(days.get(6).get("status").isNull()).isTrue();                      // Fri 13 = today, not yet in

        // you cannot punch in on a day of approved leave
        at(LocalDate.of(2026, 3, 10), 9, 0);
        MvcResult blocked = send("POST", "/api/v1/attendance/check-in", t, null).andExpect(status().isConflict()).andReturn();
        assertThat(blocked.getResponse().getContentAsString()).contains("approved leave");
    }

    private com.staffsuite.entity.PublicHoliday holiday(LocalDate date, boolean optional) {
        com.staffsuite.entity.PublicHoliday h = new com.staffsuite.entity.PublicHoliday();
        h.setName("Test Holiday");
        h.setHolidayDate(date);
        h.setOptional(optional);
        return h;
    }

    @Test
    void attendanceCanOnlyBeReadBySelfDirectManagerOrHr() throws Exception {
        String range = "?from=2026-03-02&to=2026-03-06";
        send("GET", "/api/v1/attendance/" + empA.getId() + range, bearer("a@test.com"), null).andExpect(status().isOk());
        send("GET", "/api/v1/attendance/" + empA.getId() + range, bearer("mgr@test.com"), null).andExpect(status().isOk());
        send("GET", "/api/v1/attendance/" + empA.getId() + range, bearer("hr@test.com"), null).andExpect(status().isOk());
        send("GET", "/api/v1/attendance/" + empA.getId() + range, bearer("admin@test.com"), null).andExpect(status().isOk());
        send("GET", "/api/v1/attendance/" + empA.getId() + range, bearer("b@test.com"), null).andExpect(status().isForbidden());
        send("GET", "/api/v1/attendance/" + empB.getId() + range, bearer("mgr@test.com"), null).andExpect(status().isForbidden());
        send("GET", "/api/v1/attendance/99999" + range, bearer("hr@test.com"), null).andExpect(status().isNotFound());
        send("GET", "/api/v1/attendance/" + empA.getId() + "?from=2026-03-06&to=2026-03-02", bearer("a@test.com"), null)
                .andExpect(status().isBadRequest());
        send("GET", "/api/v1/attendance/" + empA.getId() + "?from=2026-01-01&to=2026-12-31", bearer("a@test.com"), null)
                .andExpect(status().isBadRequest());
        send("GET", "/api/v1/attendance/" + empA.getId(), bearer("a@test.com"), null).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ corrections

    @Test
    void approvedCorrectionUpdatesTheRecordAndRecomputesHoursAndStatus() throws Exception {
        clock.set(FRI.atTime(12, 0));
        String emp = bearer("a@test.com");
        String mgr = bearer("mgr@test.com");
        LocalDate thu = LocalDate.of(2026, 3, 12);

        // Thursday has no record at all, so it shows ABSENT
        assertThat(day(emp, empA, thu).get("status").asString()).isEqualTo("ABSENT");

        long id = submitCorrection(emp, correctionBody(thu, dt(thu, 9, 0), dt(thu, 17, 30)));
        JsonNode pending = day(emp, empA, thu);
        assertThat(pending.get("pendingCorrection").asBoolean()).isTrue();
        assertThat(pending.get("status").asString()).isEqualTo("ABSENT");   // nothing changes until approved
        send("POST", "/api/v1/attendance-corrections", emp, correctionBody(thu, dt(thu, 9, 5), null))
                .andExpect(status().isConflict());   // one pending request per day

        send("PUT", "/api/v1/attendance-corrections/" + id + "/approve", mgr, "{\"comment\":\"ok\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.reviewedByName").value("Mark Manager"));
        JsonNode fixed = day(emp, empA, thu);
        assertThat(fixed.get("status").asString()).isEqualTo("PRESENT");
        assertThat(fixed.get("workingHours").decimalValue()).isEqualByComparingTo("8.5");
        assertThat(fixed.get("checkInTime").asString()).isEqualTo("2026-03-12T09:00:00");
        assertThat(fixed.get("pendingCorrection").asBoolean()).isFalse();
        send("PUT", "/api/v1/attendance-corrections/" + id + "/approve", mgr, null).andExpect(status().isConflict());

        // a late arrival and a short day come out as LATE and HALF_DAY after approval
        LocalDate d1 = LocalDate.of(2026, 3, 6);
        long late = submitCorrection(emp, correctionBody(d1, dt(d1, 9, 50), dt(d1, 18, 20)));
        send("PUT", "/api/v1/attendance-corrections/" + late + "/approve", mgr, null).andExpect(status().isOk());
        JsonNode lateDay = day(emp, empA, d1);
        assertThat(lateDay.get("status").asString()).isEqualTo("LATE");
        assertThat(lateDay.get("workingHours").decimalValue()).isEqualByComparingTo("8.5");

        LocalDate d2 = LocalDate.of(2026, 3, 5);
        long half = submitCorrection(emp, correctionBody(d2, dt(d2, 9, 0), dt(d2, 12, 0)));
        send("PUT", "/api/v1/attendance-corrections/" + half + "/approve", mgr, null).andExpect(status().isOk());
        assertThat(day(emp, empA, d2).get("status").asString()).isEqualTo("HALF_DAY");
    }

    @Test
    void aCorrectionCanFixAMissingCheckOutOnAnExistingRecord() throws Exception {
        clock.set(FRI.atTime(12, 0));
        LocalDate d = LocalDate.of(2026, 3, 4);
        AttendanceRecord forgot = record(empA, d, 9, 0, null, null, AttendanceStatus.PRESENT, null);
        String emp = bearer("a@test.com");

        long id = submitCorrection(emp, "{\"attendanceRecordId\":" + forgot.getId() + ",\"requestedCheckOut\":"
                + dt(d, 18, 0) + ",\"reason\":\"Left at six\"}");
        send("PUT", "/api/v1/attendance-corrections/" + id + "/approve", bearer("hr@test.com"), null).andExpect(status().isOk());

        AttendanceRecord after = records.findById(forgot.getId()).orElseThrow();
        assertThat(after.getCheckInTime()).isEqualTo(d.atTime(9, 0));     // untouched
        assertThat(after.getCheckOutTime()).isEqualTo(d.atTime(18, 0));
        assertThat(after.getWorkingHours()).isEqualByComparingTo("9");
        assertThat(after.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
    }

    @Test
    void correctionRequestsAreValidated() throws Exception {
        clock.set(FRI.atTime(12, 0));
        String emp = bearer("a@test.com");
        LocalDate d = LocalDate.of(2026, 3, 10);

        send("POST", "/api/v1/attendance-corrections", emp, correctionBody(FRI.plusDays(1), dt(FRI.plusDays(1), 9, 0), null))
                .andExpect(status().isBadRequest());                                   // future date
        LocalDate old = FRI.minusDays(60);
        send("POST", "/api/v1/attendance-corrections", emp, correctionBody(old, dt(old, 9, 0), dt(old, 17, 0)))
                .andExpect(status().isBadRequest());                                   // outside the window
        send("POST", "/api/v1/attendance-corrections", emp, correctionBody(d, dt(d.minusDays(1), 9, 0), null))
                .andExpect(status().isBadRequest());                                   // time on another day
        send("POST", "/api/v1/attendance-corrections", emp, correctionBody(d, dt(d, 18, 0), dt(d, 9, 0)))
                .andExpect(status().isBadRequest());                                   // out before in
        send("POST", "/api/v1/attendance-corrections", emp, correctionBody(d, null, dt(d, 18, 0)))
                .andExpect(status().isBadRequest());                                   // no check-in anywhere
        send("POST", "/api/v1/attendance-corrections", emp, correctionBody(d, null, null))
                .andExpect(status().isBadRequest());                                   // nothing to change
        send("POST", "/api/v1/attendance-corrections", emp,
                "{\"requestedCheckIn\":" + dt(d, 9, 0) + ",\"reason\":\"x\"}").andExpect(status().isBadRequest());
        send("POST", "/api/v1/attendance-corrections", emp,
                "{\"attendanceDate\":\"" + d + "\",\"requestedCheckIn\":" + dt(d, 9, 0) + ",\"reason\":\" \"}")
                .andExpect(status().isBadRequest());                                   // reason required
        send("POST", "/api/v1/attendance-corrections", emp,
                correctionBody(FRI, dt(FRI, 13, 0), null)).andExpect(status().isBadRequest());   // after "now" (12:00)

        // someone else's record
        AttendanceRecord others = record(empB, d, 9, 0, 17, 0, AttendanceStatus.PRESENT, "8.00");
        send("POST", "/api/v1/attendance-corrections", emp, "{\"attendanceRecordId\":" + others.getId()
                + ",\"requestedCheckOut\":" + dt(d, 18, 0) + ",\"reason\":\"x\"}").andExpect(status().isForbidden());
        send("POST", "/api/v1/attendance-corrections", emp, "{\"attendanceRecordId\":99999,\"reason\":\"x\"}")
                .andExpect(status().isNotFound());

        // a day of approved leave has nothing to correct
        approvedLeave(empA, LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 9));
        LocalDate leaveDay = LocalDate.of(2026, 3, 9);
        send("POST", "/api/v1/attendance-corrections", emp, correctionBody(leaveDay, dt(leaveDay, 9, 0), dt(leaveDay, 17, 0)))
                .andExpect(status().isConflict());
        assertThat(corrections.count()).isZero();
        assertThat(records.findByEmployeeIdAndAttendanceDate(empA.getId(), d)).isEmpty();   // failed requests leave no placeholder
    }

    @Test
    void rejectingNeedsAReasonAndLeavesTheRecordUnchanged() throws Exception {
        clock.set(FRI.atTime(12, 0));
        String emp = bearer("a@test.com");
        String mgr = bearer("mgr@test.com");
        LocalDate d = LocalDate.of(2026, 3, 10);
        long id = submitCorrection(emp, correctionBody(d, dt(d, 9, 0), dt(d, 17, 0)));

        send("PUT", "/api/v1/attendance-corrections/" + id + "/reject", mgr, null).andExpect(status().isBadRequest());
        send("PUT", "/api/v1/attendance-corrections/" + id + "/reject", mgr, "{\"comment\":\" \"}").andExpect(status().isBadRequest());
        send("PUT", "/api/v1/attendance-corrections/" + id + "/reject", mgr, "{\"comment\":\"No evidence\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reviewComment").value("No evidence"));

        AttendanceRecord stored = records.findByEmployeeIdAndAttendanceDate(empA.getId(), d).orElseThrow();
        assertThat(stored.getCheckInTime()).isNull();
        assertThat(day(emp, empA, d).get("status").asString()).isEqualTo("ABSENT");
        // a rejected request does not block a fresh one
        submitCorrection(emp, correctionBody(d, dt(d, 9, 15), dt(d, 17, 15)));
    }

    @Test
    void nobodyDecidesTheirOwnCorrectionAndOnlyTheDirectManagerOrHrCan() throws Exception {
        clock.set(FRI.atTime(12, 0));
        LocalDate d = LocalDate.of(2026, 3, 10);
        String mgr = bearer("mgr@test.com");
        String hr = bearer("hr@test.com");

        long mgrOwn = submitCorrection(mgr, correctionBody(d, dt(d, 9, 0), dt(d, 17, 0)));
        MvcResult self = send("PUT", "/api/v1/attendance-corrections/" + mgrOwn + "/approve", mgr, null)
                .andExpect(status().isForbidden()).andReturn();
        assertThat(self.getResponse().getContentAsString()).contains("own correction request");
        send("PUT", "/api/v1/attendance-corrections/" + mgrOwn + "/reject", mgr, "{\"comment\":\"no\"}")
                .andExpect(status().isForbidden());
        long hrOwn = submitCorrection(hr, correctionBody(d, dt(d, 9, 0), dt(d, 17, 0)));
        send("PUT", "/api/v1/attendance-corrections/" + hrOwn + "/approve", hr, null).andExpect(status().isForbidden());
        assertThat(corrections.findById(mgrOwn).orElseThrow().getStatus().name()).isEqualTo("PENDING");

        send("PUT", "/api/v1/attendance-corrections/" + mgrOwn + "/approve", hr, null).andExpect(status().isOk());
        send("PUT", "/api/v1/attendance-corrections/" + hrOwn + "/approve", bearer("admin@test.com"), null).andExpect(status().isOk());

        long bela = submitCorrection(bearer("b@test.com"), correctionBody(d, dt(d, 9, 0), dt(d, 17, 0)));
        send("PUT", "/api/v1/attendance-corrections/" + bela + "/approve", mgr, null).andExpect(status().isForbidden());   // not their report
        send("PUT", "/api/v1/attendance-corrections/" + bela + "/approve", bearer("a@test.com"), null).andExpect(status().isForbidden());
        send("PUT", "/api/v1/attendance-corrections/99999/approve", hr, null).andExpect(status().isNotFound());
    }

    @Test
    void correctionVisibilityIsScopedByRole() throws Exception {
        clock.set(FRI.atTime(12, 0));
        LocalDate d = LocalDate.of(2026, 3, 10);
        submitCorrection(bearer("a@test.com"), correctionBody(d, dt(d, 9, 0), dt(d, 17, 0)));
        submitCorrection(bearer("b@test.com"), correctionBody(d, dt(d, 9, 0), dt(d, 17, 0)));
        submitCorrection(bearer("mgr@test.com"), correctionBody(d, dt(d, 9, 0), dt(d, 17, 0)));

        String emp = bearer("a@test.com");
        send("GET", "/api/v1/attendance-corrections", emp, null).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].employeeName").value("Asha Employee"));
        send("GET", "/api/v1/attendance-corrections?scope=ALL", emp, null).andExpect(status().isForbidden());
        send("GET", "/api/v1/attendance-corrections?scope=TEAM", emp, null).andExpect(status().isForbidden());

        String mgr = bearer("mgr@test.com");
        send("GET", "/api/v1/attendance-corrections", mgr, null).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].employeeName").value("Asha Employee"));
        send("GET", "/api/v1/attendance-corrections?scope=MINE", mgr, null).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].employeeName").value("Mark Manager"));
        send("GET", "/api/v1/attendance-corrections?scope=ALL", mgr, null).andExpect(status().isForbidden());

        for (String who : new String[] { "hr@test.com", "admin@test.com" }) {
            String t = bearer(who);
            send("GET", "/api/v1/attendance-corrections", t, null).andExpect(jsonPath("$.totalElements").value(3));
            send("GET", "/api/v1/attendance-corrections?status=PENDING", t, null).andExpect(jsonPath("$.totalElements").value(3));
            send("GET", "/api/v1/attendance-corrections?status=APPROVED", t, null).andExpect(jsonPath("$.totalElements").value(0));
        }
        send("GET", "/api/v1/attendance-corrections?size=2", bearer("hr@test.com"), null)
                .andExpect(jsonPath("$.content.length()").value(2)).andExpect(jsonPath("$.totalPages").value(2));
        send("GET", "/api/v1/attendance-corrections?status=NOPE", bearer("hr@test.com"), null).andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- summary

    @Test
    void summaryAggregatesByDepartmentAndEmployeeAndIsScopedByRole() throws Exception {
        clock.set(FRI.atTime(12, 0));   // Friday: today, so it is not counted for anyone without a punch
        record(empA, LocalDate.of(2026, 3, 9), 9, 0, 18, 0, AttendanceStatus.PRESENT, "9.00");
        record(empA, LocalDate.of(2026, 3, 10), 9, 45, 18, 0, AttendanceStatus.LATE, "8.25");
        record(empA, LocalDate.of(2026, 3, 11), 9, 0, 12, 0, AttendanceStatus.HALF_DAY, "3.00");
        approvedLeave(empA, LocalDate.of(2026, 3, 12), LocalDate.of(2026, 3, 12));
        record(empB, LocalDate.of(2026, 3, 9), 9, 0, 17, 0, AttendanceStatus.PRESENT, "8.00");

        String range = "?from=2026-03-09&to=2026-03-13";
        JsonNode hr = body(send("GET", "/api/v1/attendance/summary" + range, bearer("hr@test.com"), null).andExpect(status().isOk()));
        assertThat(hr.get("groupBy").asString()).isEqualTo("DEPARTMENT");
        JsonNode engRow = null;
        for (JsonNode row : hr.get("rows")) {
            if (row.get("label").asString().equals("Engineering")) {
                engRow = row;
            }
        }
        assertThat(engRow).isNotNull();
        assertThat(engRow.get("employeeCount").asInt()).isEqualTo(2);
        assertThat(engRow.get("present").asInt()).isEqualTo(2);
        assertThat(engRow.get("late").asInt()).isEqualTo(1);
        assertThat(engRow.get("halfDay").asInt()).isEqualTo(1);
        assertThat(engRow.get("onLeave").asInt()).isEqualTo(1);    // leave day is ON_LEAVE, not ABSENT
        assertThat(engRow.get("absent").asInt()).isEqualTo(3);     // Bela: Tue, Wed, Thu
        assertThat(engRow.get("totalHours").decimalValue()).isEqualByComparingTo("28.25");
        assertThat(hr.get("rows")).hasSize(2);
        assertThat(hr.get("totals").get("employeeCount").asInt()).isEqualTo(4);

        // department filter
        JsonNode onlyEng = body(send("GET", "/api/v1/attendance/summary" + range + "&departmentId=" + eng.getId(),
                bearer("admin@test.com"), null));
        assertThat(onlyEng.get("rows")).hasSize(1);

        // a manager only ever sees their own team (Asha), never the whole department
        JsonNode mgr = body(send("GET", "/api/v1/attendance/summary" + range, bearer("mgr@test.com"), null)
                .andExpect(status().isOk()));
        assertThat(mgr.get("rows")).hasSize(1);
        assertThat(mgr.get("rows").get(0).get("employeeCount").asInt()).isEqualTo(1);
        assertThat(mgr.get("rows").get(0).get("absent").asInt()).isZero();
        assertThat(mgr.get("rows").get(0).get("onLeave").asInt()).isEqualTo(1);
        JsonNode perPerson = body(send("GET", "/api/v1/attendance/summary" + range + "&groupBy=EMPLOYEE",
                bearer("mgr@test.com"), null));
        assertThat(perPerson.get("rows")).hasSize(1);
        assertThat(perPerson.get("rows").get(0).get("label").asString()).isEqualTo("Asha Employee");
        assertThat(perPerson.toString()).doesNotContain("Bela");

        send("GET", "/api/v1/attendance/summary" + range, bearer("a@test.com"), null).andExpect(status().isForbidden());
        send("GET", "/api/v1/attendance/summary", bearer("hr@test.com"), null).andExpect(status().isBadRequest());
        send("GET", "/api/v1/attendance/summary" + range + "&groupBy=NOPE", bearer("hr@test.com"), null)
                .andExpect(status().isBadRequest());
    }
}
