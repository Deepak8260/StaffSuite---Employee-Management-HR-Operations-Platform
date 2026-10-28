package com.staffsuite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.staffsuite.entity.AttendanceCorrection;
import com.staffsuite.entity.Department;
import com.staffsuite.entity.DocumentCategory;
import com.staffsuite.entity.DocumentVisibility;
import com.staffsuite.entity.Employee;
import com.staffsuite.entity.EmployeeDocument;
import com.staffsuite.entity.EmploymentStatus;
import com.staffsuite.entity.LeaveType;
import com.staffsuite.entity.NotificationType;
import com.staffsuite.entity.Role;
import com.staffsuite.entity.User;
import com.staffsuite.repository.AttendanceCorrectionRepository;
import com.staffsuite.repository.AttendanceRecordRepository;
import com.staffsuite.repository.AuditLogRepository;
import com.staffsuite.repository.DepartmentRepository;
import com.staffsuite.repository.DocumentCategoryRepository;
import com.staffsuite.repository.EmployeeDocumentRepository;
import com.staffsuite.repository.EmployeeRepository;
import com.staffsuite.repository.LeaveBalanceRepository;
import com.staffsuite.repository.LeaveRequestRepository;
import com.staffsuite.repository.LeaveTypeRepository;
import com.staffsuite.repository.NotificationRepository;
import com.staffsuite.repository.PublicHolidayRepository;
import com.staffsuite.repository.UserRepository;
import com.staffsuite.service.DocumentReminderService;
import com.staffsuite.service.NotificationService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationFlowTests {

    // next year keeps leave dates in the future and inside one calendar year
    final int year = LocalDate.now().getYear() + 1;
    final LocalDate monday = LocalDate.of(year, 3, 1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));

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
    @Autowired AttendanceRecordRepository attendanceRecords;
    @Autowired AttendanceCorrectionRepository corrections;
    @Autowired DocumentCategoryRepository categories;
    @Autowired EmployeeDocumentRepository documents;
    @Autowired NotificationRepository notifications;
    @Autowired NotificationService notificationService;
    @Autowired DocumentReminderService reminders;
    @Autowired AuditLogRepository audits;
    @Autowired PasswordEncoder encoder;

    Department dept;
    LeaveType annual;
    Employee hrEmp, hr2Emp, mgrEmp, empA, empB, bossEmp, empC;

    @BeforeEach
    void seed() {
        wipe();
        dept = new Department();
        dept.setName("Engineering");
        dept = departments.save(dept);
        annual = new LeaveType();
        annual.setName("Annual");
        annual.setAnnualEntitlementDays(new BigDecimal("18"));
        annual = leaveTypes.save(annual);

        hrEmp = employee("Hana HR", "hana@test.com", null);
        hr2Emp = employee("Hugo HR", "hugo@test.com", null);
        mgrEmp = employee("Mark Manager", "mark@test.com", null);
        empA = employee("Asha Employee", "asha@test.com", mgrEmp);
        empB = employee("Bela Employee", "bela@test.com", null);          // no manager
        bossEmp = employee("Bo Boss", "bo@test.com", null);               // is a manager on paper, but only has an EMPLOYEE login
        empC = employee("Chen Employee", "chen@test.com", bossEmp);

        account("admin@test.com", Role.SYSTEM_ADMIN, null);
        account("hr@test.com", Role.HR_ADMIN, hrEmp);
        account("hr2@test.com", Role.HR_ADMIN, hr2Emp);
        account("mgr@test.com", Role.MANAGER, mgrEmp);
        account("a@test.com", Role.EMPLOYEE, empA);
        account("b@test.com", Role.EMPLOYEE, empB);
        account("boss@test.com", Role.EMPLOYEE, bossEmp);
        account("c@test.com", Role.EMPLOYEE, empC);
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
        audits.deleteAllInBatch();
        users.deleteAllInBatch();
        employees.deleteAllInBatch();
        departments.deleteAllInBatch();
        jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }

    // ------------------------------------------------------------------ helpers

    private Employee employee(String name, String email, Employee manager) {
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

    private User account(String email, Role role, Employee employee) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(encoder.encode("Passw0rd!x"));
        u.setRole(role);
        u.setEmployeeId(employee == null ? null : employee.getId());
        return users.save(u);
    }

    private Long userId(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow().getId();
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

    /** All of a user's notifications, newest first, as the API returns them. */
    private JsonNode inbox(String email) throws Exception {
        return body(send("GET", "/api/v1/notifications?size=100", bearer(email), null).andExpect(status().isOk())).get("content");
    }

    private long unread(String email) throws Exception {
        return body(send("GET", "/api/v1/notifications/unread-count", bearer(email), null).andExpect(status().isOk())).get("count").asLong();
    }

    private List<String> types(JsonNode inbox) {
        List<String> out = new ArrayList<>();
        inbox.forEach(n -> out.add(n.get("type").asString()));
        return out;
    }

    private long applyLeave(String email, LocalDate from, LocalDate to) throws Exception {
        return body(send("POST", "/api/v1/leave-requests", bearer(email),
                "{\"leaveTypeId\":" + annual.getId() + ",\"startDate\":\"" + from + "\",\"endDate\":\"" + to + "\"}")
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private long correction(String email, LocalDate date) throws Exception {
        return body(send("POST", "/api/v1/attendance-corrections", bearer(email),
                "{\"attendanceDate\":\"" + date + "\",\"requestedCheckIn\":\"" + date + "T09:00:00\",\"requestedCheckOut\":\""
                        + date + "T17:00:00\",\"reason\":\"Forgot to punch\"}").andExpect(status().isCreated())).get("id").asLong();
    }

    private LocalDate pastWorkday() {
        return LocalDate.now(clock).minusDays(3);
    }

    private void assertNobodyElseNotified(long total, String... expectedRecipients) throws Exception {
        assertThat(notifications.count()).isEqualTo(total);
        List<String> expected = List.of(expectedRecipients);
        for (String email : new String[] { "admin@test.com", "hr@test.com", "hr2@test.com", "mgr@test.com", "a@test.com",
                "b@test.com", "boss@test.com", "c@test.com" }) {
            assertThat(notifications.countByRecipientIdAndReadFalse(userId(email)))
                    .as("unread for %s", email).isEqualTo(expected.contains(email) ? 1 : 0);
        }
    }

    // ------------------------------------------------------------- leave: submit

    @Test
    void submittingLeaveNotifiesTheApprovingManagerAndNobodyElse() throws Exception {
        long id = applyLeave("a@test.com", monday, monday.plusDays(2));

        JsonNode mgr = inbox("mgr@test.com");
        assertThat(mgr).hasSize(1);
        assertThat(mgr.get(0).get("type").asString()).isEqualTo("LEAVE_REQUESTED");
        assertThat(mgr.get(0).get("referenceId").asLong()).isEqualTo(id);
        assertThat(mgr.get(0).get("read").asBoolean()).isFalse();
        assertThat(mgr.get(0).get("message").asString()).contains("Asha Employee", "Annual", "3 days", monday.toString());
        assertNobodyElseNotified(1, "mgr@test.com");   // not HR, not the admin, and not the requester themselves
        assertThat(unread("mgr@test.com")).isEqualTo(1);
    }

    @Test
    void withNoManagerTheRequestGoesToHrInstead() throws Exception {
        long id = applyLeave("b@test.com", monday, monday);
        assertThat(inbox("hr@test.com").get(0).get("referenceId").asLong()).isEqualTo(id);
        assertThat(inbox("hr2@test.com").get(0).get("type").asString()).isEqualTo("LEAVE_REQUESTED");
        assertThat(inbox("hr@test.com").get(0).get("message").asString()).contains("1 day").doesNotContain("1 days");
        assertThat(notifications.count()).isEqualTo(2);       // exactly one per HR user, none for the system admin
        assertThat(unread("admin@test.com")).isZero();
        assertThat(unread("mgr@test.com")).isZero();
    }

    @Test
    void aManagerWhoseLoginCannotApproveIsSkippedInFavourOfHr() throws Exception {
        applyLeave("c@test.com", monday, monday);   // reports to Bo, whose login is a plain EMPLOYEE
        assertThat(unread("boss@test.com")).isZero();
        assertThat(unread("hr@test.com")).isEqualTo(1);
        assertThat(unread("hr2@test.com")).isEqualTo(1);
    }

    @Test
    void anHrEmployeesOwnRequestOnlyReachesOtherHr() throws Exception {
        applyLeave("hr@test.com", monday, monday);
        assertThat(unread("hr2@test.com")).isEqualTo(1);
        assertThat(unread("hr@test.com")).as("never told about their own request").isZero();
        assertThat(notifications.count()).isEqualTo(1);
    }

    @Test
    void systemAdminsAreTheFallbackWhenThereIsNoActiveHr() throws Exception {
        for (String hr : new String[] { "hr@test.com", "hr2@test.com" }) {
            User u = users.findByEmailIgnoreCase(hr).orElseThrow();
            u.setActive(false);
            users.save(u);
        }
        applyLeave("b@test.com", monday, monday);
        assertThat(unread("admin@test.com")).isEqualTo(1);
        assertThat(notifications.count()).isEqualTo(1);
    }

    // ----------------------------------------------------------- leave: decisions

    @Test
    void approvingOrRejectingLeaveNotifiesTheEmployeeWhoAskedAndNoOneElse() throws Exception {
        long first = applyLeave("a@test.com", monday, monday.plusDays(1));
        long second = applyLeave("a@test.com", monday.plusDays(7), monday.plusDays(7));
        long beforeDecisions = notifications.count();    // two submissions, both to the manager

        send("PUT", "/api/v1/leave-requests/" + first + "/approve", bearer("mgr@test.com"), "{\"comment\":\"Enjoy\"}")
                .andExpect(status().isOk());
        JsonNode afterApprove = inbox("a@test.com");
        assertThat(afterApprove).hasSize(1);
        assertThat(afterApprove.get(0).get("type").asString()).isEqualTo("LEAVE_APPROVED");
        assertThat(afterApprove.get(0).get("referenceId").asLong()).isEqualTo(first);
        assertThat(afterApprove.get(0).get("message").asString()).contains("Annual", "was approved by Mark Manager", "Comment: Enjoy");

        send("PUT", "/api/v1/leave-requests/" + second + "/reject", bearer("mgr@test.com"), "{\"comment\":\"Release week\"}")
                .andExpect(status().isOk());
        JsonNode afterReject = inbox("a@test.com");
        assertThat(types(afterReject)).containsExactly("LEAVE_REJECTED", "LEAVE_APPROVED");   // newest first
        assertThat(afterReject.get(0).get("referenceId").asLong()).isEqualTo(second);
        assertThat(afterReject.get(0).get("message").asString()).contains("was rejected", "Comment: Release week");

        assertThat(notifications.count()).isEqualTo(beforeDecisions + 2);
        assertThat(inbox("mgr@test.com")).hasSize(2);       // the manager is not told about their own decisions
        assertThat(unread("hr@test.com")).isZero();
    }

    @Test
    void anHrApprovalNotifiesTheEmployeeWithNoManager() throws Exception {
        long id = applyLeave("b@test.com", monday, monday);
        send("PUT", "/api/v1/leave-requests/" + id + "/approve", bearer("hr2@test.com"), null).andExpect(status().isOk());
        JsonNode b = inbox("b@test.com");
        assertThat(b).hasSize(1);
        assertThat(b.get(0).get("message").asString()).contains("approved by Hugo HR").doesNotContain("Comment");
    }

    @Test
    void failedOrRepeatedActionsNeverCreateDuplicateNotifications() throws Exception {
        long id = applyLeave("a@test.com", monday, monday);
        assertThat(notifications.count()).isEqualTo(1);

        // refused submissions: overlap, bad dates, over the balance
        send("POST", "/api/v1/leave-requests", bearer("a@test.com"),
                "{\"leaveTypeId\":" + annual.getId() + ",\"startDate\":\"" + monday + "\",\"endDate\":\"" + monday + "\"}")
                .andExpect(status().isConflict());
        send("POST", "/api/v1/leave-requests", bearer("a@test.com"),
                "{\"leaveTypeId\":" + annual.getId() + ",\"startDate\":\"" + monday.plusDays(10) + "\",\"endDate\":\"" + monday.plusDays(3) + "\"}")
                .andExpect(status().isBadRequest());
        send("POST", "/api/v1/leave-requests", bearer("a@test.com"),
                "{\"leaveTypeId\":" + annual.getId() + ",\"startDate\":\"" + monday.plusDays(14) + "\",\"endDate\":\"" + monday.plusDays(60) + "\"}")
                .andExpect(status().isBadRequest());
        // refused decisions: wrong person, employee role, rejection without a reason
        send("PUT", "/api/v1/leave-requests/" + id + "/approve", bearer("a@test.com"), null).andExpect(status().isForbidden());
        send("PUT", "/api/v1/leave-requests/" + id + "/approve", bearer("b@test.com"), null).andExpect(status().isForbidden());
        send("PUT", "/api/v1/leave-requests/" + id + "/reject", bearer("mgr@test.com"), null).andExpect(status().isBadRequest());
        assertThat(notifications.count()).isEqualTo(1);

        send("PUT", "/api/v1/leave-requests/" + id + "/approve", bearer("mgr@test.com"), null).andExpect(status().isOk());
        send("PUT", "/api/v1/leave-requests/" + id + "/approve", bearer("mgr@test.com"), null).andExpect(status().isConflict());
        send("PUT", "/api/v1/leave-requests/" + id + "/reject", bearer("mgr@test.com"), "{\"comment\":\"x\"}").andExpect(status().isConflict());
        send("PUT", "/api/v1/leave-requests/" + id + "/approve", bearer("hr@test.com"), null).andExpect(status().isConflict());

        assertThat(notifications.count()).isEqualTo(2);                         // one when asked, one when decided
        assertThat(types(inbox("a@test.com"))).containsExactly("LEAVE_APPROVED");
        assertThat(types(inbox("mgr@test.com"))).containsExactly("LEAVE_REQUESTED");
    }

    @Test
    void nobodyIsToldAboutTheirOwnDecisionAttemptEvenWhenRefused() throws Exception {
        long id = applyLeave("mgr@test.com", monday, monday);          // the manager has no manager -> HR is told
        long before = notifications.count();
        send("PUT", "/api/v1/leave-requests/" + id + "/approve", bearer("mgr@test.com"), null).andExpect(status().isForbidden());
        assertThat(notifications.count()).isEqualTo(before);
    }

    // ----------------------------------------------------------------- attendance

    @Test
    void attendanceCorrectionsNotifyTheManagerThenTheRequester() throws Exception {
        long id = correction("a@test.com", pastWorkday());
        JsonNode mgr = inbox("mgr@test.com");
        assertThat(mgr).hasSize(1);
        assertThat(mgr.get(0).get("type").asString()).isEqualTo("ATTENDANCE_CORRECTION_REQUESTED");
        assertThat(mgr.get(0).get("referenceId").asLong()).isEqualTo(id);
        assertThat(mgr.get(0).get("message").asString()).contains("Asha Employee", pastWorkday().toString());
        assertNobodyElseNotified(1, "mgr@test.com");

        // a second request for the same day is refused and says nothing to anybody
        send("POST", "/api/v1/attendance-corrections", bearer("a@test.com"),
                "{\"attendanceDate\":\"" + pastWorkday() + "\",\"requestedCheckIn\":\"" + pastWorkday() + "T09:05:00\",\"reason\":\"again\"}")
                .andExpect(status().isConflict());
        assertThat(notifications.count()).isEqualTo(1);

        send("PUT", "/api/v1/attendance-corrections/" + id + "/approve", bearer("mgr@test.com"), "{\"comment\":\"Fine\"}")
                .andExpect(status().isOk());
        JsonNode a = inbox("a@test.com");
        assertThat(a).hasSize(1);
        assertThat(a.get(0).get("type").asString()).isEqualTo("ATTENDANCE_CORRECTION_APPROVED");
        assertThat(a.get(0).get("referenceId").asLong()).isEqualTo(id);
        assertThat(a.get(0).get("message").asString()).contains("was approved by Mark Manager", "Comment: Fine");

        send("PUT", "/api/v1/attendance-corrections/" + id + "/approve", bearer("mgr@test.com"), null).andExpect(status().isConflict());
        assertThat(notifications.count()).isEqualTo(2);
    }

    @Test
    void aCorrectionWithNoManagerGoesToHrAndARejectionNotifiesTheEmployee() throws Exception {
        long id = correction("b@test.com", pastWorkday());
        assertThat(unread("hr@test.com")).isEqualTo(1);
        assertThat(unread("hr2@test.com")).isEqualTo(1);
        assertThat(unread("mgr@test.com")).isZero();

        send("PUT", "/api/v1/attendance-corrections/" + id + "/reject", bearer("hr@test.com"), "{\"comment\":\"No evidence\"}")
                .andExpect(status().isOk());
        JsonNode b = inbox("b@test.com");
        assertThat(b).hasSize(1);
        assertThat(b.get(0).get("type").asString()).isEqualTo("ATTENDANCE_CORRECTION_REJECTED");
        assertThat(b.get(0).get("message").asString()).contains("was rejected by Hana HR", "Comment: No evidence");
        assertThat(notifications.count()).isEqualTo(3);

        // refused correction requests create nothing
        send("POST", "/api/v1/attendance-corrections", bearer("b@test.com"),
                "{\"attendanceDate\":\"" + LocalDate.now(clock).plusDays(2) + "\",\"requestedCheckIn\":\"" + LocalDate.now(clock).plusDays(2)
                        + "T09:00:00\",\"reason\":\"future\"}").andExpect(status().isBadRequest());
        assertThat(notifications.count()).isEqualTo(3);
    }

    // ------------------------------------------------------------------ documents

    private EmployeeDocument document(Employee owner, LocalDate expiry, String name) {
        DocumentCategory category = categories.findAll().stream().findFirst().orElseGet(() -> {
            DocumentCategory c = new DocumentCategory();
            c.setName("ID Proof");
            return categories.save(c);
        });
        EmployeeDocument d = new EmployeeDocument();
        d.setEmployee(owner);
        d.setCategory(category);
        d.setFileName(name);
        d.setFileUrl("employee-documents/" + owner.getId() + "/" + name);
        d.setContentType("application/pdf");
        d.setExpiryDate(expiry);
        d.setVisibility(DocumentVisibility.SELF_AND_HR);
        return documents.save(d);
    }

    @Test
    void hrIsRemindedOnceWhenADocumentEntersTheExpiryWindow() throws Exception {
        LocalDate today = LocalDate.now(clock);
        EmployeeDocument soon = document(empA, today.plusDays(10), "passport.pdf");
        EmployeeDocument tomorrow = document(empB, today.plusDays(1), "visa.pdf");
        document(empA, today.plusDays(45), "later.pdf");              // outside the 30-day window
        document(empA, today.minusDays(1), "expired.pdf");            // already expired: not "entering" the window
        document(empA, null, "never.pdf");
        Employee gone = employee("Gita Gone", "gita@test.com", null);
        gone.setDeleted(true);
        employees.save(gone);
        document(gone, today.plusDays(3), "left.pdf");                // offboarded employees are not chased

        assertThat(reminders.sendExpiryReminders()).isEqualTo(4);     // 2 documents x 2 HR users
        JsonNode hr = inbox("hr@test.com");
        assertThat(hr).hasSize(2);
        assertThat(types(hr)).containsOnly("DOCUMENT_EXPIRING");
        List<Long> refs = new ArrayList<>();
        hr.forEach(n -> refs.add(n.get("referenceId").asLong()));
        assertThat(refs).containsExactlyInAnyOrder(soon.getId(), tomorrow.getId());
        assertThat(hr.toString()).contains("Asha Employee", "passport.pdf", "in 10 days", "tomorrow");
        assertThat(inbox("hr2@test.com")).hasSize(2);
        assertThat(unread("admin@test.com")).isZero();                // HR exists, so the admin is not bothered
        assertThat(unread("mgr@test.com")).isZero();
        assertThat(unread("a@test.com")).isZero();

        // running it again (or every day) never repeats a reminder
        assertThat(reminders.sendExpiryReminders()).isZero();
        assertThat(reminders.sendExpiryReminders()).isZero();
        assertThat(notifications.count()).isEqualTo(4);

        // a new document entering the window is announced; an existing one whose date is merely edited is not
        EmployeeDocument fresh = document(empB, today.plusDays(5), "contract.pdf");
        soon.setExpiryDate(today.plusDays(25));
        documents.save(soon);
        assertThat(reminders.sendExpiryReminders()).isEqualTo(2);
        assertThat(types(inbox("hr@test.com"))).hasSize(3);
        assertThat(inbox("hr@test.com").get(0).get("referenceId").asLong()).isEqualTo(fresh.getId());

        // when a far-off expiry date is brought forward into the window, it is announced for the first time
        EmployeeDocument far = documents.findAll().stream().filter(d -> d.getFileName().equals("later.pdf")).findFirst().orElseThrow();
        far.setExpiryDate(today.plusDays(20));
        documents.save(far);
        assertThat(reminders.sendExpiryReminders()).isEqualTo(2);
    }

    // ------------------------------------------------------------------- the inbox

    @Test
    void userCanOnlyEverSeeAndChangeTheirOwnNotifications() throws Exception {
        Long a = userId("a@test.com");
        Long mgr = userId("mgr@test.com");
        for (int i = 1; i <= 5; i++) {
            notificationService.notify(a, NotificationType.LEAVE_APPROVED, "A note " + i, (long) i);
        }
        notificationService.notify(mgr, NotificationType.LEAVE_REQUESTED, "Manager only", 99L);

        // newest first, own only, paginated
        JsonNode all = inbox("a@test.com");
        assertThat(all).hasSize(5);
        assertThat(all.get(0).get("message").asString()).isEqualTo("A note 5");
        assertThat(all.get(4).get("message").asString()).isEqualTo("A note 1");
        assertThat(all.toString()).doesNotContain("Manager only");
        JsonNode page2 = body(send("GET", "/api/v1/notifications?size=2&page=1", bearer("a@test.com"), null));
        assertThat(page2.get("totalElements").asInt()).isEqualTo(5);
        assertThat(page2.get("totalPages").asInt()).isEqualTo(3);
        assertThat(page2.get("content").get(0).get("message").asString()).isEqualTo("A note 3");
        assertThat(unread("a@test.com")).isEqualTo(5);
        assertThat(unread("mgr@test.com")).isEqualTo(1);

        // marking one read changes exactly that one, and the count follows
        long target = all.get(2).get("id").asLong();
        send("PUT", "/api/v1/notifications/" + target + "/read", bearer("a@test.com"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.read").value(true)).andExpect(jsonPath("$.readAt").exists());
        assertThat(unread("a@test.com")).isEqualTo(4);
        send("PUT", "/api/v1/notifications/" + target + "/read", bearer("a@test.com"), null).andExpect(status().isOk());   // idempotent
        assertThat(unread("a@test.com")).isEqualTo(4);
        JsonNode unreadOnly = body(send("GET", "/api/v1/notifications?unreadOnly=true", bearer("a@test.com"), null));
        assertThat(unreadOnly.get("totalElements").asInt()).isEqualTo(4);
        assertThat(unreadOnly.toString()).doesNotContain("A note 3");

        // someone else's notification is "not found", and is left untouched
        long managersId = inbox("mgr@test.com").get(0).get("id").asLong();
        send("PUT", "/api/v1/notifications/" + managersId + "/read", bearer("a@test.com"), null).andExpect(status().isNotFound());
        send("PUT", "/api/v1/notifications/99999/read", bearer("a@test.com"), null).andExpect(status().isNotFound());
        assertThat(unread("mgr@test.com")).isEqualTo(1);

        // read-all clears only your own
        send("PUT", "/api/v1/notifications/read-all", bearer("a@test.com"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.updated").value(4));
        assertThat(unread("a@test.com")).isZero();
        assertThat(unread("mgr@test.com")).isEqualTo(1);
        send("PUT", "/api/v1/notifications/read-all", bearer("a@test.com"), null).andExpect(jsonPath("$.updated").value(0));
        assertThat(inbox("a@test.com")).hasSize(5);        // read ones stay in the history

        // the unread count always equals the unread items in the list
        long unreadItems = 0;
        for (JsonNode n : inbox("mgr@test.com")) {
            if (!n.get("read").asBoolean()) {
                unreadItems++;
            }
        }
        assertThat(unread("mgr@test.com")).isEqualTo(unreadItems);

        for (String url : new String[] { "/api/v1/notifications", "/api/v1/notifications/unread-count",
                "/api/v1/notifications/pending-approvals" }) {
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
        }
        mvc.perform(put("/api/v1/notifications/read-all")).andExpect(status().isUnauthorized());
    }

    @Test
    void unreadCountMatchesWhatTheUserHasActuallyNotRead() throws Exception {
        applyLeave("a@test.com", monday, monday);
        applyLeave("a@test.com", monday.plusDays(7), monday.plusDays(7));
        applyLeave("a@test.com", monday.plusDays(14), monday.plusDays(14));
        assertThat(unread("mgr@test.com")).isEqualTo(3);

        long one = inbox("mgr@test.com").get(1).get("id").asLong();
        send("PUT", "/api/v1/notifications/" + one + "/read", bearer("mgr@test.com"), null).andExpect(status().isOk());
        assertThat(unread("mgr@test.com")).isEqualTo(2);
        assertThat(notifications.countByRecipientIdAndReadFalse(userId("mgr@test.com"))).isEqualTo(2);

        send("PUT", "/api/v1/notifications/read-all", bearer("mgr@test.com"), null).andExpect(status().isOk());
        assertThat(unread("mgr@test.com")).isZero();
        applyLeave("a@test.com", monday.plusDays(21), monday.plusDays(21));     // a new one arrives after read-all
        assertThat(unread("mgr@test.com")).isEqualTo(1);
    }

    // ------------------------------------------------------- pending approvals

    @Test
    void pendingApprovalsAreComputedPerPersonAndNeverIncludeTheirOwnRequests() throws Exception {
        applyLeave("a@test.com", monday, monday);                    // team of mgr
        applyLeave("b@test.com", monday, monday);                    // no manager
        applyLeave("c@test.com", monday, monday);                    // Bo's report
        applyLeave("hr@test.com", monday, monday);                   // HR's own
        correction("a@test.com", pastWorkday());
        correction("b@test.com", pastWorkday());

        // a manager: their direct reports only
        send("GET", "/api/v1/notifications/pending-approvals", bearer("mgr@test.com"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.leaveRequests").value(1)).andExpect(jsonPath("$.attendanceCorrections").value(1))
                .andExpect(jsonPath("$.total").value(2));
        // HR: everyone's except their own
        send("GET", "/api/v1/notifications/pending-approvals", bearer("hr@test.com"), null)
                .andExpect(jsonPath("$.leaveRequests").value(3)).andExpect(jsonPath("$.attendanceCorrections").value(2))
                .andExpect(jsonPath("$.total").value(5));
        // the admin has no employee record, so every pending request counts
        send("GET", "/api/v1/notifications/pending-approvals", bearer("admin@test.com"), null)
                .andExpect(jsonPath("$.leaveRequests").value(4)).andExpect(jsonPath("$.total").value(6));
        // people who cannot approve anything see nothing
        send("GET", "/api/v1/notifications/pending-approvals", bearer("a@test.com"), null)
                .andExpect(jsonPath("$.total").value(0));
        send("GET", "/api/v1/notifications/pending-approvals", bearer("boss@test.com"), null)
                .andExpect(jsonPath("$.total").value(0));

        // deciding something takes it off the count straight away (it is computed, not stored)
        long id = inbox("mgr@test.com").get(0).get("referenceId").asLong();
        send("PUT", "/api/v1/attendance-corrections/" + id + "/approve", bearer("mgr@test.com"), null).andExpect(status().isOk());
        send("GET", "/api/v1/notifications/pending-approvals", bearer("mgr@test.com"), null)
                .andExpect(jsonPath("$.attendanceCorrections").value(0));
    }

    // ------------------------------------------------------------- out of scope

    @Test
    void thereIsNoEmailDeliveryAnywhere() {
        ClassLoader loader = getClass().getClassLoader();
        assertThat(org.springframework.util.ClassUtils.isPresent("jakarta.mail.Session", loader)).isFalse();
        assertThat(org.springframework.util.ClassUtils.isPresent("org.springframework.mail.javamail.JavaMailSender", loader)).isFalse();
    }
}
