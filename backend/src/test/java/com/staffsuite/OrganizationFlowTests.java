package com.staffsuite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.staffsuite.entity.Department;
import com.staffsuite.entity.Employee;
import com.staffsuite.entity.EmploymentStatus;
import com.staffsuite.entity.Role;
import com.staffsuite.entity.User;
import com.staffsuite.repository.AuditLogRepository;
import com.staffsuite.repository.DepartmentRepository;
import com.staffsuite.repository.DesignationRepository;
import com.staffsuite.repository.EmployeeRepository;
import com.staffsuite.repository.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrganizationFlowTests {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired EmployeeRepository employees;
    @Autowired DepartmentRepository departments;
    @Autowired DesignationRepository designations;
    @Autowired AuditLogRepository audits;
    @Autowired PasswordEncoder encoder;

    Department eng;
    Department sales;
    Employee ceo;      // root
    Employee vp;       // reports to ceo
    Employee lead;     // reports to vp
    Employee dev;      // reports to lead

    @BeforeEach
    void seed() {
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        audits.deleteAllInBatch();
        users.deleteAllInBatch();
        employees.deleteAllInBatch();
        designations.deleteAllInBatch();
        departments.deleteAllInBatch();
        jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");

        eng = department("Engineering");
        sales = department("Sales");

        account("admin@test.com", Role.SYSTEM_ADMIN);
        account("hr@test.com", Role.HR_ADMIN);
        account("emp@test.com", Role.EMPLOYEE);

        ceo = employee("Cara CEO", "cara@test.com", eng, null);
        vp = employee("Victor VP", "victor@test.com", eng, ceo);
        lead = employee("Lena Lead", "lena@test.com", eng, vp);
        dev = employee("Dan Dev", "dan@test.com", eng, lead);
    }

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
        e.setJoiningDate(LocalDate.now().minusYears(1));
        e.setJobTitle("Staff");
        e.setEmploymentStatus(EmploymentStatus.ACTIVE);
        e.setDepartment(dept);
        e.setManager(manager);
        return employees.save(e);
    }

    private void account(String email, Role role) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(encoder.encode("Passw0rd!x"));
        u.setRole(role);
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

    // ------------------------------------------------------------- departments

    @Test
    void departmentCrudAndPermissions() throws Exception {
        String hr = bearer("hr@test.com");
        String emp = bearer("emp@test.com");

        // everyone authenticated can read
        send("GET", "/api/v1/departments", emp, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        // only Admin/HR can write
        send("POST", "/api/v1/departments", emp, "{\"name\":\"X\"}").andExpect(status().isForbidden());
        send("POST", "/api/v1/departments", emp, "{}").andExpect(status().isForbidden());

        JsonNode created = body(send("POST", "/api/v1/departments", hr, "{\"name\":\"Legal\",\"description\":\"Contracts\"}")
                .andExpect(status().isCreated()));
        long id = created.get("id").asLong();
        send("POST", "/api/v1/departments", hr, "{\"name\":\"legal\"}").andExpect(status().isConflict());
        send("POST", "/api/v1/departments", hr, "{\"name\":\"\"}").andExpect(status().isBadRequest());

        send("PUT", "/api/v1/departments/" + id, hr, "{\"name\":\"Legal & Compliance\",\"description\":\"x\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Legal & Compliance"));
        send("PUT", "/api/v1/departments/" + id, hr, "{\"name\":\"Sales\"}").andExpect(status().isConflict());
    }

    @Test
    void deletingDepartmentWithActiveEmployeesIsBlockedWithClearMessage() throws Exception {
        String hr = bearer("hr@test.com");

        MvcResult blocked = send("DELETE", "/api/v1/departments/" + eng.getId(), hr, null)
                .andExpect(status().isConflict()).andReturn();
        String message = json.readTree(blocked.getResponse().getContentAsString()).get("message").asString();
        assertThat(message).contains("Engineering").contains("4 active employees").contains("Reassign");
        assertThat(departments.findById(eng.getId()).orElseThrow().isActive()).isTrue();

        // deactivating through update is blocked the same way
        send("PUT", "/api/v1/departments/" + eng.getId(), hr, "{\"name\":\"Engineering\",\"active\":false}")
                .andExpect(status().isConflict());

        // sales is empty, so it can be deleted (soft) and is hidden from the default list
        send("DELETE", "/api/v1/departments/" + sales.getId(), hr, null).andExpect(status().isNoContent());
        send("GET", "/api/v1/departments", hr, null).andExpect(jsonPath("$.length()").value(1));
        send("GET", "/api/v1/departments?includeInactive=true", hr, null).andExpect(jsonPath("$.length()").value(2));
        // regular users never see inactive departments even if they ask
        send("GET", "/api/v1/departments?includeInactive=true", bearer("emp@test.com"), null)
                .andExpect(jsonPath("$.length()").value(1));
        // restore
        send("PUT", "/api/v1/departments/" + sales.getId(), hr, "{\"name\":\"Sales\",\"active\":true}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void departmentOfOffboardedEmployeesOnlyDoesNotBlockDeletionAndKeepsHistory() throws Exception {
        String hr = bearer("hr@test.com");
        Employee leaver = employee("Lee Leaver", "lee@test.com", sales, null);
        send("PUT", "/api/v1/employees/" + leaver.getId() + "/offboard", hr, null).andExpect(status().isOk());

        send("DELETE", "/api/v1/departments/" + sales.getId(), hr, null).andExpect(status().isNoContent());
        // history retained: the offboarded employee still points at the department row
        assertThat(employees.findById(leaver.getId()).orElseThrow().getDepartment().getId()).isEqualTo(sales.getId());
    }

    @Test
    void departmentListReportsActiveEmployeeCounts() throws Exception {
        send("GET", "/api/v1/departments", bearer("hr@test.com"), null)
                .andExpect(jsonPath("$[?(@.name=='Engineering')].employeeCount").value(4))
                .andExpect(jsonPath("$[?(@.name=='Sales')].employeeCount").value(0));
    }

    // ------------------------------------------------------------ designations

    @Test
    void designationCrudLinksToDepartment() throws Exception {
        String hr = bearer("hr@test.com");
        send("POST", "/api/v1/designations", bearer("emp@test.com"), "{\"title\":\"X\"}").andExpect(status().isForbidden());

        JsonNode created = body(send("POST", "/api/v1/designations", hr,
                "{\"title\":\"Staff Engineer\",\"departmentId\":" + eng.getId() + "}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.departmentName").value("Engineering")));
        long id = created.get("id").asLong();

        send("POST", "/api/v1/designations", hr, "{\"title\":\"staff engineer\",\"departmentId\":" + eng.getId() + "}")
                .andExpect(status().isConflict());
        // same title in another department is fine; so is one with no department
        send("POST", "/api/v1/designations", hr, "{\"title\":\"Staff Engineer\",\"departmentId\":" + sales.getId() + "}")
                .andExpect(status().isCreated());
        send("POST", "/api/v1/designations", hr, "{\"title\":\"Consultant\"}").andExpect(status().isCreated());
        send("POST", "/api/v1/designations", hr, "{\"title\":\"Consultant\"}").andExpect(status().isConflict());
        send("POST", "/api/v1/designations", hr, "{\"title\":\"Ghost\",\"departmentId\":99999}")
                .andExpect(status().isBadRequest());

        send("PUT", "/api/v1/designations/" + id, hr,
                "{\"title\":\"Principal Engineer\",\"departmentId\":" + sales.getId() + "}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.departmentName").value("Sales"));

        send("GET", "/api/v1/designations?departmentId=" + sales.getId(), bearer("emp@test.com"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));

        // deactivate -> hidden from the default (dropdown) list
        send("PUT", "/api/v1/designations/" + id, hr,
                "{\"title\":\"Principal Engineer\",\"departmentId\":" + sales.getId() + ",\"active\":false}")
                .andExpect(status().isOk());
        send("GET", "/api/v1/designations", hr, null).andExpect(jsonPath("$.length()").value(2));
        send("GET", "/api/v1/designations?includeInactive=true", hr, null).andExpect(jsonPath("$.length()").value(3));
    }

    // --------------------------------------------------------------- org chart

    @Test
    void orgChartReflectsManagerHierarchyOfActiveEmployees() throws Exception {
        String t = bearer("emp@test.com");   // any authenticated user
        JsonNode roots = body(send("GET", "/api/v1/org-chart", t, null).andExpect(status().isOk()));
        assertThat(roots.size()).isEqualTo(1);
        JsonNode cara = roots.get(0);
        assertThat(cara.get("fullName").asString()).isEqualTo("Cara CEO");
        JsonNode victor = cara.get("children").get(0);
        assertThat(victor.get("fullName").asString()).isEqualTo("Victor VP");
        JsonNode lena = victor.get("children").get(0);
        assertThat(lena.get("children").get(0).get("fullName").asString()).isEqualTo("Dan Dev");
        assertThat(lena.get("children").get(0).get("children").size()).isZero();
        // no sensitive fields in the chart
        String raw = roots.toString();
        assertThat(raw).doesNotContain("@test.com").doesNotContain("phone");

        // offboarding the middle manager removes them; their reports surface as roots
        send("PUT", "/api/v1/employees/" + vp.getId() + "/offboard", bearer("hr@test.com"), null).andExpect(status().isOk());
        JsonNode after = body(send("GET", "/api/v1/org-chart", t, null));
        assertThat(after.toString()).doesNotContain("Victor VP");
        assertThat(after.size()).isEqualTo(2);   // Cara and Lena (now a root with Dan under her)
    }

    @Test
    void orgChartRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/org-chart")).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- reassign

    @Test
    void reassignChangesDepartmentAndManager() throws Exception {
        String hr = bearer("hr@test.com");
        send("PUT", "/api/v1/employees/" + dev.getId() + "/reassign", hr,
                "{\"departmentId\":" + sales.getId() + ",\"managerId\":" + vp.getId() + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentName").value("Sales"))
                .andExpect(jsonPath("$.managerName").value("Victor VP"));

        send("PUT", "/api/v1/employees/" + dev.getId() + "/reassign", hr, "{\"removeManager\":true}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.managerId").doesNotExist());
        // department untouched by a manager-only change
        assertThat(employees.findById(dev.getId()).orElseThrow().getDepartment().getId()).isEqualTo(sales.getId());
    }

    @Test
    void reassignBlocksCircularReportingStructures() throws Exception {
        String hr = bearer("hr@test.com");
        String url = "/api/v1/employees/" + ceo.getId() + "/reassign";

        // the CEO becoming a report of their own descendant would form a loop
        for (Employee descendant : new Employee[] { vp, lead, dev }) {
            MvcResult res = send("PUT", url, hr, "{\"managerId\":" + descendant.getId() + "}")
                    .andExpect(status().isBadRequest()).andReturn();
            assertThat(res.getResponse().getContentAsString()).contains("loop");
        }
        // an employee cannot be their own manager
        send("PUT", url, hr, "{\"managerId\":" + ceo.getId() + "}").andExpect(status().isBadRequest());
        // nothing changed
        assertThat(employees.findById(ceo.getId()).orElseThrow().getManager()).isNull();
        // a non-circular move is fine: dev can move up under the CEO
        send("PUT", "/api/v1/employees/" + dev.getId() + "/reassign", hr, "{\"managerId\":" + ceo.getId() + "}")
                .andExpect(status().isOk());
    }

    @Test
    void reassignValidationAndPermissions() throws Exception {
        String hr = bearer("hr@test.com");
        String url = "/api/v1/employees/" + dev.getId() + "/reassign";
        send("PUT", url, hr, "{}").andExpect(status().isBadRequest());
        send("PUT", url, hr, "{\"managerId\":" + vp.getId() + ",\"removeManager\":true}").andExpect(status().isBadRequest());
        send("PUT", url, hr, "{\"departmentId\":99999}").andExpect(status().isBadRequest());
        send("PUT", url, hr, "{\"managerId\":99999}").andExpect(status().isBadRequest());
        send("PUT", url, bearer("emp@test.com"), "{\"managerId\":" + vp.getId() + "}").andExpect(status().isForbidden());
        send("PUT", "/api/v1/employees/99999/reassign", hr, "{\"managerId\":" + vp.getId() + "}")
                .andExpect(status().isNotFound());
    }
}
