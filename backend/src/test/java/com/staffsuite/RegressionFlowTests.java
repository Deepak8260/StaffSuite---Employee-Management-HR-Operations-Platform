package com.staffsuite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.staffsuite.entity.Role;
import com.staffsuite.entity.User;
import com.staffsuite.repository.AttendanceCorrectionRepository;
import com.staffsuite.repository.AttendanceRecordRepository;
import com.staffsuite.repository.AuditLogRepository;
import com.staffsuite.repository.DepartmentRepository;
import com.staffsuite.repository.DesignationRepository;
import com.staffsuite.repository.DocumentCategoryRepository;
import com.staffsuite.repository.EmployeeDocumentRepository;
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
 * Phase 9: the whole product walked through its real HTTP API, the way the screens use it. Nothing is seeded
 * through repositories except the first SYSTEM_ADMIN login (the bootstrap admin); everything else is created
 * by the people who would create it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(RegressionFlowTests.TestBeans.class)
class RegressionFlowTests {

    @TestConfiguration
    static class TestBeans {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(ZoneId.of("UTC"), LocalDateTime.of(2026, 3, 11, 9, 0));   // a Wednesday
        }

        @Bean
        @Primary
        InMemoryStorageService memoryStorage() {
            return new InMemoryStorageService();
        }
    }

    static final byte[] PDF = "%PDF-1.4 regression".getBytes(StandardCharsets.UTF_8);
    static final String PASSWORD = "Passw0rd!x";
    static final LocalDate TODAY = LocalDate.of(2026, 3, 11);
    // leave is validated against the real calendar (this year +/- 1), so use next year's first Monday of March
    final int leaveYear = LocalDate.now().getYear() + 1;
    final LocalDate leaveMonday = LocalDate.of(leaveYear, 3, 1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired PasswordEncoder encoder;
    @Autowired UserRepository users;
    @Autowired EmployeeRepository employees;
    @Autowired DepartmentRepository departments;
    @Autowired DesignationRepository designations;
    @Autowired LeaveTypeRepository leaveTypes;
    @Autowired LeaveBalanceRepository leaveBalances;
    @Autowired LeaveRequestRepository leaveRequests;
    @Autowired PublicHolidayRepository holidays;
    @Autowired AttendanceRecordRepository attendanceRecords;
    @Autowired AttendanceCorrectionRepository corrections;
    @Autowired DocumentCategoryRepository categories;
    @Autowired EmployeeDocumentRepository documents;
    @Autowired NotificationRepository notifications;
    @Autowired AuditLogRepository audits;

    String admin;

    @BeforeEach
    void bootstrapAdmin() throws Exception {
        wipe();
        clock.set(TODAY.atTime(9, 0));
        User u = new User();
        u.setEmail("admin@test.com");
        u.setPasswordHash(encoder.encode(PASSWORD));
        u.setRole(Role.SYSTEM_ADMIN);
        users.save(u);
        admin = login("admin@test.com");
    }

    @AfterEach
    void cleanUp() {
        wipe();
    }

    private void wipe() {
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        notifications.deleteAllInBatch();
        corrections.deleteAllInBatch();
        attendanceRecords.deleteAllInBatch();
        leaveRequests.deleteAllInBatch();
        leaveBalances.deleteAllInBatch();
        leaveTypes.deleteAllInBatch();
        holidays.deleteAllInBatch();
        documents.deleteAllInBatch();
        categories.deleteAllInBatch();
        designations.deleteAllInBatch();
        audits.deleteAllInBatch();
        users.deleteAllInBatch();
        employees.deleteAllInBatch();
        departments.deleteAllInBatch();
        jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }

    // ------------------------------------------------------------------ helpers

    private String login(String email) throws Exception {
        JsonNode res = body(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")).andExpect(status().isOk()));
        return "Bearer " + res.get("token").asString();
    }

    private ResultActions send(String method, String url, String token, String payload) throws Exception {
        MockHttpServletRequestBuilder req = switch (method) {
            case "POST" -> post(url);
            case "PUT" -> put(url);
            case "DELETE" -> delete(url);
            default -> get(url);
        };
        req.header("Authorization", token);
        if (payload != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(payload);
        }
        return mvc.perform(req);
    }

    private JsonNode body(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private long id(ResultActions created) throws Exception {
        return body(created.andExpect(status().isCreated())).get("id").asLong();
    }

    private long onboard(String name, String email, long departmentId, Long managerId, Role role) throws Exception {
        String payload = "{\"fullName\":\"" + name + "\",\"email\":\"" + email + "\",\"joiningDate\":\"2026-03-02\","
                + "\"jobTitle\":\"Staff\",\"employmentStatus\":\"ACTIVE\",\"departmentId\":" + departmentId
                + (managerId == null ? "" : ",\"managerId\":" + managerId)
                + ",\"userAccount\":{\"password\":\"" + PASSWORD + "\",\"role\":\"" + role + "\"}}";
        return id(send("POST", "/api/v1/employees", admin, payload));
    }

    private ResultActions upload(String token, long employeeId, long categoryId, String expiry) throws Exception {
        var req = multipart("/api/v1/employees/" + employeeId + "/documents")
                .file(new MockMultipartFile("file", "passport.pdf", "application/pdf", PDF))
                .param("categoryId", String.valueOf(categoryId));
        if (expiry != null) {
            req.param("expiryDate", expiry);
        }
        return mvc.perform(req.header("Authorization", token));
    }

    private long unread(String token) throws Exception {
        return body(send("GET", "/api/v1/notifications/unread-count", token, null).andExpect(status().isOk())).get("count").asLong();
    }

    // ------------------------------------------------------------------ the full journey

    @Test
    void fullJourneyFromOnboardingToOffboarding() throws Exception {
        // 1. Admin sets up the organisation and onboards people with linked logins.
        long dept = id(send("POST", "/api/v1/departments", admin, "{\"name\":\"Engineering\"}"));
        long designation = id(send("POST", "/api/v1/designations", admin,
                "{\"title\":\"Software Engineer\",\"departmentId\":" + dept + "}"));
        long leaveType = id(send("POST", "/api/v1/leave-types", admin,
                "{\"name\":\"Annual Leave\",\"annualEntitlementDays\":18,\"carryForwardAllowed\":false}"));
        long category = id(send("POST", "/api/v1/documents/categories", admin, "{\"name\":\"ID Proof\"}"));

        long hrId = onboard("Hana HR", "hr@test.com", dept, null, Role.HR_ADMIN);
        long managerId = onboard("Mark Manager", "mgr@test.com", dept, null, Role.MANAGER);
        long empId = onboard("Asha Employee", "asha@test.com", dept, managerId, Role.EMPLOYEE);
        assertThat(hrId).isNotEqualTo(empId);
        send("GET", "/api/v1/designations", admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(designation));

        // 2. The new employee logs in (and only then has a landing summary) and applies for two days of leave.
        String emp = login("asha@test.com");
        String mgr = login("mgr@test.com");
        String hr = login("hr@test.com");
        long leaveId = id(send("POST", "/api/v1/leave-requests", emp, "{\"leaveTypeId\":" + leaveType
                + ",\"startDate\":\"" + leaveMonday + "\",\"endDate\":\"" + leaveMonday.plusDays(1) + "\",\"reason\":\"Family\"}"));

        // 3. The manager is notified, approves; the balance moves and the employee is notified.
        assertThat(unread(mgr)).isEqualTo(1);
        assertThat(unread(emp)).isZero();
        send("PUT", "/api/v1/leave-requests/" + leaveId + "/approve", mgr, "{\"comment\":\"Enjoy\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        send("GET", "/api/v1/leave-balances/" + empId + "?year=" + leaveYear, emp, null).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].usedDays").value(2.0)).andExpect(jsonPath("$[0].availableDays").value(16.0));
        assertThat(unread(emp)).isEqualTo(1);
        send("GET", "/api/v1/notifications", emp, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("LEAVE_APPROVED"));

        // 4. Attendance: check in and out today, and a correction for yesterday that the manager approves.
        clock.set(TODAY.atTime(9, 5));
        send("POST", "/api/v1/attendance/check-in", emp, null).andExpect(status().isOk());
        clock.set(TODAY.atTime(17, 45));
        send("POST", "/api/v1/attendance/check-out", emp, null).andExpect(status().isOk());
        send("GET", "/api/v1/attendance/" + empId + "?from=" + TODAY + "&to=" + TODAY, emp, null).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PRESENT"));

        LocalDate yesterday = TODAY.minusDays(1);
        long correction = id(send("POST", "/api/v1/attendance-corrections", emp, "{\"attendanceDate\":\"" + yesterday
                + "\",\"requestedCheckIn\":\"" + yesterday + "T09:00:00\",\"requestedCheckOut\":\"" + yesterday
                + "T17:30:00\",\"reason\":\"Forgot to punch\"}"));
        send("PUT", "/api/v1/attendance-corrections/" + correction + "/approve", mgr, "{\"comment\":\"ok\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        send("GET", "/api/v1/attendance/" + empId + "?from=" + yesterday + "&to=" + yesterday, emp, null)
                .andExpect(jsonPath("$[0].status").value("PRESENT")).andExpect(jsonPath("$[0].workingHours").value(8.5));

        // 5. Document: the employee uploads, HR sees it, and it shows in the expiring report once expiry is close.
        upload(emp, empId, category, TODAY.plusDays(10).toString()).andExpect(status().isCreated());
        send("GET", "/api/v1/employees/" + empId + "/documents", hr, null).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fileName").value("passport.pdf"));
        send("GET", "/api/v1/documents/expiring", hr, null).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employeeName").value("Asha Employee"));

        // 6. HR opens the analytics and exports a CSV.
        send("GET", "/api/v1/reports/headcount", hr, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3));
        var csv = send("GET", "/api/v1/reports/headcount/export", hr, null).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(csv.getContentType()).startsWith("text/csv");
        assertThat(csv.getContentAsString(StandardCharsets.UTF_8)).contains("Engineering");

        // 7. Admin offboards the employee: the login stops working, the history stays.
        send("PUT", "/api/v1/employees/" + empId + "/offboard", admin, null).andExpect(status().isOk());
        send("GET", "/api/v1/notifications/unread-count", emp, null).andExpect(status().isUnauthorized());   // old token is dead
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"asha@test.com\",\"password\":\"" + PASSWORD + "\"}")).andExpect(status().isUnauthorized());

        send("GET", "/api/v1/employees/" + empId + "/documents", hr, null).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fileName").value("passport.pdf"));                 // documents stay viewable
        send("GET", "/api/v1/leave-requests?scope=ALL&size=50", hr, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id==" + leaveId + ")].status").value("APPROVED"));
        send("GET", "/api/v1/attendance/" + empId + "?from=" + yesterday + "&to=" + TODAY, hr, null).andExpect(status().isOk())
                .andExpect(jsonPath("$[1].status").value("PRESENT"));
        send("GET", "/api/v1/reports/headcount", hr, null).andExpect(jsonPath("$.total").value(2));   // no longer counted as staff
    }

    // ------------------------------------------------------------------ cross-module consistency

    @Test
    void offboardedEmployeeCannotRequestLeaveOrCheckInEvenWithAnOldToken() throws Exception {
        long dept = id(send("POST", "/api/v1/departments", admin, "{\"name\":\"Ops\"}"));
        long leaveType = id(send("POST", "/api/v1/leave-types", admin, "{\"name\":\"Casual\",\"annualEntitlementDays\":8}"));
        long empId = onboard("Omar Ops", "omar@test.com", dept, null, Role.EMPLOYEE);
        String emp = login("omar@test.com");

        send("PUT", "/api/v1/employees/" + empId + "/offboard", admin, null).andExpect(status().isOk());

        send("POST", "/api/v1/leave-requests", emp, "{\"leaveTypeId\":" + leaveType + ",\"startDate\":\"" + leaveMonday
                + "\",\"endDate\":\"" + leaveMonday + "\"}").andExpect(status().isUnauthorized());
        send("POST", "/api/v1/attendance/check-in", emp, null).andExpect(status().isUnauthorized());
        send("POST", "/api/v1/attendance-corrections", emp, "{\"attendanceDate\":\"" + TODAY.minusDays(1)
                + "\",\"requestedCheckIn\":\"" + TODAY.minusDays(1) + "T09:00:00\",\"reason\":\"x\"}").andExpect(status().isUnauthorized());
        // and the record can no longer be edited or reassigned
        send("PUT", "/api/v1/employees/" + empId + "/reassign", admin, "{\"departmentId\":" + dept + "}").andExpect(status().isConflict());
    }

    @Test
    void departmentCannotBeDeletedWhileEmployeesOrDesignationsReferenceIt() throws Exception {
        long withEmployee = id(send("POST", "/api/v1/departments", admin, "{\"name\":\"Staffed\"}"));
        long withDesignation = id(send("POST", "/api/v1/departments", admin, "{\"name\":\"Titled\"}"));
        long empty = id(send("POST", "/api/v1/departments", admin, "{\"name\":\"Empty\"}"));
        long empId = onboard("Eve Staff", "eve@test.com", withEmployee, null, Role.EMPLOYEE);
        long designation = id(send("POST", "/api/v1/designations", admin,
                "{\"title\":\"Analyst\",\"departmentId\":" + withDesignation + "}"));

        send("DELETE", "/api/v1/departments/" + withEmployee, admin, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("active employee")));
        send("DELETE", "/api/v1/departments/" + withDesignation, admin, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("active designation")));
        send("DELETE", "/api/v1/departments/" + empty, admin, null).andExpect(status().isNoContent());

        // once the blockers are gone the delete goes through
        send("PUT", "/api/v1/employees/" + empId + "/offboard", admin, null).andExpect(status().isOk());
        send("DELETE", "/api/v1/departments/" + withEmployee, admin, null).andExpect(status().isNoContent());
        send("PUT", "/api/v1/designations/" + designation, admin,
                "{\"title\":\"Analyst\",\"departmentId\":" + withDesignation + ",\"active\":false}").andExpect(status().isOk());
        send("DELETE", "/api/v1/departments/" + withDesignation, admin, null).andExpect(status().isNoContent());
    }

    @Test
    void managerReassignmentImmediatelyMovesTheTeamLeaveAndAttendanceViews() throws Exception {
        long dept = id(send("POST", "/api/v1/departments", admin, "{\"name\":\"Eng\"}"));
        long leaveType = id(send("POST", "/api/v1/leave-types", admin, "{\"name\":\"Annual\",\"annualEntitlementDays\":18}"));
        long mgrA = onboard("Mia Alpha", "mia@test.com", dept, null, Role.MANAGER);
        long mgrB = onboard("Max Beta", "max@test.com", dept, null, Role.MANAGER);
        long empId = onboard("Tom Team", "tom@test.com", dept, mgrA, Role.EMPLOYEE);
        String a = login("mia@test.com");
        String b = login("max@test.com");
        String emp = login("tom@test.com");

        send("POST", "/api/v1/leave-requests", emp, "{\"leaveTypeId\":" + leaveType + ",\"startDate\":\"" + leaveMonday
                + "\",\"endDate\":\"" + leaveMonday + "\"}").andExpect(status().isCreated());

        // before: only manager A sees Tom
        send("GET", "/api/v1/employees/" + mgrA + "/team", a, null).andExpect(jsonPath("$.totalElements").value(1));
        send("GET", "/api/v1/employees/" + mgrB + "/team", b, null).andExpect(jsonPath("$.totalElements").value(0));
        send("GET", "/api/v1/leave-requests?scope=TEAM", a, null).andExpect(jsonPath("$.totalElements").value(1));
        send("GET", "/api/v1/leave-requests?scope=TEAM", b, null).andExpect(jsonPath("$.totalElements").value(0));
        send("GET", "/api/v1/attendance/summary?from=" + TODAY + "&to=" + TODAY + "&groupBy=EMPLOYEE", a, null)
                .andExpect(jsonPath("$.rows.length()").value(1));
        send("GET", "/api/v1/attendance/summary?from=" + TODAY + "&to=" + TODAY + "&groupBy=EMPLOYEE", b, null)
                .andExpect(jsonPath("$.rows.length()").value(0));

        send("PUT", "/api/v1/employees/" + empId + "/reassign", admin, "{\"managerId\":" + mgrB + "}").andExpect(status().isOk());

        // after: the very next request shows Tom under manager B, and manager A has lost sight of him
        send("GET", "/api/v1/employees/" + mgrA + "/team", a, null).andExpect(jsonPath("$.totalElements").value(0));
        send("GET", "/api/v1/employees/" + mgrB + "/team", b, null).andExpect(jsonPath("$.totalElements").value(1));
        send("GET", "/api/v1/leave-requests?scope=TEAM", a, null).andExpect(jsonPath("$.totalElements").value(0));
        send("GET", "/api/v1/leave-requests?scope=TEAM", b, null).andExpect(jsonPath("$.totalElements").value(1));
        send("GET", "/api/v1/attendance/summary?from=" + TODAY + "&to=" + TODAY + "&groupBy=EMPLOYEE", a, null)
                .andExpect(jsonPath("$.rows.length()").value(0));
        send("GET", "/api/v1/attendance/summary?from=" + TODAY + "&to=" + TODAY + "&groupBy=EMPLOYEE", b, null)
                .andExpect(jsonPath("$.rows.length()").value(1));
    }

    @Test
    void everyRoleRestrictedEndpointRejectsTheWrongRole() throws Exception {
        long dept = id(send("POST", "/api/v1/departments", admin, "{\"name\":\"Eng\"}"));
        onboard("Mia Mgr", "mia@test.com", dept, null, Role.MANAGER);
        onboard("Tom Team", "tom@test.com", dept, null, Role.EMPLOYEE);
        String mgr = login("mia@test.com");
        String emp = login("tom@test.com");

        for (String url : new String[] { "/api/v1/reports/headcount", "/api/v1/users", "/api/v1/documents/expiring" }) {
            send("GET", url, emp, null).andExpect(status().isForbidden());
            send("GET", url, mgr, null).andExpect(status().isForbidden());
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
        }
        send("GET", "/api/v1/employees", emp, null).andExpect(status().isForbidden());
        send("GET", "/api/v1/attendance/summary?from=" + TODAY + "&to=" + TODAY + "&groupBy=EMPLOYEE", emp, null)
                .andExpect(status().isForbidden());
        send("POST", "/api/v1/departments", mgr, "{\"name\":\"Nope\"}").andExpect(status().isForbidden());
    }
}
