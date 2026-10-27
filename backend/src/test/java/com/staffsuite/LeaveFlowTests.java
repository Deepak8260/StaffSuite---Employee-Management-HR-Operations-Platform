package com.staffsuite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.staffsuite.entity.Department;
import com.staffsuite.entity.Employee;
import com.staffsuite.entity.EmploymentStatus;
import com.staffsuite.entity.LeaveBalance;
import com.staffsuite.entity.LeaveRequest;
import com.staffsuite.entity.LeaveStatus;
import com.staffsuite.entity.LeaveType;
import com.staffsuite.entity.Role;
import com.staffsuite.entity.User;
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
class LeaveFlowTests {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired EmployeeRepository employees;
    @Autowired DepartmentRepository departments;
    @Autowired LeaveTypeRepository leaveTypes;
    @Autowired LeaveBalanceRepository balances;
    @Autowired LeaveRequestRepository requests;
    @Autowired PublicHolidayRepository holidays;
    @Autowired AuditLogRepository audits;
    @Autowired PasswordEncoder encoder;

    // next year keeps every test date in the future and inside a single calendar year
    final int year = LocalDate.now().getYear() + 1;
    final LocalDate monday = LocalDate.of(year, 3, 1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));

    Department dept;
    LeaveType annual;
    LeaveType sick;
    Employee hrEmp;
    Employee mgrEmp;
    Employee empA;   // reports to mgrEmp
    Employee empB;   // no manager

    @BeforeEach
    void seed() {
        wipe();
        dept = new Department();
        dept.setName("Engineering");
        dept = departments.save(dept);

        annual = leaveType("Annual", "10", true, "5");
        sick = leaveType("Sick", "5", false, "0");

        hrEmp = employee("Hana HR", "hana@test.com", null);
        mgrEmp = employee("Mark Manager", "mark@test.com", null);
        empA = employee("Asha Employee", "asha@test.com", mgrEmp);
        empB = employee("Bela Employee", "bela@test.com", null);

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
        requests.deleteAllInBatch();
        balances.deleteAllInBatch();
        leaveTypes.deleteAllInBatch();
        holidays.deleteAllInBatch();
        audits.deleteAllInBatch();
        users.deleteAllInBatch();
        employees.deleteAllInBatch();
        departments.deleteAllInBatch();
        jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }

    // ------------------------------------------------------------------ helpers

    private LeaveType leaveType(String name, String days, boolean carry, String maxCarry) {
        LeaveType t = new LeaveType();
        t.setName(name);
        t.setAnnualEntitlementDays(new BigDecimal(days));
        t.setCarryForwardAllowed(carry);
        t.setMaxCarryForwardDays(new BigDecimal(maxCarry));
        return leaveTypes.save(t);
    }

    private Employee employee(String name, String email, Employee manager) {
        Employee e = new Employee();
        e.setEmployeeCode("EMP-T" + Math.abs(email.hashCode()));
        e.setFullName(name);
        e.setEmail(email);
        e.setJoiningDate(LocalDate.now().minusYears(2));
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
            case "DELETE" -> delete(url);
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

    private String leaveJson(LeaveType type, LocalDate from, LocalDate to) {
        return "{\"leaveTypeId\":" + type.getId() + ",\"startDate\":\"" + from + "\",\"endDate\":\"" + to
                + "\",\"reason\":\"Trip\"}";
    }

    /** Applies and returns the new request id. */
    private long apply(String token, LeaveType type, LocalDate from, LocalDate to) throws Exception {
        return body(send("POST", "/api/v1/leave-requests", token, leaveJson(type, from, to))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private JsonNode balanceOf(String token, Employee e, LeaveType type) throws Exception {
        JsonNode all = body(send("GET", "/api/v1/leave-balances/" + e.getId() + "?year=" + year, token, null)
                .andExpect(status().isOk()));
        for (JsonNode b : all) {
            if (b.get("leaveTypeId").asLong() == type.getId()) {
                return b;
            }
        }
        throw new AssertionError("no balance for " + type.getName());
    }

    private void holiday(LocalDate date, boolean optional) throws Exception {
        send("POST", "/api/v1/public-holidays", bearer("hr@test.com"),
                "{\"name\":\"Day off\",\"holidayDate\":\"" + date + "\",\"optional\":" + optional + "}")
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------- applying

    @Test
    void totalDaysExcludeWeekendsAndMandatoryHolidaysButNotOptionalOnes() throws Exception {
        holiday(monday.plusDays(2), false);   // Wednesday: public holiday
        holiday(monday.plusDays(3), true);    // Thursday: optional, still counts
        String t = bearer("a@test.com");

        JsonNode created = body(send("POST", "/api/v1/leave-requests", t, leaveJson(annual, monday, monday.plusDays(6)))
                .andExpect(status().isCreated()));
        // Mon, Tue, Thu, Fri = 4 (Wed holiday, Sat/Sun weekend)
        assertThat(created.get("totalDays").decimalValue()).isEqualByComparingTo("4");
        assertThat(created.get("status").asString()).isEqualTo("PENDING");
    }

    @Test
    void cannotApplyBeyondRemainingBalanceIncludingPendingRequests() throws Exception {
        String t = bearer("a@test.com");
        // 10 working days against Sick (5 days)
        send("POST", "/api/v1/leave-requests", t, leaveJson(sick, monday, monday.plusDays(11)))
                .andExpect(status().isBadRequest());

        // Annual has 10 days: two pending weeks use them all, so a third request is refused
        apply(t, annual, monday, monday.plusDays(4));
        apply(t, annual, monday.plusDays(7), monday.plusDays(11));
        MvcResult third = send("POST", "/api/v1/leave-requests", t,
                leaveJson(annual, monday.plusDays(14), monday.plusDays(14))).andExpect(status().isBadRequest()).andReturn();
        assertThat(third.getResponse().getContentAsString()).contains("Insufficient Annual balance");

        JsonNode b = balanceOf(t, empA, annual);
        assertThat(b.get("pendingDays").decimalValue()).isEqualByComparingTo("10");
        assertThat(b.get("availableDays").decimalValue()).isEqualByComparingTo("0");
        assertThat(b.get("remainingDays").decimalValue()).isEqualByComparingTo("10");   // nothing approved yet
    }

    @Test
    void overlappingAndInvalidDateRangesAreRejected() throws Exception {
        String t = bearer("a@test.com");
        long first = apply(t, annual, monday, monday.plusDays(2));

        send("POST", "/api/v1/leave-requests", t, leaveJson(annual, monday.plusDays(1), monday.plusDays(3)))
                .andExpect(status().isConflict());
        send("POST", "/api/v1/leave-requests", t, leaveJson(sick, monday.plusDays(2), monday.plusDays(2)))
                .andExpect(status().isConflict());   // overlap is per employee, not per leave type

        // once the first is cancelled the dates are free again
        send("PUT", "/api/v1/leave-requests/" + first + "/cancel", t, null).andExpect(status().isOk());
        send("POST", "/api/v1/leave-requests", t, leaveJson(annual, monday.plusDays(1), monday.plusDays(3)))
                .andExpect(status().isCreated());

        LocalDate saturday = monday.plusDays(12);
        send("POST", "/api/v1/leave-requests", t, leaveJson(annual, saturday, saturday.plusDays(1)))
                .andExpect(status().isBadRequest());   // weekend only
        holiday(monday.plusDays(21), false);
        send("POST", "/api/v1/leave-requests", t, leaveJson(annual, monday.plusDays(21), monday.plusDays(21)))
                .andExpect(status().isBadRequest());   // holiday only
        send("POST", "/api/v1/leave-requests", t, leaveJson(annual, monday.plusDays(8), monday.plusDays(7)))
                .andExpect(status().isBadRequest());   // end before start
        send("POST", "/api/v1/leave-requests", t,
                leaveJson(annual, LocalDate.of(year, 12, 30), LocalDate.of(year + 1, 1, 2)))
                .andExpect(status().isBadRequest());   // crosses years
        send("POST", "/api/v1/leave-requests", t, "{\"leaveTypeId\":99999,\"startDate\":\"" + monday
                + "\",\"endDate\":\"" + monday + "\"}").andExpect(status().isBadRequest());
    }

    @Test
    void userWithoutEmployeeRecordCannotApply() throws Exception {
        send("POST", "/api/v1/leave-requests", bearer("admin@test.com"), leaveJson(annual, monday, monday))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------- approve / reject / cancel

    @Test
    void approvingDeductsBalanceAndCancellingBeforeStartRestoresIt() throws Exception {
        String emp = bearer("a@test.com");
        String mgr = bearer("mgr@test.com");
        long id = apply(emp, annual, monday, monday.plusDays(4));   // 5 working days

        send("PUT", "/api/v1/leave-requests/" + id + "/approve", mgr, "{\"comment\":\"Enjoy\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approverName").value("Mark Manager"))
                .andExpect(jsonPath("$.approverComment").value("Enjoy"));
        JsonNode afterApprove = balanceOf(emp, empA, annual);
        assertThat(afterApprove.get("usedDays").decimalValue()).isEqualByComparingTo("5");
        assertThat(afterApprove.get("remainingDays").decimalValue()).isEqualByComparingTo("5");

        // already decided
        send("PUT", "/api/v1/leave-requests/" + id + "/approve", mgr, null).andExpect(status().isConflict());
        send("PUT", "/api/v1/leave-requests/" + id + "/reject", mgr, "{\"comment\":\"x\"}").andExpect(status().isConflict());

        // approved but not yet started -> cancellable, and the days come back
        send("PUT", "/api/v1/leave-requests/" + id + "/cancel", emp, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        JsonNode afterCancel = balanceOf(emp, empA, annual);
        assertThat(afterCancel.get("usedDays").decimalValue()).isEqualByComparingTo("0");
        assertThat(afterCancel.get("remainingDays").decimalValue()).isEqualByComparingTo("10");
        send("PUT", "/api/v1/leave-requests/" + id + "/cancel", emp, null).andExpect(status().isConflict());
    }

    @Test
    void cancellingAPendingRequestLeavesTheBalanceUntouched() throws Exception {
        String emp = bearer("a@test.com");
        long id = apply(emp, annual, monday, monday.plusDays(1));
        send("PUT", "/api/v1/leave-requests/" + id + "/cancel", emp, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        JsonNode b = balanceOf(emp, empA, annual);
        assertThat(b.get("usedDays").decimalValue()).isEqualByComparingTo("0");
        assertThat(b.get("pendingDays").decimalValue()).isEqualByComparingTo("0");
        // only the owner can cancel
        long other = apply(emp, annual, monday.plusDays(7), monday.plusDays(8));
        send("PUT", "/api/v1/leave-requests/" + other + "/cancel", bearer("b@test.com"), null)
                .andExpect(status().isForbidden());
        send("PUT", "/api/v1/leave-requests/" + other + "/cancel", bearer("hr@test.com"), null)
                .andExpect(status().isForbidden());
    }

    @Test
    void anApprovedLeaveThatHasAlreadyStartedCannotBeCancelled() throws Exception {
        LeaveRequest started = new LeaveRequest();
        started.setEmployee(empA);
        started.setLeaveType(annual);
        started.setStartDate(LocalDate.now().minusDays(1));
        started.setEndDate(LocalDate.now().plusDays(1));
        started.setTotalDays(new BigDecimal("3"));
        started.setStatus(LeaveStatus.APPROVED);
        started = requests.save(started);

        send("PUT", "/api/v1/leave-requests/" + started.getId() + "/cancel", bearer("a@test.com"), null)
                .andExpect(status().isConflict());
    }

    @Test
    void nobodyCanApproveOrRejectTheirOwnLeave() throws Exception {
        String hr = bearer("hr@test.com");
        String mgr = bearer("mgr@test.com");

        // HR applies, then tries to decide their own request
        long hrReq = apply(hr, annual, monday, monday);
        MvcResult approveSelf = send("PUT", "/api/v1/leave-requests/" + hrReq + "/approve", hr, null)
                .andExpect(status().isForbidden()).andReturn();
        assertThat(approveSelf.getResponse().getContentAsString()).contains("own leave request");
        send("PUT", "/api/v1/leave-requests/" + hrReq + "/reject", hr, "{\"comment\":\"no\"}")
                .andExpect(status().isForbidden());
        assertThat(requests.findById(hrReq).orElseThrow().getStatus()).isEqualTo(LeaveStatus.PENDING);
        assertThat(balanceOf(hr, hrEmp, annual).get("usedDays").decimalValue()).isEqualByComparingTo("0");

        // same for a manager
        long mgrReq = apply(mgr, annual, monday, monday);
        send("PUT", "/api/v1/leave-requests/" + mgrReq + "/approve", mgr, null).andExpect(status().isForbidden());

        // but someone else can: admin decides HR's, HR decides the manager's
        send("PUT", "/api/v1/leave-requests/" + hrReq + "/approve", bearer("admin@test.com"), null)
                .andExpect(status().isOk());
        send("PUT", "/api/v1/leave-requests/" + mgrReq + "/approve", hr, null).andExpect(status().isOk());
    }

    @Test
    void onlyTheDirectManagerOrHrCanDecideAndEmployeesCannotAtAll() throws Exception {
        long reqA = apply(bearer("a@test.com"), annual, monday, monday);
        long reqB = apply(bearer("b@test.com"), annual, monday, monday);   // Bela has no manager

        send("PUT", "/api/v1/leave-requests/" + reqA + "/approve", bearer("b@test.com"), null)
                .andExpect(status().isForbidden());                         // employee role
        send("PUT", "/api/v1/leave-requests/" + reqB + "/approve", bearer("mgr@test.com"), null)
                .andExpect(status().isForbidden());                         // not their report
        send("PUT", "/api/v1/leave-requests/" + reqA + "/approve", bearer("mgr@test.com"), null)
                .andExpect(status().isOk());
        send("PUT", "/api/v1/leave-requests/" + reqB + "/approve", bearer("hr@test.com"), null)
                .andExpect(status().isOk());
        send("PUT", "/api/v1/leave-requests/99999/approve", bearer("hr@test.com"), null)
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectingNeedsAReasonAndLeavesTheBalanceAlone() throws Exception {
        String emp = bearer("a@test.com");
        String mgr = bearer("mgr@test.com");
        long id = apply(emp, annual, monday, monday.plusDays(1));

        send("PUT", "/api/v1/leave-requests/" + id + "/reject", mgr, null).andExpect(status().isBadRequest());
        send("PUT", "/api/v1/leave-requests/" + id + "/reject", mgr, "{\"comment\":\"  \"}").andExpect(status().isBadRequest());
        send("PUT", "/api/v1/leave-requests/" + id + "/reject", mgr, "{\"comment\":\"Release week\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.approverComment").value("Release week"));

        JsonNode b = balanceOf(emp, empA, annual);
        assertThat(b.get("usedDays").decimalValue()).isEqualByComparingTo("0");
        assertThat(b.get("pendingDays").decimalValue()).isEqualByComparingTo("0");   // rejected days are freed
        send("PUT", "/api/v1/leave-requests/" + id + "/cancel", emp, null).andExpect(status().isConflict());
    }

    // ---------------------------------------------------------- visibility

    @Test
    void leaveRequestVisibilityIsScopedByRole() throws Exception {
        apply(bearer("a@test.com"), annual, monday, monday);
        apply(bearer("b@test.com"), annual, monday, monday);
        apply(bearer("mgr@test.com"), annual, monday, monday);
        apply(bearer("hr@test.com"), annual, monday, monday);

        // employee: own only; cannot widen the scope
        String emp = bearer("a@test.com");
        send("GET", "/api/v1/leave-requests", emp, null).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].employeeName").value("Asha Employee"));
        send("GET", "/api/v1/leave-requests?scope=ALL", emp, null).andExpect(status().isForbidden());
        send("GET", "/api/v1/leave-requests?scope=TEAM", emp, null).andExpect(status().isForbidden());
        send("GET", "/api/v1/leave-requests?employeeId=" + empB.getId(), emp, null)
                .andExpect(jsonPath("$.totalElements").value(1));   // employeeId is ignored outside scope=ALL

        // manager: team's requests by default, own via scope=MINE, never everything
        String mgr = bearer("mgr@test.com");
        send("GET", "/api/v1/leave-requests", mgr, null).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].employeeName").value("Asha Employee"));
        send("GET", "/api/v1/leave-requests?scope=MINE", mgr, null).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].employeeName").value("Mark Manager"));
        send("GET", "/api/v1/leave-requests?scope=ALL", mgr, null).andExpect(status().isForbidden());

        // HR and admin: everything, with filters
        for (String who : new String[] { "hr@test.com", "admin@test.com" }) {
            String t = bearer(who);
            send("GET", "/api/v1/leave-requests", t, null).andExpect(jsonPath("$.totalElements").value(4));
            send("GET", "/api/v1/leave-requests?status=APPROVED", t, null).andExpect(jsonPath("$.totalElements").value(0));
            send("GET", "/api/v1/leave-requests?employeeId=" + empB.getId(), t, null)
                    .andExpect(jsonPath("$.totalElements").value(1));
        }
        send("GET", "/api/v1/leave-requests?size=2", bearer("hr@test.com"), null)
                .andExpect(jsonPath("$.content.length()").value(2)).andExpect(jsonPath("$.totalPages").value(2));
        // a user with no employee record has nothing of their own
        send("GET", "/api/v1/leave-requests?scope=MINE", bearer("admin@test.com"), null)
                .andExpect(jsonPath("$.totalElements").value(0));
        send("GET", "/api/v1/leave-requests?status=NOPE", bearer("hr@test.com"), null).andExpect(status().isBadRequest());
        send("GET", "/api/v1/leave-requests", "Bearer nope", null).andExpect(status().isUnauthorized());
    }

    @Test
    void balancesCanOnlyBeReadBySelfDirectManagerOrHr() throws Exception {
        send("GET", "/api/v1/leave-balances/" + empA.getId() + "?year=" + year, bearer("a@test.com"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        send("GET", "/api/v1/leave-balances/" + empA.getId() + "?year=" + year, bearer("mgr@test.com"), null)
                .andExpect(status().isOk());
        send("GET", "/api/v1/leave-balances/" + empA.getId() + "?year=" + year, bearer("hr@test.com"), null)
                .andExpect(status().isOk());
        send("GET", "/api/v1/leave-balances/" + empA.getId(), bearer("b@test.com"), null).andExpect(status().isForbidden());
        send("GET", "/api/v1/leave-balances/" + empB.getId(), bearer("mgr@test.com"), null).andExpect(status().isForbidden());
        send("GET", "/api/v1/leave-balances/99999", bearer("hr@test.com"), null).andExpect(status().isNotFound());
    }

    // ----------------------------------------------------- balances / carry-forward

    @Test
    void carryForwardIsCappedAtMaxCarryForwardDaysAndOnlyWhenAllowed() throws Exception {
        balance(empA, annual, year - 1, "20", "8");   // remaining 12, cap 5  -> carry 5
        balance(empB, annual, year - 1, "10", "7");   // remaining 3,  cap 5  -> carry 3
        balance(empA, sick, year - 1, "5", "1");      // remaining 4, carry not allowed -> 0

        MvcResult res = send("POST", "/api/v1/leave-balances/initialize?year=" + year, bearer("hr@test.com"), null)
                .andExpect(status().isOk()).andReturn();
        assertThat(json.readTree(res.getResponse().getContentAsString()).get("created").asInt()).isEqualTo(8);   // 4 employees x 2 types

        LeaveBalance aAnnual = balances.findByEmployeeIdAndLeaveTypeIdAndYear(empA.getId(), annual.getId(), year).orElseThrow();
        assertThat(aAnnual.getCarriedForwardDays()).isEqualByComparingTo("5");
        assertThat(aAnnual.getAllocatedDays()).isEqualByComparingTo("15");
        assertThat(aAnnual.getRemainingDays()).isEqualByComparingTo("15");

        LeaveBalance bAnnual = balances.findByEmployeeIdAndLeaveTypeIdAndYear(empB.getId(), annual.getId(), year).orElseThrow();
        assertThat(bAnnual.getCarriedForwardDays()).isEqualByComparingTo("3");
        assertThat(bAnnual.getAllocatedDays()).isEqualByComparingTo("13");

        LeaveBalance aSick = balances.findByEmployeeIdAndLeaveTypeIdAndYear(empA.getId(), sick.getId(), year).orElseThrow();
        assertThat(aSick.getCarriedForwardDays()).isEqualByComparingTo("0");
        assertThat(aSick.getAllocatedDays()).isEqualByComparingTo("5");

        // idempotent, and restricted to HR/Admin
        MvcResult again = send("POST", "/api/v1/leave-balances/initialize?year=" + year, bearer("admin@test.com"), null)
                .andReturn();
        assertThat(json.readTree(again.getResponse().getContentAsString()).get("created").asInt()).isZero();
        send("POST", "/api/v1/leave-balances/initialize", bearer("a@test.com"), null).andExpect(status().isForbidden());
        send("POST", "/api/v1/leave-balances/initialize", bearer("mgr@test.com"), null).andExpect(status().isForbidden());
    }

    private void balance(Employee e, LeaveType t, int forYear, String allocated, String used) {
        LeaveBalance b = new LeaveBalance();
        b.setEmployeeId(e.getId());
        b.setLeaveTypeId(t.getId());
        b.setYear(forYear);
        b.setAllocatedDays(new BigDecimal(allocated));
        b.setUsedDays(new BigDecimal(used));
        balances.save(b);
    }

    @Test
    void newEmployeesGetBalancesAndNewLeaveTypesReachExistingEmployees() throws Exception {
        String admin = bearer("admin@test.com");
        int thisYear = LocalDate.now().getYear();

        JsonNode created = body(send("POST", "/api/v1/employees", admin,
                "{\"fullName\":\"New Joiner\",\"email\":\"new@test.com\",\"joiningDate\":\"" + LocalDate.now().minusDays(3)
                        + "\",\"jobTitle\":\"Dev\",\"employmentStatus\":\"PROBATION\",\"departmentId\":" + dept.getId() + "}")
                .andExpect(status().isCreated()));
        long newId = created.get("id").asLong();
        assertThat(balances.findByEmployeeIdAndYearOrderByLeaveTypeIdAsc(newId, thisYear)).hasSize(2);

        send("POST", "/api/v1/leave-types", admin,
                "{\"name\":\"Study\",\"annualEntitlementDays\":3,\"carryForwardAllowed\":false}")
                .andExpect(status().isCreated());
        assertThat(balances.findByEmployeeIdAndYearOrderByLeaveTypeIdAsc(newId, thisYear)).hasSize(3);
        assertThat(balances.findByEmployeeIdAndYearOrderByLeaveTypeIdAsc(empA.getId(), thisYear)).hasSize(1);   // only the new type, others on demand
    }

    // ------------------------------------------------- leave types & holidays

    @Test
    void leaveTypeAndHolidayManagementIsRestrictedToHrAndAdmin() throws Exception {
        String hr = bearer("hr@test.com");
        String emp = bearer("a@test.com");

        send("GET", "/api/v1/leave-types", emp, null).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        send("POST", "/api/v1/leave-types", emp, "{}").andExpect(status().isForbidden());
        send("POST", "/api/v1/public-holidays", emp, "{}").andExpect(status().isForbidden());

        long id = body(send("POST", "/api/v1/leave-types", hr,
                "{\"name\":\"Comp Off\",\"annualEntitlementDays\":4,\"carryForwardAllowed\":false,\"maxCarryForwardDays\":9}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.maxCarryForwardDays").value(0))).get("id").asLong();
        send("POST", "/api/v1/leave-types", hr, "{\"name\":\"comp off\",\"annualEntitlementDays\":1}")
                .andExpect(status().isConflict());
        send("POST", "/api/v1/leave-types", hr, "{\"name\":\"Bad\",\"annualEntitlementDays\":-1}")
                .andExpect(status().isBadRequest());
        send("PUT", "/api/v1/leave-types/" + id, hr,
                "{\"name\":\"Comp Off\",\"annualEntitlementDays\":6,\"carryForwardAllowed\":true,\"maxCarryForwardDays\":2}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.maxCarryForwardDays").value(2));
        send("PUT", "/api/v1/leave-types/" + id, hr,
                "{\"name\":\"Comp Off\",\"annualEntitlementDays\":6,\"active\":false}").andExpect(status().isOk());
        send("GET", "/api/v1/leave-types", emp, null).andExpect(jsonPath("$.length()").value(2));
        send("GET", "/api/v1/leave-types?includeInactive=true", hr, null).andExpect(jsonPath("$.length()").value(3));
        send("GET", "/api/v1/leave-types?includeInactive=true", emp, null).andExpect(jsonPath("$.length()").value(2));

        LocalDate day = LocalDate.of(year, 8, 15);
        long hid = body(send("POST", "/api/v1/public-holidays", hr,
                "{\"name\":\"Independence Day\",\"holidayDate\":\"" + day + "\"}").andExpect(status().isCreated()))
                .get("id").asLong();
        send("POST", "/api/v1/public-holidays", hr, "{\"name\":\"Dup\",\"holidayDate\":\"" + day + "\"}")
                .andExpect(status().isConflict());
        send("GET", "/api/v1/public-holidays?year=" + year, emp, null).andExpect(jsonPath("$.length()").value(1));
        send("GET", "/api/v1/public-holidays?year=" + (year + 1), emp, null).andExpect(jsonPath("$.length()").value(0));
        send("PUT", "/api/v1/public-holidays/" + hid, hr,
                "{\"name\":\"Independence Day\",\"holidayDate\":\"" + day + "\",\"optional\":true}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.optional").value(true));
        send("DELETE", "/api/v1/public-holidays/" + hid, emp, null).andExpect(status().isForbidden());
        send("DELETE", "/api/v1/public-holidays/" + hid, hr, null).andExpect(status().isNoContent());
        send("DELETE", "/api/v1/public-holidays/" + hid, hr, null).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------ attachments

    @Test
    void attachmentsAreValidatedStoredAndOnlyVisibleToThoseWhoMaySeeTheRequest() throws Exception {
        String emp = bearer("a@test.com");
        byte[] pdf = "%PDF-1.4 fake pdf content".getBytes(StandardCharsets.UTF_8);

        String url = json.readTree(mvc.perform(multipart("/api/v1/leave-requests/attachments")
                .file(new MockMultipartFile("file", "note.pdf", "application/pdf", pdf))
                .header("Authorization", emp)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("attachmentUrl").asString();
        assertThat(url).startsWith("leave-attachments/" + empA.getId() + "/").endsWith(".pdf");

        // wrong type (decided by content, not by the name the client gives it) and empty file
        mvc.perform(multipart("/api/v1/leave-requests/attachments")
                .file(new MockMultipartFile("file", "evil.pdf", "application/pdf", "just text".getBytes(StandardCharsets.UTF_8)))
                .header("Authorization", emp)).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/leave-requests/attachments")
                .file(new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]))
                .header("Authorization", emp)).andExpect(status().isBadRequest());

        // cannot attach a path that is not yours or not an upload at all
        String otherUrl = json.readTree(mvc.perform(multipart("/api/v1/leave-requests/attachments")
                .file(new MockMultipartFile("file", "b.pdf", "application/pdf", pdf))
                .header("Authorization", bearer("b@test.com"))).andReturn().getResponse().getContentAsString())
                .get("attachmentUrl").asString();
        send("POST", "/api/v1/leave-requests", emp, withAttachment(monday, otherUrl)).andExpect(status().isBadRequest());
        send("POST", "/api/v1/leave-requests", emp, withAttachment(monday, "../../etc/passwd")).andExpect(status().isBadRequest());

        JsonNode created = body(send("POST", "/api/v1/leave-requests", emp, withAttachment(monday, url))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.hasAttachment").value(true)));
        long id = created.get("id").asLong();
        assertThat(created.toString()).doesNotContain("leave-attachments");   // storage path is never exposed

        for (String allowed : new String[] { "a@test.com", "mgr@test.com", "hr@test.com", "admin@test.com" }) {
            mvc.perform(get("/api/v1/leave-requests/" + id + "/attachment").header("Authorization", bearer(allowed)))
                    .andExpect(status().isOk());
        }
        send("GET", "/api/v1/leave-requests/" + id + "/attachment", bearer("b@test.com"), null)
                .andExpect(status().isForbidden());
        long without = apply(emp, annual, monday.plusDays(7), monday.plusDays(7));
        send("GET", "/api/v1/leave-requests/" + without + "/attachment", emp, null).andExpect(status().isNotFound());
    }

    private String withAttachment(LocalDate day, String url) {
        return "{\"leaveTypeId\":" + annual.getId() + ",\"startDate\":\"" + day + "\",\"endDate\":\"" + day
                + "\",\"attachmentUrl\":\"" + url + "\"}";
    }

    // --------------------------------------------------------------- calendar

    @Test
    void calendarShowsApprovedLeaveAndHolidaysWithinTheCallersScope() throws Exception {
        holiday(monday.plusDays(2), false);
        long idA = apply(bearer("a@test.com"), annual, monday, monday.plusDays(1));
        apply(bearer("a@test.com"), sick, monday.plusDays(7), monday.plusDays(7));   // stays pending: not shown
        long idB = apply(bearer("b@test.com"), annual, monday, monday.plusDays(1));
        send("PUT", "/api/v1/leave-requests/" + idA + "/approve", bearer("mgr@test.com"), null).andExpect(status().isOk());
        send("PUT", "/api/v1/leave-requests/" + idB + "/approve", bearer("hr@test.com"), null).andExpect(status().isOk());

        String range = "?from=" + monday + "&to=" + monday.plusDays(13);
        // employee: only their own approved leave, plus holidays
        JsonNode mine = body(send("GET", "/api/v1/leave-calendar" + range, bearer("a@test.com"), null).andExpect(status().isOk()));
        assertThat(mine.get("leaves")).hasSize(1);
        assertThat(mine.get("leaves").get(0).get("employeeName").asString()).isEqualTo("Asha Employee");
        assertThat(mine.get("holidays")).hasSize(1);
        // another employee sees only theirs
        JsonNode theirs = body(send("GET", "/api/v1/leave-calendar" + range, bearer("b@test.com"), null));
        assertThat(theirs.get("leaves")).hasSize(1);
        assertThat(theirs.get("leaves").get(0).get("employeeName").asString()).isEqualTo("Bela Employee");
        // manager: their team; HR: everyone
        assertThat(body(send("GET", "/api/v1/leave-calendar" + range, bearer("mgr@test.com"), null)).get("leaves")).hasSize(1);
        assertThat(body(send("GET", "/api/v1/leave-calendar" + range, bearer("hr@test.com"), null)).get("leaves")).hasSize(2);
        send("GET", "/api/v1/leave-calendar" + range + "&scope=ALL", bearer("a@test.com"), null).andExpect(status().isForbidden());
        send("GET", "/api/v1/leave-calendar?from=" + monday + "&to=" + monday.plusDays(500), bearer("hr@test.com"), null)
                .andExpect(status().isBadRequest());
        send("GET", "/api/v1/leave-calendar", bearer("hr@test.com"), null).andExpect(status().isBadRequest());
    }
}
