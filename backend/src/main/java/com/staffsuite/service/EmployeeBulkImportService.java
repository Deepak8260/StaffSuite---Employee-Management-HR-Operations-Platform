package com.staffsuite.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.staffsuite.dto.EmployeeDtos.BulkUploadResult;
import com.staffsuite.dto.EmployeeDtos.BulkUploadRowError;
import com.staffsuite.dto.EmployeeDtos.EmployeeRequest;
import com.staffsuite.entity.Department;
import com.staffsuite.entity.Designation;
import com.staffsuite.entity.Employee;
import com.staffsuite.entity.EmploymentStatus;
import com.staffsuite.exception.ApiException;
import com.staffsuite.repository.DepartmentRepository;
import com.staffsuite.repository.DesignationRepository;
import com.staffsuite.repository.EmployeeRepository;
import com.staffsuite.security.AuthenticatedUser;

import lombok.RequiredArgsConstructor;

/**
 * Creates employees from an Excel sheet. Every row goes through {@link EmployeeService#create}, so codes, leave balances,
 * duplicate checks and audit entries behave exactly as for a single new employee. Each row is its own transaction:
 * a bad row is reported and skipped, the good ones are kept.
 */
@Service
@RequiredArgsConstructor
public class EmployeeBulkImportService {

    public static final List<String> COLUMNS = List.of("full_name", "email", "phone", "date_of_birth", "joining_date",
            "job_title", "department", "designation", "manager_email", "employment_status");

    /** Shown on screen next to the file picker and returned verbatim when the header row is wrong. */
    public static final String COLUMN_GUIDE = """
            The Excel file must contain exactly these column headers in row 1 (spelling, case, and order do not need to match order, but names must match exactly):

            full_name       (required, text)
            email           (required, text, must be unique)
            phone           (optional, text)
            date_of_birth   (optional, date — format YYYY-MM-DD)
            joining_date    (required, date — format YYYY-MM-DD)
            job_title       (required, text)
            department      (required, text — must match an existing department name exactly)
            designation     (optional, text — must match an existing designation title exactly)
            manager_email   (optional, text — email of an existing employee to set as manager)
            employment_status (optional, text — one of ACTIVE, PROBATION; defaults to ACTIVE if blank)

            Do not rename, remove, or add columns beyond this list. Any row missing a required column value, or containing a department/designation/manager_email that does not already exist in the system, will be rejected and reported as an error for that row — the rest of the valid rows should still be processed.""";

    private static final int MAX_ROWS = 1000;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final EmployeeService employeeService;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;
    private final AuditService auditService;

    /** A blank workbook holding only the header row. */
    public byte[] template() {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Employees");
            Row header = sheet.createRow(0);
            for (int i = 0; i < COLUMNS.size(); i++) {
                header.createCell(i).setCellValue(COLUMNS.get(i));
                sheet.setColumnWidth(i, 20 * 256);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not build the template");
        }
    }

