package com.staffsuite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;

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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.staffsuite.entity.Department;
import com.staffsuite.entity.DocumentCategory;
import com.staffsuite.entity.DocumentVisibility;
import com.staffsuite.entity.Employee;
import com.staffsuite.entity.EmployeeDocument;
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
@Import(DocumentFlowTests.MemoryStorageConfig.class)
class DocumentFlowTests {

    /** Every file in these tests lives in memory: nothing here is tied to the local filesystem. */
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
    static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F' };

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
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
    DocumentCategory offer;
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
        idProof = category("ID Proof");
        offer = category("Offer Letter");

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

    // ------------------------------------------------------------------ helpers

    private DocumentCategory category(String name) {
        DocumentCategory c = new DocumentCategory();
        c.setName(name);
        return categories.save(c);
    }

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

    private ResultActions upload(String token, Employee target, DocumentCategory cat, String name, byte[] content,
            String expiry, String visibility) throws Exception {
        MockMultipartHttpServletRequestBuilder req = multipart("/api/v1/employees/" + target.getId() + "/documents");
        if (content != null) {
            req.file(new MockMultipartFile("file", name, "application/pdf", content));
        }
        req.param("categoryId", String.valueOf(cat.getId()));
        if (expiry != null) {
            req.param("expiryDate", expiry);
        }
        if (visibility != null) {
            req.param("visibility", visibility);
        }
        return mvc.perform(req.header("Authorization", token));
    }

