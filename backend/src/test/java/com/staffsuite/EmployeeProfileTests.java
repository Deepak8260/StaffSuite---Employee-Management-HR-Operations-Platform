package com.staffsuite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.staffsuite.entity.Department;
import com.staffsuite.entity.DocumentCategory;
import com.staffsuite.entity.Employee;
import com.staffsuite.entity.EmploymentStatus;
import com.staffsuite.entity.Role;
import com.staffsuite.entity.User;
import com.staffsuite.repository.AuditLogRepository;
import com.staffsuite.repository.DepartmentRepository;
import com.staffsuite.repository.DocumentCategoryRepository;
import com.staffsuite.repository.EmployeeDocumentRepository;
import com.staffsuite.repository.EmployeeRepository;
import com.staffsuite.repository.UserRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmployeeProfileTests {

    @TestConfiguration
    static class MemoryStorageConfig {
        @Bean
        @Primary
        InMemoryStorageService memoryStorage() {
            return new InMemoryStorageService();
        }
    }

    static final byte[] PDF = "%PDF-1.4 fake pdf content".getBytes(StandardCharsets.UTF_8);
    static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0 };

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired InMemoryStorageService storage;
    @Autowired UserRepository users;
    @Autowired EmployeeRepository employees;
    @Autowired DepartmentRepository departments;
    @Autowired DocumentCategoryRepository categories;
    @Autowired EmployeeDocumentRepository documents;
    @Autowired AuditLogRepository audits;
    @Autowired PasswordEncoder encoder;

    Department dept;
    DocumentCategory idProof;
    Employee mgrEmp;
    Employee otherMgrEmp;
    Employee empA;   // reports to mgrEmp
    Employee empB;   // reports to otherMgrEmp

    @BeforeEach
    void seed() {
        wipe();
        dept = new Department();
        dept.setName("Engineering");
        dept = departments.save(dept);
        idProof = new DocumentCategory();
        idProof.setName("ID Proof");
        idProof = categories.save(idProof);

        mgrEmp = employee("Mark Manager", "mark@test.com", null);
        otherMgrEmp = employee("Olga Other", "olga@test.com", null);
        empA = employee("Asha Employee", "asha@test.com", mgrEmp);
        empB = employee("Bela Employee", "bela@test.com", otherMgrEmp);

        account("admin@test.com", Role.SYSTEM_ADMIN, null);
        account("hr@test.com", Role.HR_ADMIN, null);
        account("mgr@test.com", Role.MANAGER, mgrEmp);
        account("olga@test.com", Role.MANAGER, otherMgrEmp);
        account("a@test.com", Role.EMPLOYEE, empA);
        account("b@test.com", Role.EMPLOYEE, empB);
    }

    @AfterEach
    void cleanUp() {
        wipe();
    }

    private void wipe() {
        storage.files.clear();
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        documents.deleteAllInBatch();
        categories.deleteAllInBatch();
        audits.deleteAllInBatch();
        users.deleteAllInBatch();
        employees.deleteAllInBatch();
        departments.deleteAllInBatch();
        jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }

    private Employee employee(String name, String email, Employee manager) {
        Employee e = new Employee();
        e.setEmployeeCode("EMP-T" + Math.abs(email.hashCode()));
        e.setFullName(name);
        e.setEmail(email);
        e.setJoiningDate(LocalDate.now().minusYears(1));
        e.setJobTitle("Engineer");
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

    private JsonNode profile(String token, Employee target) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/employees/" + target.getId() + "/profile").header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private void uploadDocument(String token, Employee target, String visibility) throws Exception {
        mvc.perform(multipart("/api/v1/employees/" + target.getId() + "/documents")
                .file(new MockMultipartFile("file", "doc-" + visibility + ".pdf", "application/pdf", PDF))
                .param("categoryId", String.valueOf(idProof.getId())).param("visibility", visibility)
                .header("Authorization", token)).andExpect(status().isCreated());
    }

    private ResultActions photo(String token, Employee target, String name, byte[] content) throws Exception {
        return mvc.perform(multipart("/api/v1/employees/" + target.getId() + "/photo")
                .file(new MockMultipartFile("file", name, "image/png", content)).header("Authorization", token));
    }

    private static List<String> names(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.get("fileName").asString()));
        return out;
    }

    // -------------------------------------------------------------------- tests

    @Test
    void selfHrAndDirectManagerGetTheFullProfileWithEverySection() throws Exception {
        for (String who : List.of("a@test.com", "hr@test.com", "admin@test.com", "mgr@test.com")) {
            JsonNode p = profile(bearer(who), empA);
            assertThat(p.get("accessLevel").asString()).as(who).isEqualTo("FULL");
            assertThat(p.get("fullName").asString()).isEqualTo("Asha Employee");
            assertThat(p.get("employeeCode").asString()).startsWith("EMP-");
            assertThat(p.get("managerName").asString()).isEqualTo("Mark Manager");
            assertThat(p.get("managerId").asLong()).isEqualTo(mgrEmp.getId());
            assertThat(p.get("leave").get("pendingRequests").asLong()).isZero();
            assertThat(p.get("leave").get("balances").isArray()).isTrue();
            assertThat(p.get("attendance").get("month").asString()).matches("\\d{4}-\\d{2}");
            assertThat(p.get("documents").isArray()).isTrue();
            assertThat(p.get("directReports").size()).isZero();
        }
    }

    @Test
    void aManagerProfileListsDirectReports() throws Exception {
        JsonNode p = profile(bearer("mgr@test.com"), mgrEmp);
        assertThat(p.get("directReports").size()).isEqualTo(1);
        assertThat(p.get("directReports").get(0).get("fullName").asString()).isEqualTo("Asha Employee");
        assertThat(p.get("directReports").get(0).get("id").asLong()).isEqualTo(empA.getId());
    }

    @Test
    void nobodyElseCanOpenAProfileAtAll() throws Exception {
        // another team's manager and a colleague: refused, not even a reduced view
        for (String who : List.of("olga@test.com", "b@test.com")) {
            mvc.perform(get("/api/v1/employees/" + empA.getId() + "/profile").header("Authorization", bearer(who)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void profileDocumentsFollowTheHrOnlyRule() throws Exception {
        uploadDocument(bearer("a@test.com"), empA, "SELF_AND_HR");
        uploadDocument(bearer("hr@test.com"), empA, "HR_ONLY");
        assertThat(names(profile(bearer("mgr@test.com"), empA).get("documents"))).containsExactly("doc-SELF_AND_HR.pdf");
        assertThat(names(profile(bearer("a@test.com"), empA).get("documents")))
                .containsExactlyInAnyOrder("doc-SELF_AND_HR.pdf");
        assertThat(names(profile(bearer("hr@test.com"), empA).get("documents")))
                .containsExactlyInAnyOrder("doc-SELF_AND_HR.pdf", "doc-HR_ONLY.pdf");
    }

    @Test
    void photoIsStoredAsAReferenceServedBackAndRestrictedToSelfOrHr() throws Exception {
        assertThat(profile(bearer("a@test.com"), empA).get("profilePhotoUrl").isNull()).isTrue();

        photo(bearer("a@test.com"), empA, "me.png", PNG).andExpect(status().isOk());
        String url = profile(bearer("hr@test.com"), empA).get("profilePhotoUrl").asString();
        assertThat(url).isEqualTo("/api/v1/employees/" + empA.getId() + "/photo");
        String reference = employees.findById(empA.getId()).orElseThrow().getProfilePhotoUrl();
        assertThat(reference).startsWith("employee-photos/" + empA.getId() + "/").endsWith(".png");
        assertThat(storage.files).containsKey(reference);

        // only the people who may see the profile can load it, and it is an image
        byte[] served = mvc.perform(get(url).header("Authorization", bearer("mgr@test.com")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(served).isEqualTo(PNG);
        mvc.perform(get(url).header("Authorization", bearer("b@test.com"))).andExpect(status().isForbidden());
        mvc.perform(get(url).header("Authorization", bearer("olga@test.com"))).andExpect(status().isForbidden());
        mvc.perform(get(url)).andExpect(status().isUnauthorized());

        // someone else's photo can only be changed by HR/Admin
        photo(bearer("b@test.com"), empA, "x.png", PNG).andExpect(status().isForbidden());
        photo(bearer("mgr@test.com"), empA, "x.png", PNG).andExpect(status().isForbidden());
        photo(bearer("hr@test.com"), empA, "hr.png", PNG).andExpect(status().isOk());
        // the replaced file is removed from storage
        assertThat(storage.files).doesNotContainKey(reference);

    }

    @Test
    void photoUploadRejectsWrongTypesAndOversizedFiles() throws Exception {
        String t = bearer("a@test.com");
        photo(t, empA, "doc.png", PDF).andExpect(status().isBadRequest());                  // a PDF pretending to be a PNG
        photo(t, empA, "text.png", "just text".getBytes(StandardCharsets.UTF_8)).andExpect(status().isBadRequest());
        byte[] big = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        photo(t, empA, "big.png", big).andExpect(status().isPayloadTooLarge());
        assertThat(employees.findById(empA.getId()).orElseThrow().getProfilePhotoUrl()).isNull();
    }

    @Test
    void offboardedEmployeesAreHiddenFromEveryoneButHr() throws Exception {
        empB.setDeleted(true);
        employees.save(empB);
        mvc.perform(get("/api/v1/employees/" + empB.getId() + "/profile").header("Authorization", bearer("a@test.com")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/employees/" + empB.getId() + "/profile").header("Authorization", bearer("olga@test.com")))
                .andExpect(status().isNotFound());      // even their own manager no longer sees a person who has left
        mvc.perform(get("/api/v1/employees/" + empB.getId() + "/profile").header("Authorization", bearer("hr@test.com")))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------- profile completion

    private void saveContact(String token, Employee target, String phone, String address, String emergency) throws Exception {
        mvc.perform(put("/api/v1/employees/" + target.getId() + "/profile").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + phone + "\",\"address\":\"" + address + "\",\"emergencyContact\":\"" + emergency + "\"}"))
                .andExpect(status().isOk());
    }

    private List<String> completionMessages(String token) throws Exception {
        JsonNode page = json.readTree(mvc.perform(get("/api/v1/notifications?size=50").header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        List<String> out = new ArrayList<>();
        page.get("content").forEach(n -> {
            if (n.get("type").asString().equals("PROFILE_COMPLETED")) {
                out.add(n.get("message").asString());
            }
        });
        return out;
    }

    @Test
    void completionBarCountsWhatTheEmployeeCanFillIn() throws Exception {
        JsonNode before = profile(bearer("a@test.com"), empA).get("completion");
        assertThat(before.get("percent").asInt()).isZero();
        assertThat(before.get("complete").asBoolean()).isFalse();
        assertThat(before.get("items").size()).isEqualTo(5);

        saveContact(bearer("a@test.com"), empA, "9876543210", "12 Main Street", "Ravi 9123456780");
        assertThat(profile(bearer("a@test.com"), empA).get("completion").get("percent").asInt()).isEqualTo(60);

        photo(bearer("a@test.com"), empA, "me.png", PNG).andExpect(status().isOk());
        assertThat(profile(bearer("a@test.com"), empA).get("completion").get("percent").asInt()).isEqualTo(80);

        uploadDocument(bearer("a@test.com"), empA, "SELF_AND_HR");
        JsonNode full = profile(bearer("mgr@test.com"), empA).get("completion");
        assertThat(full.get("percent").asInt()).isEqualTo(100);
        assertThat(full.get("complete").asBoolean()).isTrue();
    }

    @Test
    void managerAndSystemAdminAreToldOnceWhenTheProfileReachesOneHundredPercent() throws Exception {
        String a = bearer("a@test.com");
        saveContact(a, empA, "9876543210", "12 Main Street", "Ravi 9123456780");
        assertThat(completionMessages(bearer("mgr@test.com"))).isEmpty();
        assertThat(completionMessages(bearer("admin@test.com"))).isEmpty();

        photo(a, empA, "me.png", PNG).andExpect(status().isOk());
        assertThat(completionMessages(bearer("mgr@test.com"))).isEmpty();      // still missing a document
        uploadDocument(a, empA, "SELF_AND_HR");                                  // the last missing item
        assertThat(completionMessages(bearer("mgr@test.com"))).containsExactly("Asha Employee has completed their profile.");
        assertThat(completionMessages(bearer("admin@test.com"))).containsExactly("Asha Employee has completed their profile.");
        assertThat(completionMessages(bearer("hr@test.com"))).isEmpty();      // only the manager and system admins
        assertThat(completionMessages(bearer("olga@test.com"))).isEmpty();    // another team's manager
        assertThat(completionMessages(a)).isEmpty();                          // not the employee themself

        // later edits never repeat it
        saveContact(a, empA, "1111111111", "New address", "Someone 222");
        photo(a, empA, "again.png", PNG).andExpect(status().isOk());
        uploadDocument(a, empA, "SELF_AND_HR");
        assertThat(completionMessages(bearer("mgr@test.com"))).hasSize(1);
        assertThat(completionMessages(bearer("admin@test.com"))).hasSize(1);
    }

    @Test
    void withoutAManagerOnlySystemAdminsAreTold() throws Exception {
        Employee solo = employee("Sam Solo", "sam@test.com", null);
        account("sam@test.com", Role.EMPLOYEE, solo);
        String s = bearer("sam@test.com");
        saveContact(s, solo, "9876543210", "1 Road", "Mum 5555");
        photo(s, solo, "me.png", PNG).andExpect(status().isOk());
        uploadDocument(s, solo, "SELF_AND_HR");
        assertThat(completionMessages(bearer("admin@test.com"))).containsExactly("Sam Solo has completed their profile.");
        assertThat(completionMessages(bearer("mgr@test.com"))).isEmpty();
    }

    @Test
    void hrFinishingSomeonesPhotoDoesNotCountAsTheEmployeeCompletingIt() throws Exception {
        saveContact(bearer("a@test.com"), empA, "9876543210", "12 Main Street", "Ravi 9123456780");
        uploadDocument(bearer("a@test.com"), empA, "SELF_AND_HR");
        photo(bearer("hr@test.com"), empA, "hr.png", PNG).andExpect(status().isOk());
        assertThat(profile(bearer("a@test.com"), empA).get("completion").get("percent").asInt()).isEqualTo(100);
        assertThat(completionMessages(bearer("mgr@test.com"))).isEmpty();
        assertThat(completionMessages(bearer("admin@test.com"))).isEmpty();
    }

    @Test
    void aPhotoCanBeRemovedByTheEmployeeOrHrOnly() throws Exception {
        String a = bearer("a@test.com");
        photo(a, empA, "me.png", PNG).andExpect(status().isOk());
        String reference = employees.findById(empA.getId()).orElseThrow().getProfilePhotoUrl();
        assertThat(storage.files).containsKey(reference);

        mvc.perform(delete("/api/v1/employees/" + empA.getId() + "/photo").header("Authorization", bearer("b@test.com")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/employees/" + empA.getId() + "/photo").header("Authorization", bearer("mgr@test.com")))
                .andExpect(status().isForbidden());
        assertThat(storage.files).containsKey(reference);

        mvc.perform(delete("/api/v1/employees/" + empA.getId() + "/photo").header("Authorization", a)).andExpect(status().isOk());
        assertThat(employees.findById(empA.getId()).orElseThrow().getProfilePhotoUrl()).isNull();
        assertThat(storage.files).doesNotContainKey(reference);
        assertThat(profile(a, empA).get("profilePhotoUrl").isNull()).isTrue();
        mvc.perform(get("/api/v1/employees/" + empA.getId() + "/photo").header("Authorization", a)).andExpect(status().isNotFound());

        // removing again, or as HR, is harmless
        mvc.perform(delete("/api/v1/employees/" + empA.getId() + "/photo").header("Authorization", a)).andExpect(status().isOk());
        photo(a, empA, "me.png", PNG).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/employees/" + empA.getId() + "/photo").header("Authorization", bearer("hr@test.com")))
                .andExpect(status().isOk());
    }

    @Test
    void addressAndEmergencyContactAreShownAndAnHrUploadedDocumentDoesNotTriggerTheNotification() throws Exception {
        String a = bearer("a@test.com");
        saveContact(a, empA, "9876543210", "12 Main Street", "Ravi 9123456780");
        photo(a, empA, "me.png", PNG).andExpect(status().isOk());
        JsonNode p = profile(a, empA);
        assertThat(p.get("address").asString()).isEqualTo("12 Main Street");
        assertThat(p.get("emergencyContact").asString()).isEqualTo("Ravi 9123456780");

        uploadDocument(bearer("hr@test.com"), empA, "SELF_AND_HR");     // HR adds the last item
        assertThat(profile(a, empA).get("completion").get("percent").asInt()).isEqualTo(100);
        assertThat(completionMessages(bearer("mgr@test.com"))).isEmpty();
    }

    // ------------------------------------------------------------ data isolation

    private List<String> directoryNames(String token) throws Exception {
        JsonNode page = json.readTree(mvc.perform(get("/api/v1/employees/directory?size=50").header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        List<String> out = new ArrayList<>();
        page.get("content").forEach(n -> out.add(n.get("fullName").asString()));
        return out;
    }

    @Test
    void theDirectoryShowsOnlyThePeopleTheCallerMaySee() throws Exception {
        assertThat(directoryNames(bearer("a@test.com"))).containsExactly("Asha Employee");
        assertThat(directoryNames(bearer("b@test.com"))).containsExactly("Bela Employee");
        assertThat(directoryNames(bearer("mgr@test.com"))).containsExactlyInAnyOrder("Mark Manager", "Asha Employee");
        assertThat(directoryNames(bearer("olga@test.com"))).containsExactlyInAnyOrder("Olga Other", "Bela Employee");
        assertThat(directoryNames(bearer("hr@test.com"))).hasSize(4);
        assertThat(directoryNames(bearer("admin@test.com"))).hasSize(4);
        // searching cannot reach beyond the scope either
        JsonNode found = json.readTree(mvc.perform(get("/api/v1/employees/directory?q=Bela").header("Authorization", bearer("a@test.com")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(found.get("totalElements").asLong()).isZero();
    }

    @Test
    void theOrgChartIsLimitedTheSameWay() throws Exception {
        JsonNode mine = json.readTree(mvc.perform(get("/api/v1/org-chart").header("Authorization", bearer("a@test.com")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(mine.size()).isEqualTo(1);
        assertThat(mine.get(0).get("fullName").asString()).isEqualTo("Asha Employee");
        assertThat(mine.get(0).get("children").size()).isZero();

        JsonNode team = json.readTree(mvc.perform(get("/api/v1/org-chart").header("Authorization", bearer("mgr@test.com")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(team.size()).isEqualTo(1);
        assertThat(team.get(0).get("fullName").asString()).isEqualTo("Mark Manager");
        assertThat(team.get(0).get("children").get(0).get("fullName").asString()).isEqualTo("Asha Employee");
        assertThat(team.toString()).doesNotContain("Olga").doesNotContain("Bela");

        JsonNode all = json.readTree(mvc.perform(get("/api/v1/org-chart").header("Authorization", bearer("hr@test.com")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(all.size()).isEqualTo(2);
    }

    @Test
    void everyOtherEmployeeEndpointRefusesToShowSomeoneElse() throws Exception {
        String a = bearer("a@test.com");
        String olga = bearer("olga@test.com");
        for (String url : List.of("/api/v1/employees/" + empB.getId(), "/api/v1/employees/" + empB.getId() + "/profile",
                "/api/v1/employees/" + empB.getId() + "/photo", "/api/v1/employees/" + empB.getId() + "/documents",
                "/api/v1/employees/" + empB.getId() + "/team")) {
            mvc.perform(get(url).header("Authorization", a)).andExpect(status().isForbidden());
        }
        // a manager sees their own report but not another team's person, nor a peer manager
        mvc.perform(get("/api/v1/employees/" + empA.getId() + "/profile").header("Authorization", bearer("mgr@test.com")))
                .andExpect(status().isOk());
        for (Employee other : List.of(empB, otherMgrEmp)) {
            mvc.perform(get("/api/v1/employees/" + other.getId() + "/profile").header("Authorization", bearer("mgr@test.com")))
                    .andExpect(status().isForbidden());
        }
        // a person's own manager can't be read by the person either (only HR/Admin and the manager themself)
        mvc.perform(get("/api/v1/employees/" + mgrEmp.getId() + "/profile").header("Authorization", a)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/employees/" + empA.getId() + "/profile").header("Authorization", olga)).andExpect(status().isForbidden());
        // the full employee list is not available to employees, and a manager gets only their own team
        mvc.perform(get("/api/v1/employees").header("Authorization", a)).andExpect(status().isForbidden());
        JsonNode list = json.readTree(mvc.perform(get("/api/v1/employees").header("Authorization", bearer("mgr@test.com")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(list.get("totalElements").asLong()).isEqualTo(1);
    }
}
