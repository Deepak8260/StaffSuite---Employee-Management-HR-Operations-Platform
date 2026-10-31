package com.staffsuite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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

import com.staffsuite.entity.Department;
import com.staffsuite.entity.Employee;
import com.staffsuite.entity.EmploymentStatus;
import com.staffsuite.entity.Role;
import com.staffsuite.entity.User;
import com.staffsuite.repository.AuditLogRepository;
import com.staffsuite.repository.DepartmentRepository;
import com.staffsuite.repository.EmployeeRepository;
import com.staffsuite.repository.UserRepository;
import com.staffsuite.service.EmployeeBulkImportService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmployeeBulkUploadTests {

    static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired EmployeeRepository employees;
    @Autowired DepartmentRepository departments;
    @Autowired AuditLogRepository audits;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void seed() {
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        audits.deleteAllInBatch();
        users.deleteAllInBatch();
        employees.deleteAllInBatch();
        departments.deleteAllInBatch();
        jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");

        Department engineering = new Department();
        engineering.setName("Engineering");
        departments.save(engineering);

        Employee boss = new Employee();
        boss.setEmployeeCode("EMP-BOSS");
        boss.setFullName("Boss Person");
        boss.setEmail("boss@test.com");
        boss.setJoiningDate(LocalDate.now().minusYears(2));
        boss.setJobTitle("Director");
        boss.setEmploymentStatus(EmploymentStatus.ACTIVE);
        boss.setDepartment(engineering);
        employees.save(boss);

        account("hr@test.com", Role.HR_ADMIN);
        account("emp@test.com", Role.EMPLOYEE);
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

    private static byte[] workbook(List<String> header, List<List<String>> rows) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Employees");
            Row h = sheet.createRow(0);
            for (int i = 0; i < header.size(); i++) {
                h.createCell(i).setCellValue(header.get(i));
            }
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows.get(r).size(); c++) {
                    row.createCell(c).setCellValue(rows.get(r).get(c));
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static List<String> row(String name, String email, String joining, String dept, String desig, String mgr,
            String status) {
        return List.of(name, email, "", "", joining, "Developer", dept, desig, mgr, status);
    }

    private JsonNode upload(String token, byte[] bytes, String filename, int expectedStatus) throws Exception {
        MvcResult res = mvc.perform(multipart("/api/v1/employees/bulk-upload")
                .file(new MockMultipartFile("file", filename, XLSX, bytes)).header("Authorization", token))
                .andExpect(status().is(expectedStatus)).andReturn();
        return json.readTree(res.getResponse().getContentAsString());
    }

    @Test
    void validRowsAreCreatedAndInvalidOnesReportedPerRow() throws Exception {
        String ok = LocalDate.now().minusDays(30).toString();
        byte[] file = workbook(EmployeeBulkImportService.COLUMNS, List.of(
                row("Good One", "good1@test.com", ok, "Engineering", "", "boss@test.com", ""),
                row("Bad Dept", "bad1@test.com", ok, "Sales", "", "", "ACTIVE"),
                row("Dup Email", "boss@test.com", ok, "Engineering", "", "", "ACTIVE"),
                row("Bad Mgr", "bad2@test.com", ok, "Engineering", "", "nobody@test.com", "ACTIVE"),
                row("", "bad3@test.com", ok, "Engineering", "", "", "ACTIVE"),
                row("Bad Status", "bad4@test.com", ok, "Engineering", "", "", "TERMINATED"),
                row("Bad Date", "bad5@test.com", "31/12/2024", "Engineering", "", "", "ACTIVE"),
                row("Good Two", "good2@test.com", ok, "engineering", "", "good1@test.com", "PROBATION")));

        JsonNode result = upload(bearer("hr@test.com"), file, "people.xlsx", 200);

        assertThat(result.get("totalRows").asInt()).isEqualTo(8);
        assertThat(result.get("created").asInt()).isEqualTo(2);
        assertThat(result.get("failed").asInt()).isEqualTo(6);
        assertThat(result.get("errors").get(0).get("row").asInt()).isEqualTo(3);
        assertThat(result.get("errors").get(0).get("reason").asString()).contains("department 'Sales' not found");
        assertThat(result.get("errors").get(1).get("reason").asString()).contains("already exists");

        Employee good1 = employees.findByEmailIgnoreCase("good1@test.com").orElseThrow();
        Employee good2 = employees.findByEmailIgnoreCase("good2@test.com").orElseThrow();
        assertThat(good1.getEmployeeCode()).matches("EMP-\\d{4,}");
        assertThat(good2.getEmployeeCode()).matches("EMP-\\d{4,}").isNotEqualTo(good1.getEmployeeCode());
        assertThat(good1.getManager().getId()).isEqualTo(employees.findByEmailIgnoreCase("boss@test.com").orElseThrow().getId());
        assertThat(good1.getEmploymentStatus()).isEqualTo(EmploymentStatus.ACTIVE);
        assertThat(good2.getEmploymentStatus()).isEqualTo(EmploymentStatus.PROBATION);
        assertThat(employees.findByEmailIgnoreCase("bad1@test.com")).isEmpty();
    }

    @Test
    void wrongHeaderRejectsTheWholeFileWithTheColumnGuide() throws Exception {
        String ok = LocalDate.now().minusDays(30).toString();
        List<String> renamed = new java.util.ArrayList<>(EmployeeBulkImportService.COLUMNS);
        renamed.set(0, "name");
        JsonNode error = upload(bearer("hr@test.com"),
                workbook(renamed, List.of(row("A", "a@test.com", ok, "Engineering", "", "", ""))), "x.xlsx", 400);
        assertThat(error.get("message").asString()).contains(EmployeeBulkImportService.COLUMN_GUIDE);
        assertThat(employees.findByEmailIgnoreCase("a@test.com")).isEmpty();

        List<String> extra = new java.util.ArrayList<>(EmployeeBulkImportService.COLUMNS);
        extra.add("salary");
        upload(bearer("hr@test.com"), workbook(extra, List.of()), "x.xlsx", 400);
        upload(bearer("hr@test.com"), workbook(EmployeeBulkImportService.COLUMNS.subList(0, 9), List.of()), "x.xlsx", 400);
    }

    @Test
    void templateHasOnlyTheHeaderRowAndNonHrRolesAreRejected() throws Exception {
        byte[] bytes = mvc.perform(get("/api/v1/employees/bulk-upload/template").header("Authorization", bearer("hr@test.com")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isZero();
            for (int i = 0; i < EmployeeBulkImportService.COLUMNS.size(); i++) {
                assertThat(sheet.getRow(0).getCell(i).getStringCellValue()).isEqualTo(EmployeeBulkImportService.COLUMNS.get(i));
            }
        }
        String employee = bearer("emp@test.com");
        mvc.perform(get("/api/v1/employees/bulk-upload/template").header("Authorization", employee))
                .andExpect(status().isForbidden());
        upload(employee, bytes, "x.xlsx", 403);
    }

    @Test
    void nonExcelFilesAreRejected() throws Exception {
        upload(bearer("hr@test.com"), "a,b".getBytes(), "people.csv", 400);
    }

    @Test
    void exportProducesAFinishedWorkbookForHrAndRespectsFilters() throws Exception {
        String hr = bearer("hr@test.com");
        var res = mvc.perform(get("/api/v1/employees/export").header("Authorization", hr))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(res.getHeader("Content-Disposition")).contains("staffsuite-employees-").contains(".xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(res.getContentAsByteArray()))) {
            assertThat(wb.getSheetName(0)).isEqualTo("Employees");
            assertThat(wb.getSheetName(1)).isEqualTo("Summary");
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).contains("Employee directory");
            Row header = sheet.getRow(4);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Employee code");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("Full name");
            Row first = sheet.getRow(5);
            assertThat(first.getCell(1).getStringCellValue()).isEqualTo("Boss Person");
            assertThat(first.getCell(5).getStringCellValue()).isEqualTo("Engineering");
            assertThat(first.getCell(8).getStringCellValue()).isEqualTo("Active");
            assertThat(first.getCell(9).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(LocalDate.now().minusYears(2));
            assertThat((int) sheet.getPaneInformation().getHorizontalSplitTopRow()).isEqualTo(5);   // frozen under the header
            assertThat(((org.apache.poi.xssf.usermodel.XSSFSheet) sheet).getCTWorksheet().isSetAutoFilter()).isTrue();
            Sheet summary = wb.getSheetAt(1);
            assertThat(summary.getRow(3).getCell(0).getStringCellValue()).isEqualTo("By status");
        }

        // the filters of the screen apply: a department with nobody in it gives an empty (but valid) report
        var none = mvc.perform(get("/api/v1/employees/export?q=nobody-matches-this").header("Authorization", hr))
                .andExpect(status().isOk()).andReturn().getResponse();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(none.getContentAsByteArray()))) {
            assertThat(wb.getSheetAt(0).getRow(5).getCell(0).getStringCellValue()).startsWith("No employees match");
            assertThat(wb.getSheetAt(0).getRow(2).getCell(0).getStringCellValue()).contains("nobody-matches-this");
        }
    }

    @Test
    void exportIsForHrAndAdminOnly() throws Exception {
        mvc.perform(get("/api/v1/employees/export").header("Authorization", bearer("emp@test.com")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/employees/export")).andExpect(status().isUnauthorized());
    }
}