    private long uploaded(String token, Employee target, String name, String expiry, String visibility) throws Exception {
        return json.readTree(upload(token, target, idProof, name, PDF, expiry, visibility)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode list(String token, Employee target) throws Exception {
        return json.readTree(send("GET", "/api/v1/employees/" + target.getId() + "/documents", token, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    // ----------------------------------------------------------------- uploads

    @Test
    void uploadStoresTheFileBehindTheStorageServiceAndOnlyMetadataInTheDatabase() throws Exception {
        String t = bearer("a@test.com");
        MvcResult res = upload(t, empA, idProof, "passport.pdf", PDF, "2030-01-31", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("passport.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.visibility").value("SELF_AND_HR"))
                .andExpect(jsonPath("$.categoryName").value("ID Proof"))
                .andExpect(jsonPath("$.employeeName").value("Asha Employee"))
                .andExpect(jsonPath("$.uploadedByName").value("Asha Employee"))
                .andReturn();
        String body = res.getResponse().getContentAsString();
        assertThat(body).doesNotContain("employee-documents/").doesNotContain("fileUrl");   // no storage path leaks

        EmployeeDocument row = documents.findAll().get(0);
        assertThat(row.getFileUrl()).matches("employee-documents/" + empA.getId() + "/[0-9a-f-]{36}\\.pdf");
        assertThat(row.getFileSize()).isEqualTo(PDF.length);
        assertThat(row.getExpiryDate()).isEqualTo(LocalDate.of(2030, 1, 31));
        assertThat(storage.files).containsKey(row.getFileUrl());              // the bytes are in storage ...
        assertThat(storage.files.get(row.getFileUrl())).isEqualTo(PDF);       // ... exactly as uploaded
        assertThat(audits.findAll()).anyMatch(a -> "DOCUMENT_UPLOADED".equals(a.getAction()));
    }

    @Test
    void whoMayUploadForWhom() throws Exception {
        String a = bearer("a@test.com");
        upload(a, empB, idProof, "x.pdf", PDF, null, null).andExpect(status().isForbidden());           // not your own
        upload(bearer("mgr@test.com"), empA, idProof, "x.pdf", PDF, null, null).andExpect(status().isForbidden());   // managers cannot
        upload(a, empA, idProof, "x.pdf", PDF, null, "HR_ONLY").andExpect(status().isBadRequest());     // cannot hide from HR-only
        upload(bearer("hr@test.com"), empA, idProof, "x.pdf", PDF, null, "HR_ONLY").andExpect(status().isCreated());
        upload(bearer("admin@test.com"), empB, offer, "y.pdf", PDF, null, null).andExpect(status().isCreated());
        mvc.perform(multipart("/api/v1/employees/" + empA.getId() + "/documents")
                .file(new MockMultipartFile("file", "x.pdf", "application/pdf", PDF)).param("categoryId", "1"))
                .andExpect(status().isUnauthorized());

        // unknown employee: HR is told, everyone else just gets 403 (no way to probe which ids exist)
        mvc.perform(multipart("/api/v1/employees/99999/documents").file(new MockMultipartFile("file", "x.pdf", "application/pdf", PDF))
                .param("categoryId", String.valueOf(idProof.getId())).header("Authorization", bearer("hr@test.com")))
                .andExpect(status().isNotFound());
        mvc.perform(multipart("/api/v1/employees/99999/documents").file(new MockMultipartFile("file", "x.pdf", "application/pdf", PDF))
                .param("categoryId", String.valueOf(idProof.getId())).header("Authorization", a))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadsAreValidated() throws Exception {
        String t = bearer("a@test.com");
        upload(t, empA, idProof, "notes.pdf", "plain text pretending to be a pdf".getBytes(StandardCharsets.UTF_8), null, null)
                .andExpect(status().isBadRequest());                                            // decided by content, not the name
        // only PDFs: real PNG / JPEG content is refused even when named and typed as a PDF
        MvcResult png = upload(t, empA, idProof, "scan.pdf", PNG, null, null).andExpect(status().isBadRequest()).andReturn();
        assertThat(png.getResponse().getContentAsString()).contains("Only PDF files are allowed");
        upload(t, empA, idProof, "photo.pdf", JPEG, null, null).andExpect(status().isBadRequest());
        upload(t, empA, idProof, "empty.pdf", new byte[0], null, null).andExpect(status().isBadRequest());
        upload(t, empA, idProof, "missing.pdf", null, null, null).andExpect(status().isBadRequest());
        upload(t, empA, idProof, "far.pdf", PDF, "2999-01-01", null).andExpect(status().isBadRequest());
        upload(t, empA, idProof, "bad-date.pdf", PDF, "tomorrow", null).andExpect(status().isBadRequest());
        upload(t, empA, idProof, "bad-vis.pdf", PDF, null, "EVERYONE").andExpect(status().isBadRequest());

        DocumentCategory ghost = new DocumentCategory();
        ghost.setId(99999L);
        upload(t, empA, ghost, "x.pdf", PDF, null, null).andExpect(status().isBadRequest());

        idProof.setActive(false);
        categories.save(idProof);
        upload(t, empA, idProof, "x.pdf", PDF, null, null).andExpect(status().isBadRequest());   // inactive category

        assertThat(documents.count()).isZero();
        assertThat(storage.files).isEmpty();        // a rejected upload leaves nothing behind
    }

    @Test
    void trickyFileNamesAreNeutralised() throws Exception {
        String t = bearer("a@test.com");
        for (String evil : new String[] { "../../etc/passwd.pdf", "C:\\Users\\x\\id\"card.pdf", "<script>.pdf" }) {
            MvcResult res = upload(t, empA, idProof, evil, PDF, null, null).andExpect(status().isCreated()).andReturn();
            String name = json.readTree(res.getResponse().getContentAsString()).get("fileName").asString();
            assertThat(name).doesNotContain("..").doesNotContain("/").doesNotContain("\\").doesNotContain("\"")
                    .doesNotContain("<");
        }
        // whatever the name, the stored reference is always generated
        documents.findAll().forEach(d -> assertThat(d.getFileUrl()).matches("employee-documents/\\d+/[0-9a-f-]{36}\\.pdf"));
    }

    // ------------------------------------------------------------- visibility

    @Test
    void visibilityRulesAreEnforcedForListingAndDownloading() throws Exception {
        long visible = uploaded(bearer("a@test.com"), empA, "mine-and-hr.pdf", null, null);
        long hrOnly = uploaded(bearer("hr@test.com"), empA, "confidential.pdf", null, "HR_ONLY");
        long byHr = uploaded(bearer("hr@test.com"), empA, "offer-letter.pdf", null, "SELF_AND_HR");
        String employee = bearer("a@test.com");
        String manager = bearer("mgr@test.com");
        String hr = bearer("hr@test.com");

        // the employee sees their own, but not what HR marked HR_ONLY
        JsonNode own = list(employee, empA);
        assertThat(own).hasSize(2);
        assertThat(own.toString()).contains("mine-and-hr.pdf", "offer-letter.pdf").doesNotContain("confidential.pdf");

        // a manager sees direct reports' non-HR_ONLY documents, and HR_ONLY never appears
        JsonNode team = list(manager, empA);
        assertThat(team).hasSize(2);
        assertThat(team.toString()).doesNotContain("confidential.pdf");

        // HR and Admin see everything
        assertThat(list(hr, empA)).hasSize(3);
        assertThat(list(bearer("admin@test.com"), empA)).hasSize(3);

        // nobody else can list someone's folder, even knowing the id; unknown ids look the same
        send("GET", "/api/v1/employees/" + empA.getId() + "/documents", bearer("b@test.com"), null).andExpect(status().isForbidden());
        send("GET", "/api/v1/employees/" + empB.getId() + "/documents", manager, null).andExpect(status().isForbidden());
        send("GET", "/api/v1/employees/99999/documents", employee, null).andExpect(status().isForbidden());
        send("GET", "/api/v1/employees/99999/documents", hr, null).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/employees/" + empA.getId() + "/documents")).andExpect(status().isUnauthorized());

        // downloads follow the same rules; anything not allowed is simply "not found"
        for (long id : new long[] { visible, byHr }) {
            for (String who : new String[] { employee, manager, hr }) {
                mvc.perform(get("/api/v1/documents/" + id + "/download").header("Authorization", who))
                        .andExpect(status().isOk());
            }
            send("GET", "/api/v1/documents/" + id + "/download", bearer("b@test.com"), null).andExpect(status().isNotFound());
        }
        for (String who : new String[] { employee, manager, bearer("b@test.com") }) {
            send("GET", "/api/v1/documents/" + hrOnly + "/download", who, null).andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/v1/documents/" + hrOnly + "/download").header("Authorization", hr)).andExpect(status().isOk());
        send("GET", "/api/v1/documents/99999/download", hr, null).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/documents/" + visible + "/download")).andExpect(status().isUnauthorized());
    }

    @Test
    void downloadReturnsTheOriginalBytesWithSafeHeaders() throws Exception {
        long id = uploaded(bearer("a@test.com"), empA, "Passport Scan.pdf", null, null);
        MvcResult res = mvc.perform(get("/api/v1/documents/" + id + "/download").header("Authorization", bearer("a@test.com")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn();
        assertThat(res.getResponse().getContentAsByteArray()).isEqualTo(PDF);
        assertThat(res.getResponse().getHeader("Content-Disposition")).startsWith("attachment").contains("Passport");
        assertThat(audits.findAll()).anyMatch(a -> "DOCUMENT_DOWNLOADED".equals(a.getAction()));
    }

    // ------------------------------------------------------------------ delete

    @Test
    void onlyHrAndAdminCanDeleteAndTheFileGoesWithTheRecord() throws Exception {
        long id = uploaded(bearer("a@test.com"), empA, "mine.pdf", null, null);
        String reference = documents.findById(id).orElseThrow().getFileUrl();

        send("DELETE", "/api/v1/documents/" + id, bearer("a@test.com"), null).andExpect(status().isForbidden());   // even the owner
        send("DELETE", "/api/v1/documents/" + id, bearer("mgr@test.com"), null).andExpect(status().isForbidden());
        send("DELETE", "/api/v1/documents/" + id, bearer("b@test.com"), null).andExpect(status().isForbidden());
        assertThat(documents.findById(id)).isPresent();
        assertThat(storage.exists(reference)).isTrue();

        send("DELETE", "/api/v1/documents/" + id, bearer("hr@test.com"), null).andExpect(status().isNoContent());
        assertThat(documents.findById(id)).isEmpty();
        assertThat(storage.exists(reference)).isFalse();
        send("DELETE", "/api/v1/documents/" + id, bearer("hr@test.com"), null).andExpect(status().isNotFound());
        send("GET", "/api/v1/documents/" + id + "/download", bearer("hr@test.com"), null).andExpect(status().isNotFound());
        assertThat(audits.findAll()).anyMatch(a -> "DOCUMENT_DELETED".equals(a.getAction()));
    }

    // ---------------------------------------------------------------- expiring

    @Test
    void expiringFiltersByTheWindowAndIsHrOnly() throws Exception {
        LocalDate today = LocalDate.now(clock);
        String hr = bearer("hr@test.com");
        uploaded(hr, empA, "in10.pdf", today.plusDays(10).toString(), null);
        uploaded(hr, empB, "in29.pdf", today.plusDays(29).toString(), null);
        uploaded(hr, empA, "in30.pdf", today.plusDays(30).toString(), null);       // the boundary is included
        uploaded(hr, empA, "in31.pdf", today.plusDays(31).toString(), null);
        uploaded(hr, empA, "in60.pdf", today.plusDays(60).toString(), null);
        uploaded(hr, empA, "never.pdf", null, null);                                // no expiry: never listed
        uploaded(hr, empA, "yesterday.pdf", today.minusDays(1).toString(), "HR_ONLY");
        uploaded(hr, empA, "today.pdf", today.toString(), null);

        // an offboarded employee's documents are not chased
        Employee gone = employee("Gita Gone", "gita@test.com", null);
        gone.setDeleted(true);
        employees.save(gone);
        EmployeeDocument orphan = new EmployeeDocument();
        orphan.setEmployee(gone);
        orphan.setCategory(idProof);
        orphan.setFileName("left.pdf");
        orphan.setFileUrl("employee-documents/" + gone.getId() + "/x.pdf");
        orphan.setExpiryDate(today.plusDays(5));
        orphan.setVisibility(DocumentVisibility.SELF_AND_HR);
        documents.save(orphan);

        // default window = 30 days (configuration)
        JsonNode defaults = json.readTree(send("GET", "/api/v1/documents/expiring", hr, null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(names(defaults)).containsExactly("today.pdf", "in10.pdf", "in29.pdf", "in30.pdf");   // soonest first
        assertThat(defaults.get(0).get("daysRemaining").asInt()).isZero();
        assertThat(defaults.get(1).get("daysRemaining").asInt()).isEqualTo(10);
        assertThat(defaults.get(1).get("employeeName").asString()).isEqualTo("Asha Employee");
        assertThat(defaults.get(1).get("departmentName").asString()).isEqualTo("Engineering");
        assertThat(defaults.get(1).get("expired").asBoolean()).isFalse();

        // an explicit window narrows or widens it
        assertThat(names(body(send("GET", "/api/v1/documents/expiring?days=15", hr, null)))).containsExactly("today.pdf", "in10.pdf");
        assertThat(names(body(send("GET", "/api/v1/documents/expiring?days=60", hr, null))))
                .containsExactly("today.pdf", "in10.pdf", "in29.pdf", "in30.pdf", "in31.pdf", "in60.pdf");

        // already-expired documents only appear when asked for
        JsonNode withExpired = body(send("GET", "/api/v1/documents/expiring?includeExpired=true", hr, null));
        assertThat(names(withExpired)).containsExactly("yesterday.pdf", "today.pdf", "in10.pdf", "in29.pdf", "in30.pdf");
        assertThat(withExpired.get(0).get("expired").asBoolean()).isTrue();
        assertThat(withExpired.get(0).get("daysRemaining").asInt()).isEqualTo(-1);

        send("GET", "/api/v1/documents/expiring?days=0", hr, null).andExpect(status().isBadRequest());
        send("GET", "/api/v1/documents/expiring?days=366", hr, null).andExpect(status().isBadRequest());
        send("GET", "/api/v1/documents/expiring?days=abc", hr, null).andExpect(status().isBadRequest());
        send("GET", "/api/v1/documents/expiring", bearer("admin@test.com"), null).andExpect(status().isOk());
        for (String who : new String[] { "a@test.com", "mgr@test.com", "b@test.com" }) {
            send("GET", "/api/v1/documents/expiring", bearer(who), null).andExpect(status().isForbidden());
        }
    }

    private JsonNode body(ResultActions r) throws Exception {
        return json.readTree(r.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static java.util.List<String> names(JsonNode docs) {
        java.util.List<String> out = new java.util.ArrayList<>();
        docs.forEach(d -> out.add(d.get("fileName").asString()));
        return out;
    }

    // --------------------------------------------------------- upload time + view

    @Test
    void uploadTimeIsSetByTheServerStoredPermanentlyAndReturned() throws Exception {
        java.time.Instant before = java.time.Instant.now();
        MvcResult res = upload(bearer("a@test.com"), empA, idProof, "dated.pdf", PDF, null, null)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.uploadedAt").exists()).andReturn();
        java.time.Instant after = java.time.Instant.now();

        java.time.Instant reported = java.time.Instant.parse(
                json.readTree(res.getResponse().getContentAsString()).get("uploadedAt").asString());
        assertThat(reported).isBetween(before.minusSeconds(1), after.plusSeconds(1));   // the server's clock, "now"

        EmployeeDocument row = documents.findAll().get(0);
        assertThat(row.getUploadedAt()).isNotNull().isBetween(before.minusSeconds(1), after.plusSeconds(1));

        // it is part of the listing too, and stays exactly the same on every read
        JsonNode first = list(bearer("a@test.com"), empA).get(0);
        Thread.sleep(50);
        JsonNode second = list(bearer("hr@test.com"), empA).get(0);
        assertThat(first.get("uploadedAt").asString()).isEqualTo(second.get("uploadedAt").asString());
        assertThat(java.time.Instant.parse(first.get("uploadedAt").asString())).isEqualTo(row.getUploadedAt());
        // what the upload call returned is character-for-character what every later read returns
        String atUpload = json.readTree(res.getResponse().getContentAsString()).get("uploadedAt").asString();
        assertThat(atUpload).isEqualTo(first.get("uploadedAt").asString()).isEqualTo(second.get("uploadedAt").asString());
        assertThat(row.getUploadedAt().getNano() % 1_000_000).isZero();   // millisecond precision

        // an "uploadedAt" sent by the client is simply ignored
        MockMultipartHttpServletRequestBuilder forged = multipart("/api/v1/employees/" + empA.getId() + "/documents");
        forged.file(new MockMultipartFile("file", "forged.pdf", "application/pdf", PDF));
        forged.param("categoryId", String.valueOf(idProof.getId())).param("uploadedAt", "2001-01-01T00:00:00Z");
        MvcResult forgedRes = mvc.perform(forged.header("Authorization", bearer("a@test.com")))
                .andExpect(status().isCreated()).andReturn();
        assertThat(json.readTree(forgedRes.getResponse().getContentAsString()).get("uploadedAt").asString())
                .doesNotStartWith("2001");
    }

    @Test
    void pdfsCanBeViewedInlineUnderTheSameAccessRulesAsDownloads() throws Exception {
        long visible = uploaded(bearer("a@test.com"), empA, "viewable.pdf", null, null);
        long hrOnly = uploaded(bearer("hr@test.com"), empA, "secret.pdf", null, "HR_ONLY");
        String employee = bearer("a@test.com");
        String manager = bearer("mgr@test.com");
        String hr = bearer("hr@test.com");

        for (String who : new String[] { employee, manager, hr, bearer("admin@test.com") }) {
            MvcResult res = mvc.perform(get("/api/v1/documents/" + visible + "/view").header("Authorization", who))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "application/pdf"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andReturn();
            assertThat(res.getResponse().getContentAsByteArray()).isEqualTo(PDF);
            assertThat(res.getResponse().getHeader("Content-Disposition")).startsWith("inline");   // shown, not saved
        }

        // anyone not allowed to see the document gets "not found", exactly as for downloads
        send("GET", "/api/v1/documents/" + visible + "/view", bearer("b@test.com"), null).andExpect(status().isNotFound());
        for (String who : new String[] { employee, manager, bearer("b@test.com") }) {
            send("GET", "/api/v1/documents/" + hrOnly + "/view", who, null).andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/v1/documents/" + hrOnly + "/view").header("Authorization", hr)).andExpect(status().isOk());
        send("GET", "/api/v1/documents/99999/view", hr, null).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/documents/" + visible + "/view")).andExpect(status().isUnauthorized());

        // the download still saves it as an attachment, and the two are audited separately
        mvc.perform(get("/api/v1/documents/" + visible + "/download").header("Authorization", employee))
                .andExpect(status().isOk());
        MvcResult dl = mvc.perform(get("/api/v1/documents/" + visible + "/download").header("Authorization", employee)).andReturn();
        assertThat(dl.getResponse().getHeader("Content-Disposition")).startsWith("attachment");
        assertThat(audits.findAll()).anyMatch(a -> "DOCUMENT_VIEWED".equals(a.getAction()))
                .anyMatch(a -> "DOCUMENT_DOWNLOADED".equals(a.getAction()));
    }

    @Test
    void onlyPdfDocumentsCanBeViewedInTheApp() throws Exception {
        // an image uploaded before documents became PDF-only: still downloadable, but not shown inline
        EmployeeDocument legacy = new EmployeeDocument();
        legacy.setEmployee(empA);
        legacy.setCategory(idProof);
        legacy.setFileName("old-scan.png");
        legacy.setFileUrl("employee-documents/" + empA.getId() + "/legacy.png");
        legacy.setContentType("image/png");
        legacy.setVisibility(DocumentVisibility.SELF_AND_HR);
        legacy = documents.save(legacy);
        storage.files.put(legacy.getFileUrl(), PNG);

        send("GET", "/api/v1/documents/" + legacy.getId() + "/view", bearer("a@test.com"), null)
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/documents/" + legacy.getId() + "/download").header("Authorization", bearer("a@test.com")))
                .andExpect(status().isOk());
    }

    // -------------------------------------------------------------- categories

    @Test
    void categoriesAreReadableByAllAndManagedByHrOnly() throws Exception {
        String hr = bearer("hr@test.com");
        String emp = bearer("a@test.com");

        send("GET", "/api/v1/documents/categories", emp, null).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        send("POST", "/api/v1/documents/categories", emp, "{\"name\":\"X\"}").andExpect(status().isForbidden());
        send("POST", "/api/v1/documents/categories", bearer("mgr@test.com"), "{}").andExpect(status().isForbidden());

        long id = json.readTree(send("POST", "/api/v1/documents/categories", hr,
                "{\"name\":\"Certificate\",\"description\":\"Degrees\"}").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        send("POST", "/api/v1/documents/categories", hr, "{\"name\":\"certificate\"}").andExpect(status().isConflict());
        send("POST", "/api/v1/documents/categories", hr, "{\"name\":\" \"}").andExpect(status().isBadRequest());
        send("PUT", "/api/v1/documents/categories/" + id, hr, "{\"name\":\"Certificates\",\"description\":\"All\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Certificates"));
        send("PUT", "/api/v1/documents/categories/" + id, hr, "{\"name\":\"ID Proof\"}").andExpect(status().isConflict());
        send("PUT", "/api/v1/documents/categories/99999", hr, "{\"name\":\"Nope\"}").andExpect(status().isNotFound());
        send("PUT", "/api/v1/documents/categories/" + id, emp, "{\"name\":\"Hax\"}").andExpect(status().isForbidden());

        send("PUT", "/api/v1/documents/categories/" + id, hr, "{\"name\":\"Certificates\",\"active\":false}").andExpect(status().isOk());
        send("GET", "/api/v1/documents/categories", emp, null).andExpect(jsonPath("$.length()").value(2));
        send("GET", "/api/v1/documents/categories?includeInactive=true", hr, null).andExpect(jsonPath("$.length()").value(3));
        send("GET", "/api/v1/documents/categories?includeInactive=true", emp, null).andExpect(jsonPath("$.length()").value(2));
    }
}
