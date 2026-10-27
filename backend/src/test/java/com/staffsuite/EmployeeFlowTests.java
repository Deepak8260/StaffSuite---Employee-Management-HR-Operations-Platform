package com.staffsuite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

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

import com.staffsuite.entity.Department;
import com.staffsuite.entity.Employee;
import com.staffsuite.entity.EmploymentStatus;
import com.staffsuite.entity.Role;
import com.staffsuite.entity.User;
import com.staffsuite.repository.AuditLogRepository;
import com.staffsuite.repository.DepartmentRepository;
import com.staffsuite.repository.EmployeeRepository;
import com.staffsuite.repository.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmployeeFlowTests {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired EmployeeRepository employees;
    @Autowired DepartmentRepository departments;
    @Autowired AuditLogRepository audits;
    @Autowired PasswordEncoder encoder;

    Department engineering;
    Employee managerEmp;
    Employee otherManagerEmp;
    Employee report1;   // reports to managerEmp
    Employee report2;   // reports to otherManagerEmp

    @BeforeEach
    void seed() {
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        audits.deleteAllInBatch();
        users.deleteAllInBatch();
        employees.deleteAllInBatch();
        departments.deleteAllInBatch();
        jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");

        engineering = new Department();
        engineering.setName("Engineering");
        engineering = departments.save(engineering);

        account("admin@test.com", Role.SYSTEM_ADMIN, null);
        account("hr@test.com", Role.HR_ADMIN, null);

        managerEmp = employee("Maya Manager", "maya@test.com", null);
        otherManagerEmp = employee("Omar Other", "omar@test.com", null);
        report1 = employee("Rita Report", "rita@test.com", managerEmp);
        report2 = employee("Ravi Report", "ravi@test.com", otherManagerEmp);

        account("maya@test.com", Role.MANAGER, managerEmp);
        account("rita@test.com", Role.EMPLOYEE, report1);
        account("ravi@test.com", Role.EMPLOYEE, report2);
    }

    // ------------------------------------------------------------------ helpers

    private Employee employee(String name, String email, Employee manager) {
        Employee e = new Employee();
        e.setEmployeeCode("EMP-T" + email.hashCode());
        e.setFullName(name);
        e.setEmail(email);
        e.setJoiningDate(LocalDate.now().minusYears(1));
        e.setJobTitle("Engineer");
        e.setEmploymentStatus(EmploymentStatus.ACTIVE);
        e.setDepartment(engineering);
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
        var req = switch (method) {
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

    private String newEmployeeJson(String email, String joining, String dob, String extra) {
        return "{\"fullName\":\"New Person\",\"email\":\"" + email + "\",\"joiningDate\":\"" + joining + "\","
                + (dob == null ? "" : "\"dateOfBirth\":\"" + dob + "\",")
                + "\"jobTitle\":\"Developer\",\"employmentStatus\":\"PROBATION\",\"departmentId\":"
                + engineering.getId() + (extra == null ? "" : "," + extra) + "}";
    }

    private JsonNode body(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    // -------------------------------------------------------------------- tests

    @Test
    void adminCreatesEmployeesWithUniqueSequentialCodesAndLinkedAccount() throws Exception {
        String t = bearer("admin@test.com");
        String joining = LocalDate.now().minusDays(10).toString();

        JsonNode first = body(send("POST", "/api/v1/employees", t, newEmployeeJson("a@test.com", joining, "1990-05-01",
                "\"userAccount\":{\"password\":\"Welcome#123\",\"role\":\"EMPLOYEE\"}")).andExpect(status().isCreated()));
        JsonNode second = body(send("POST", "/api/v1/employees", t, newEmployeeJson("b@test.com", joining, null, null))
                .andExpect(status().isCreated()));

        assertThat(first.get("employeeCode").asString()).matches("EMP-\\d{4,}");
        assertThat(second.get("employeeCode").asString()).matches("EMP-\\d{4,}")
                .isNotEqualTo(first.get("employeeCode").asString());

        User linked = users.findByEmailIgnoreCase("a@test.com").orElseThrow();
        assertThat(linked.getEmployeeId()).isEqualTo(first.get("id").asLong());
        assertThat(users.findByEmployeeId(second.get("id").asLong())).isEmpty();
        // the new account can log in
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@test.com\",\"password\":\"Welcome#123\"}")).andExpect(status().isOk());
    }

    @Test
    void validationRejectsBadInput() throws Exception {
        String t = bearer("hr@test.com");
        String ok = LocalDate.now().minusDays(10).toString();

        send("POST", "/api/v1/employees", t, newEmployeeJson("f@test.com", LocalDate.now().plusDays(2).toString(), null, null))
                .andExpect(status().isBadRequest());
        send("POST", "/api/v1/employees", t, newEmployeeJson("g@test.com", ok, LocalDate.now().minusYears(5).toString(), null))
                .andExpect(status().isBadRequest());
        send("POST", "/api/v1/employees", t, newEmployeeJson("maya@test.com", ok, null, null))
                .andExpect(status().isConflict());
        send("POST", "/api/v1/employees", t, "{\"fullName\":\"\"}").andExpect(status().isBadRequest());
        send("POST", "/api/v1/employees", t, newEmployeeJson("h@test.com", ok, null, "\"managerId\":99999"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void hrCannotGrantPrivilegedRolesButAdminCan() throws Exception {
        String joining = LocalDate.now().minusDays(10).toString();
        String account = "\"userAccount\":{\"password\":\"Welcome#123\",\"role\":\"SYSTEM_ADMIN\"}";
        send("POST", "/api/v1/employees", bearer("hr@test.com"), newEmployeeJson("p@test.com", joining, null, account))
                .andExpect(status().isForbidden());
        send("POST", "/api/v1/employees", bearer("admin@test.com"), newEmployeeJson("q@test.com", joining, null, account))
                .andExpect(status().isCreated());
    }

    @Test
    void managerSeesOnlyTheirOwnTeam() throws Exception {
        String t = bearer("maya@test.com");

        JsonNode list = body(send("GET", "/api/v1/employees", t, null).andExpect(status().isOk()));
        assertThat(list.get("totalElements").asLong()).isEqualTo(1);
        assertThat(list.get("content").get(0).get("fullName").asString()).isEqualTo("Rita Report");

        // trying to widen the scope with a different manager filter does not leak other teams
        JsonNode widened = body(send("GET", "/api/v1/employees?managerId=" + otherManagerEmp.getId(), t, null));
        assertThat(widened.get("content").get(0).get("fullName").asString()).isEqualTo("Rita Report");

        send("GET", "/api/v1/employees/" + managerEmp.getId() + "/team", t, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        send("GET", "/api/v1/employees/" + otherManagerEmp.getId() + "/team", t, null)
                .andExpect(status().isForbidden());
        send("GET", "/api/v1/employees/" + report1.getId(), t, null).andExpect(status().isOk());
        send("GET", "/api/v1/employees/" + report2.getId(), t, null).andExpect(status().isForbidden());
        send("POST", "/api/v1/employees", t, "{}").andExpect(status().isForbidden());
    }

    @Test
    void regularEmployeeGetsDirectoryOnlyAndOwnProfile() throws Exception {
        String t = bearer("rita@test.com");
        send("GET", "/api/v1/employees", t, null).andExpect(status().isForbidden());
        send("GET", "/api/v1/employees/" + report2.getId(), t, null).andExpect(status().isForbidden());
        send("GET", "/api/v1/employees/" + report1.getId(), t, null).andExpect(status().isOk());

        String dir = send("GET", "/api/v1/employees/directory", t, null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(dir).contains("Ravi Report").doesNotContain("@test.com").doesNotContain("phone")
                .doesNotContain("dateOfBirth");
    }

    @Test
    void selfProfileEditsLimitedFieldsAndRejectsEscalation() throws Exception {
        String t = bearer("rita@test.com");
        String url = "/api/v1/employees/" + report1.getId() + "/profile";

        send("PUT", url, t, "{\"phone\":\"+91 98765 43210\",\"address\":\"12 Main St\",\"emergencyContact\":\"Sam 99999\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.phone").value("+91 98765 43210"));

        send("PUT", url, t, "{\"phone\":\"123456\",\"employmentStatus\":\"TERMINATED\"}")
                .andExpect(status().isBadRequest());
        send("PUT", url, t, "{\"departmentId\":5}").andExpect(status().isBadRequest());
        send("PUT", url, t, "{\"managerId\":" + otherManagerEmp.getId() + "}").andExpect(status().isBadRequest());
        send("PUT", url, t, "{\"salary\":999999}").andExpect(status().isBadRequest());

        Employee reloaded = employees.findById(report1.getId()).orElseThrow();
        assertThat(reloaded.getEmploymentStatus()).isEqualTo(EmploymentStatus.ACTIVE);
        assertThat(reloaded.getManager().getId()).isEqualTo(managerEmp.getId());
        assertThat(reloaded.getPhone()).isEqualTo("+91 98765 43210");

        // someone else's profile
        send("PUT", "/api/v1/employees/" + report2.getId() + "/profile", t, "{\"phone\":\"123456\"}")
                .andExpect(status().isForbidden());
    }

    @Test
    void offboardingSoftDeletesAndDeactivatesAccount() throws Exception {
        String t = bearer("hr@test.com");
        send("PUT", "/api/v1/employees/" + report1.getId() + "/offboard", t, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.employmentStatus").value("TERMINATED"))
                .andExpect(jsonPath("$.deleted").value(true));
        send("PUT", "/api/v1/employees/" + report1.getId() + "/offboard", t, null).andExpect(status().isConflict());

        // gone from active lists and the directory
        String active = send("GET", "/api/v1/employees?size=50", t, null).andReturn().getResponse().getContentAsString();
        assertThat(active).doesNotContain("Rita Report");
        String dir = send("GET", "/api/v1/employees/directory", t, null).andReturn().getResponse().getContentAsString();
        assertThat(dir).doesNotContain("Rita Report");
        // ... but still there for historical queries, and the row is retained
        String all = send("GET", "/api/v1/employees?includeOffboarded=true&size=50", t, null)
                .andReturn().getResponse().getContentAsString();
        assertThat(all).contains("Rita Report");
        assertThat(employees.findById(report1.getId())).isPresent();

        // the manager no longer sees them and the linked login is disabled
        send("GET", "/api/v1/employees/" + managerEmp.getId() + "/team", bearer("maya@test.com"), null)
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"rita@test.com\",\"password\":\"Passw0rd!x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listFiltersAndPaginates() throws Exception {
        String t = bearer("admin@test.com");
        send("GET", "/api/v1/employees?size=2", t, null).andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(4)).andExpect(jsonPath("$.totalPages").value(2));
        send("GET", "/api/v1/employees?managerId=" + managerEmp.getId(), t, null)
                .andExpect(jsonPath("$.totalElements").value(1));
        send("GET", "/api/v1/employees?departmentId=" + engineering.getId() + "&status=ACTIVE", t, null)
                .andExpect(jsonPath("$.totalElements").value(4));
        send("GET", "/api/v1/employees?status=PROBATION", t, null).andExpect(jsonPath("$.totalElements").value(0));
        send("GET", "/api/v1/employees?q=maya", t, null).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void adminUpdateCannotCreateManagerLoop() throws Exception {
        String t = bearer("admin@test.com");
        // managerEmp -> report1 -> would make managerEmp report to report1 (a loop)
        send("PUT", "/api/v1/employees/" + managerEmp.getId(), t,
                newEmployeeJson("maya@test.com", LocalDate.now().minusYears(1).toString(), null,
                        "\"managerId\":" + report1.getId()))
                .andExpect(status().isBadRequest());
        send("PUT", "/api/v1/employees/" + managerEmp.getId(), t,
                newEmployeeJson("maya@test.com", LocalDate.now().minusYears(1).toString(), null, null))
                .andExpect(status().isOk());
    }
}