    public BulkUploadResult importFile(AuthenticatedUser actor, MultipartFile file, String ip) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (file.isEmpty() || !(name.endsWith(".xlsx") || name.endsWith(".xls"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please upload an Excel file (.xlsx or .xls)");
        }
        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
            Map<String, Integer> columns = readHeader(sheet);

            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            List<BulkUploadRowError> errors = new ArrayList<>();
            int total = 0;
            int created = 0;
            int last = sheet.getLastRowNum();
            if (last > MAX_ROWS) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "The file has more than " + MAX_ROWS + " rows. Split it into smaller files.");
            }
            for (int r = 1; r <= last; r++) {
                Row row = sheet.getRow(r);
                Map<String, String> values = readRow(row, columns, formatter);
                if (values.values().stream().allMatch(String::isEmpty)) {
                    continue; // blank line
                }
                total++;
                int excelRow = r + 1;
                try {
                    employeeService.create(actor, toRequest(values), ip);
                    created++;
                } catch (ApiException | AccessDeniedException e) {
                    errors.add(new BulkUploadRowError(excelRow, values.get("email"), values.get("full_name"),
                            e instanceof ApiException ? e.getMessage() : "Not allowed"));
                } catch (RuntimeException e) {
                    errors.add(new BulkUploadRowError(excelRow, values.get("email"), values.get("full_name"),
                            "Could not be saved"));
                }
            }
            auditService.log(actor.userId(), "EMPLOYEES_BULK_UPLOADED", "EMPLOYEE", null, ip);
            return new BulkUploadResult(total, created, errors.size(), errors);
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException api) {
                throw api;
            }
            throw new ApiException(HttpStatus.BAD_REQUEST, "The file could not be read as an Excel workbook");
        }
    }

    // ------------------------------------------------------------------ parsing

    private static Map<String, Integer> readHeader(Sheet sheet) {
        Row header = sheet == null ? null : sheet.getRow(0);
        Map<String, Integer> found = new LinkedHashMap<>();
        boolean problem = header == null;
        if (header != null) {
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            for (int c = 0; c < header.getLastCellNum(); c++) {
                String title = formatter.formatCellValue(header.getCell(c)).trim();
                if (title.isEmpty()) {
                    continue;
                }
                if (!COLUMNS.contains(title) || found.putIfAbsent(title, c) != null) {
                    problem = true;
                }
            }
            problem = problem || found.size() != COLUMNS.size();
        }
        if (problem) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The header row (row 1) does not match.\n\n" + COLUMN_GUIDE);
        }
        return found;
    }

    private static Map<String, String> readRow(Row row, Map<String, Integer> columns, DataFormatter formatter) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String column : COLUMNS) {
            Cell cell = row == null ? null : row.getCell(columns.get(column));
            values.put(column, cellText(cell, formatter));
        }
        return values;
    }

    private static String cellText(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return "";
        }
        boolean numeric = cell.getCellType() == CellType.NUMERIC
                || (cell.getCellType() == CellType.FORMULA && cell.getCachedFormulaResultType() == CellType.NUMERIC);
        if (numeric && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate().toString();
        }
        return formatter.formatCellValue(cell).trim();
    }

    private EmployeeRequest toRequest(Map<String, String> v) {
        require(v, "full_name", "email", "joining_date", "job_title", "department");
        String email = v.get("email").toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(email).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "email '" + v.get("email") + "' is not a valid email address");
        }
        limit(v, "full_name", 160);
        limit(v, "email", 150);
        limit(v, "phone", 25);
        limit(v, "job_title", 100);

        Department department = departmentRepository.findByNameIgnoreCase(v.get("department"))
                .filter(Department::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
                        "department '" + v.get("department") + "' not found"));

        Long designationId = null;
        if (!v.get("designation").isEmpty()) {
            designationId = findDesignation(v.get("designation"), department).getId();
        }
        Long managerId = null;
        if (!v.get("manager_email").isEmpty()) {
            managerId = employeeRepository.findByEmailIgnoreCase(v.get("manager_email")).filter(m -> !m.isDeleted())
                    .map(Employee::getId)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
                            "manager_email '" + v.get("manager_email") + "' not found"));
        }
        return new EmployeeRequest(v.get("full_name"), email, blankToNull(v.get("phone")), null, null,
                date(v, "date_of_birth"), date(v, "joining_date"), v.get("job_title"), status(v.get("employment_status")),
                department.getId(), designationId, managerId, null);
    }

    private Designation findDesignation(String title, Department department) {
        List<Designation> matches = designationRepository.findByTitleIgnoreCaseAndActiveTrue(title);
        if (matches.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "designation '" + title + "' not found");
        }
        List<Designation> inDepartment = matches.stream()
                .filter(d -> d.getDepartmentId() == null || d.getDepartmentId().equals(department.getId())).toList();
        if (inDepartment.size() == 1) {
            return inDepartment.get(0);
        }
        if (inDepartment.isEmpty() && matches.size() == 1) {
            return matches.get(0);
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "designation '" + title + "' matches more than one designation");
    }

    private static EmploymentStatus status(String value) {
        if (value.isEmpty()) {
            return EmploymentStatus.ACTIVE;
        }
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "ACTIVE" -> EmploymentStatus.ACTIVE;
            case "PROBATION" -> EmploymentStatus.PROBATION;
            default -> throw new ApiException(HttpStatus.BAD_REQUEST,
                    "employment_status '" + value + "' must be ACTIVE or PROBATION");
        };
    }

    private static LocalDate date(Map<String, String> v, String column) {
        String text = v.get(column);
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, column + " '" + text + "' must be a date in the format YYYY-MM-DD");
        }
    }

    private static void require(Map<String, String> v, String... columns) {
        List<String> missing = new ArrayList<>();
        for (String c : columns) {
            if (v.get(c).isEmpty()) {
                missing.add(c);
            }
        }
        if (!missing.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Missing required value: " + String.join(", ", missing));
        }
    }

    private static void limit(Map<String, String> v, String column, int max) {
        if (v.get(column).length() > max) {
            throw new ApiException(HttpStatus.BAD_REQUEST, column + " is longer than " + max + " characters");
        }
    }

    private static String blankToNull(String value) {
        return value.isEmpty() ? null : value;
    }
}
