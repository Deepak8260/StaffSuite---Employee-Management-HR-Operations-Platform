package com.staffsuite.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.staffsuite.dto.EmployeeDtos.EmployeeResponse;
import com.staffsuite.entity.EmploymentStatus;
import com.staffsuite.exception.ApiException;
import com.staffsuite.repository.DepartmentRepository;
import com.staffsuite.repository.EmployeeRepository;
import com.staffsuite.security.AuthenticatedUser;

import lombok.RequiredArgsConstructor;

/**
 * The Employees list as a finished Excel report: a styled sheet with the same filters the screen had applied,
 * and a summary sheet with headcount by status and by department. HR/Admin only (the caller enforces that).
 */
@Service
@RequiredArgsConstructor
public class EmployeeExportService {

    /** More than this and the file is better produced by a proper reporting job than by a button. */
    static final int MAX_ROWS = 10_000;

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);
    private static final int HEADER_ROW = 5;

    private static final String[] HEADERS = { "Employee code", "Full name", "Email", "Phone", "Job title", "Department",
            "Designation", "Manager", "Status", "Joining date", "Tenure", "Date of birth", "Address", "Emergency contact" };
    private static final int[] WIDTHS = { 15, 26, 32, 16, 28, 22, 24, 24, 15, 14, 12, 14, 38, 28 };

    private final EmployeeService employeeService;
    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditService auditService;
    private final Clock clock;

    public record Export(byte[] content, String fileName) {
    }

    public Export export(AuthenticatedUser actor, Long departmentId, EmploymentStatus status, Long managerId, String search,
            boolean includeOffboarded, String ip) {
        List<EmployeeResponse> rows = employeeService.list(actor, departmentId, status, managerId, search, includeOffboarded,
                PageRequest.of(0, MAX_ROWS + 1, Sort.by("employeeCode"))).content();
        if (rows.size() > MAX_ROWS) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Too many employees to export at once (more than " + MAX_ROWS + "). Narrow the filters first.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        List<String> filters = describeFilters(departmentId, status, managerId, search, includeOffboarded);

        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles st = new Styles(wb);
            writeEmployees(wb, st, rows, filters, now);
            writeSummary(wb, st, rows, filters, now);
            wb.getProperties().getCoreProperties().setTitle("StaffSuite employee export");
            wb.getProperties().getCoreProperties().setCreator("StaffSuite");
            wb.write(out);
            auditService.log(actor.userId(), "EMPLOYEES_EXPORTED", "EMPLOYEE", null, ip);
            return new Export(out.toByteArray(), "staffsuite-employees-" + now.toLocalDate() + ".xlsx");
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not build the Excel file");
        }
    }

    // ----------------------------------------------------------------- sheets

    private void writeEmployees(XSSFWorkbook wb, Styles st, List<EmployeeResponse> rows, List<String> filters, LocalDateTime now) {
        Sheet sheet = wb.createSheet("Employees");
        sheet.setDisplayGridlines(false);
        int last = HEADERS.length - 1;

        banner(sheet, st, "StaffSuite  |  Employee directory", last, 0);
        Row meta = sheet.createRow(1);
        text(meta, 0, "Generated " + STAMP.format(now) + "   |   " + rows.size() + " employee" + (rows.size() == 1 ? "" : "s"), st.meta);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, last));
        Row filterRow = sheet.createRow(2);
        text(filterRow, 0, "Filters: " + (filters.isEmpty() ? "none (everyone, excluding offboarded)" : String.join("; ", filters)), st.meta);
        sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, last));

        Row header = sheet.createRow(HEADER_ROW - 1);
        header.setHeightInPoints(26);
        for (int c = 0; c < HEADERS.length; c++) {
            text(header, c, HEADERS[c], st.header);
            sheet.setColumnWidth(c, WIDTHS[c] * 256);
        }

        LocalDate today = now.toLocalDate();
        int r = HEADER_ROW;
        for (EmployeeResponse e : rows) {
            boolean zebra = (r - HEADER_ROW) % 2 == 1;
            Row row = sheet.createRow(r++);
            row.setHeightInPoints(20);
            text(row, 0, e.employeeCode(), zebra ? st.bodyAlt : st.body);
            text(row, 1, e.fullName(), zebra ? st.nameAlt : st.name);
            text(row, 2, e.email(), zebra ? st.bodyAlt : st.body);
            text(row, 3, e.phone(), zebra ? st.bodyAlt : st.body);
            text(row, 4, e.jobTitle(), zebra ? st.bodyAlt : st.body);
            text(row, 5, e.departmentName(), zebra ? st.bodyAlt : st.body);
            text(row, 6, e.designationName(), zebra ? st.bodyAlt : st.body);
            text(row, 7, e.managerName(), zebra ? st.bodyAlt : st.body);
            text(row, 8, statusLabel(e), st.status(e.deleted() ? null : e.employmentStatus()));
            date(row, 9, e.joiningDate(), zebra ? st.dateAlt : st.date);
            text(row, 10, tenure(e.joiningDate(), today), zebra ? st.centerAlt : st.center);
            date(row, 11, e.dateOfBirth(), zebra ? st.dateAlt : st.date);
            text(row, 12, e.address(), zebra ? st.bodyAlt : st.body);
            text(row, 13, e.emergencyContact(), zebra ? st.bodyAlt : st.body);
        }
        if (rows.isEmpty()) {
            Row empty = sheet.createRow(r++);
            text(empty, 0, "No employees match these filters.", st.meta);
            sheet.addMergedRegion(new CellRangeAddress(r - 1, r - 1, 0, last));
        } else {
            sheet.setAutoFilter(new CellRangeAddress(HEADER_ROW - 1, r - 1, 0, last));
        }
        Row total = sheet.createRow(r + 1);
        text(total, 0, "Total employees: " + rows.size(), st.total);
        sheet.addMergedRegion(new CellRangeAddress(r + 1, r + 1, 0, last));

        sheet.createFreezePane(2, HEADER_ROW);        // header row and the code/name columns stay in view
        print(sheet, HEADER_ROW);
    }

    private void writeSummary(XSSFWorkbook wb, Styles st, List<EmployeeResponse> rows, List<String> filters, LocalDateTime now) {
        Sheet sheet = wb.createSheet("Summary");
        sheet.setDisplayGridlines(false);
        sheet.setColumnWidth(0, 34 * 256);
        sheet.setColumnWidth(1, 14 * 256);
        sheet.setColumnWidth(2, 14 * 256);
        banner(sheet, st, "StaffSuite  |  Headcount summary", 2, 0);
        text(sheet.createRow(1), 0, "Generated " + STAMP.format(now) + ", for the " + rows.size() + " employees in the export", st.meta);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 2));

        Map<String, Integer> byStatus = new LinkedHashMap<>();
        for (EmploymentStatus s : EmploymentStatus.values()) {
            byStatus.put(label(s), 0);
        }
        Map<String, Integer> byDepartment = new LinkedHashMap<>();
        for (EmployeeResponse e : rows) {
            byStatus.merge(e.deleted() ? "Offboarded" : label(e.employmentStatus()), 1, Integer::sum);
            byDepartment.merge(e.departmentName() == null ? "No department" : e.departmentName(), 1, Integer::sum);
        }
        byStatus.entrySet().removeIf(en -> en.getValue() == 0);

        int r = 3;
        r = table(sheet, st, r, "By status", byStatus, rows.size());
        r = table(sheet, st, r + 1, "By department", sorted(byDepartment), rows.size());
        print(sheet, -1);
        sheet.setFitToPage(true);
    }

    private static Map<String, Integer> sorted(Map<String, Integer> m) {
        List<Map.Entry<String, Integer>> list = new ArrayList<>(m.entrySet());
        list.sort((a, b) -> b.getValue() != a.getValue().intValue() ? b.getValue() - a.getValue() : a.getKey().compareTo(b.getKey()));
        Map<String, Integer> out = new LinkedHashMap<>();
        list.forEach(en -> out.put(en.getKey(), en.getValue()));
        return out;
    }

    private int table(Sheet sheet, Styles st, int startRow, String title, Map<String, Integer> data, int total) {
        Row head = sheet.createRow(startRow);
        head.setHeightInPoints(22);
        text(head, 0, title, st.header);
        text(head, 1, "Employees", st.header);
        text(head, 2, "Share", st.header);
        int r = startRow + 1;
        for (Map.Entry<String, Integer> en : data.entrySet()) {
            boolean zebra = (r - startRow) % 2 == 0;
            Row row = sheet.createRow(r++);
            text(row, 0, en.getKey(), zebra ? st.bodyAlt : st.body);
            number(row, 1, en.getValue(), zebra ? st.centerAlt : st.center);
            Cell share = row.createCell(2);
            share.setCellValue(total == 0 ? 0 : en.getValue() / (double) total);
            share.setCellStyle(zebra ? st.percentAlt : st.percent);
        }
        Row sum = sheet.createRow(r++);
        text(sum, 0, "Total", st.total);
        Cell n = sum.createCell(1);
        n.setCellValue(total);
        n.setCellStyle(st.totalNumber);
        Cell p = sum.createCell(2);
        p.setCellValue(total == 0 ? 0 : 1.0);
        p.setCellStyle(st.totalPercent);
        return r;
    }

    // ---------------------------------------------------------------- helpers

    private static void banner(Sheet sheet, Styles st, String title, int lastColumn, int rowIndex) {
        Row row = sheet.createRow(rowIndex);
        row.setHeightInPoints(34);
        for (int c = 0; c <= lastColumn; c++) {
            row.createCell(c).setCellStyle(st.title);
        }
        row.getCell(0).setCellValue(title);
        sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, lastColumn));
    }

    private static void print(Sheet sheet, int repeatHeaderRow) {
        sheet.getPrintSetup().setLandscape(true);
        sheet.getPrintSetup().setPaperSize((short) 9);   // A4
        sheet.setFitToPage(true);
        sheet.getPrintSetup().setFitWidth((short) 1);
        sheet.getPrintSetup().setFitHeight((short) 0);
        sheet.setHorizontallyCenter(true);
        sheet.getFooter().setLeft("StaffSuite");
        sheet.getFooter().setRight("Page &P of &N");
        if (repeatHeaderRow > 0) {
            sheet.setRepeatingRows(new CellRangeAddress(repeatHeaderRow - 1, repeatHeaderRow - 1, 0, HEADERS.length - 1));
        }
    }

    private List<String> describeFilters(Long departmentId, EmploymentStatus status, Long managerId, String search, boolean includeOffboarded) {
        List<String> out = new ArrayList<>();
        if (search != null && !search.isBlank()) {
            out.add("search \"" + search.trim() + "\"");
        }
        if (departmentId != null) {
            out.add("department " + departmentRepository.findById(departmentId).map(d -> d.getName()).orElse("#" + departmentId));
        }
        if (status != null) {
            out.add("status " + label(status));
        }
        if (managerId != null) {
            out.add("manager " + employeeRepository.findById(managerId).map(m -> m.getFullName()).orElse("#" + managerId));
        }
        if (includeOffboarded) {
            out.add("including offboarded");
        }
        return out;
    }

    private static String statusLabel(EmployeeResponse e) {
        return e.deleted() ? "Offboarded" : label(e.employmentStatus());
    }

    private static String label(EmploymentStatus s) {
        return switch (s) {
            case ACTIVE -> "Active";
            case ON_LEAVE -> "On leave";
            case PROBATION -> "Probation";
            case TERMINATED -> "Terminated";
        };
    }

    private static String tenure(LocalDate joined, LocalDate today) {
        if (joined == null || joined.isAfter(today)) {
            return "";
        }
        Period p = Period.between(joined, today);
        if (p.getYears() > 0) {
            return p.getYears() + "y " + p.getMonths() + "m";
        }
        return p.getMonths() + "m";
    }

    private static void text(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellStyle(style);
        if (value != null) {
            cell.setCellValue(value);
        }
    }

    private static void number(Row row, int column, int value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellStyle(style);
        cell.setCellValue(value);
    }

    private static void date(Row row, int column, LocalDate value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellStyle(style);
        if (value != null) {
            cell.setCellValue(value);
        }
    }

    // ----------------------------------------------------------------- styles

    private static final class Styles {
        final CellStyle title;
        final CellStyle meta;
        final CellStyle header;
        final CellStyle body;
        final CellStyle bodyAlt;
        final CellStyle name;
        final CellStyle nameAlt;
        final CellStyle center;
        final CellStyle centerAlt;
        final CellStyle date;
        final CellStyle dateAlt;
        final CellStyle percent;
        final CellStyle percentAlt;
        final CellStyle total;
        final CellStyle totalNumber;
        final CellStyle totalPercent;
        private final XSSFWorkbook wb;
        private final Map<String, CellStyle> statusStyles = new LinkedHashMap<>();

        private static final byte[] NAVY = { (byte) 0x1F, (byte) 0x3A, (byte) 0x6E };
        private static final byte[] ALT = { (byte) 0xF3, (byte) 0xF6, (byte) 0xFB };
        private static final byte[] GRID = { (byte) 0xD5, (byte) 0xDB, (byte) 0xE6 };

        Styles(XSSFWorkbook wb) {
            this.wb = wb;
            CreationHelper helper = wb.getCreationHelper();
            XSSFFont titleFont = font(16, true, new byte[] { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF });
            title = style(false, NAVY, HorizontalAlignment.LEFT, titleFont, false);
            ((XSSFCellStyle) title).setIndention((short) 1);
            XSSFFont metaFont = font(10, false, new byte[] { 0x66, 0x70, (byte) 0x85 });
            metaFont.setItalic(true);
            meta = style(false, null, HorizontalAlignment.LEFT, metaFont, false);
            header = style(true, NAVY, HorizontalAlignment.CENTER, font(11, true, new byte[] { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF }), true);
            XSSFFont normal = font(10, false, new byte[] { 0x1A, 0x22, 0x33 });
            XSSFFont bold = font(10, true, new byte[] { 0x1A, 0x22, 0x33 });
            body = style(true, null, HorizontalAlignment.LEFT, normal, false);
            bodyAlt = style(true, ALT, HorizontalAlignment.LEFT, normal, false);
            name = style(true, null, HorizontalAlignment.LEFT, bold, false);
            nameAlt = style(true, ALT, HorizontalAlignment.LEFT, bold, false);
            center = style(true, null, HorizontalAlignment.CENTER, normal, false);
            centerAlt = style(true, ALT, HorizontalAlignment.CENTER, normal, false);
            date = style(true, null, HorizontalAlignment.CENTER, normal, false);
            date.setDataFormat(helper.createDataFormat().getFormat("dd-mmm-yyyy"));
            dateAlt = style(true, ALT, HorizontalAlignment.CENTER, normal, false);
            dateAlt.setDataFormat(helper.createDataFormat().getFormat("dd-mmm-yyyy"));
            percent = style(true, null, HorizontalAlignment.CENTER, normal, false);
            percent.setDataFormat(helper.createDataFormat().getFormat("0.0%"));
            percentAlt = style(true, ALT, HorizontalAlignment.CENTER, normal, false);
            percentAlt.setDataFormat(helper.createDataFormat().getFormat("0.0%"));
            XSSFFont totalFont = font(11, true, new byte[] { 0x1F, 0x3A, 0x6E });
            total = style(true, new byte[] { (byte) 0xE4, (byte) 0xEC, (byte) 0xFB }, HorizontalAlignment.LEFT, totalFont, false);
            totalNumber = style(true, new byte[] { (byte) 0xE4, (byte) 0xEC, (byte) 0xFB }, HorizontalAlignment.CENTER, totalFont, false);
            totalPercent = style(true, new byte[] { (byte) 0xE4, (byte) 0xEC, (byte) 0xFB }, HorizontalAlignment.CENTER, totalFont, false);
            totalPercent.setDataFormat(helper.createDataFormat().getFormat("0%"));
        }

        /** A coloured pill-like cell per status (null = offboarded). */
        CellStyle status(EmploymentStatus s) {
            String key = s == null ? "OFF" : s.name();
            return statusStyles.computeIfAbsent(key, k -> {
                byte[] fill;
                byte[] ink;
                switch (k) {
                    case "ACTIVE" -> { fill = new byte[] { (byte) 0xD9, (byte) 0xF2, (byte) 0xE1 }; ink = new byte[] { 0x1B, 0x7F, 0x3B }; }
                    case "PROBATION" -> { fill = new byte[] { (byte) 0xFD, (byte) 0xF0, (byte) 0xC8 }; ink = new byte[] { (byte) 0x9A, 0x64, 0x00 }; }
                    case "ON_LEAVE" -> { fill = new byte[] { (byte) 0xDB, (byte) 0xEA, (byte) 0xFE }; ink = new byte[] { 0x1D, 0x4E, (byte) 0xD8 }; }
                    default -> { fill = new byte[] { (byte) 0xEC, (byte) 0xEF, (byte) 0xF4 }; ink = new byte[] { 0x4D, 0x58, 0x6B }; }
                }
                return style(true, fill, HorizontalAlignment.CENTER, font(10, true, ink), false);
            });
        }

        private XSSFFont font(int size, boolean bold, byte[] rgb) {
            XSSFFont f = wb.createFont();
            f.setFontName("Calibri");
            f.setFontHeightInPoints((short) size);
            f.setBold(bold);
            f.setColor(new XSSFColor(rgb, null));
            return f;
        }

        private XSSFCellStyle style(boolean border, byte[] fill, HorizontalAlignment align, Font font, boolean wrap) {
            XSSFCellStyle s = wb.createCellStyle();
            s.setFont(font);
            s.setAlignment(align);
            s.setVerticalAlignment(VerticalAlignment.CENTER);
            s.setWrapText(wrap);
            if (fill != null) {
                s.setFillForegroundColor(new XSSFColor(fill, null));
                s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            if (border) {
                s.setBorderBottom(BorderStyle.THIN);
                s.setBorderTop(BorderStyle.THIN);
                s.setBorderLeft(BorderStyle.THIN);
                s.setBorderRight(BorderStyle.THIN);
                XSSFColor grid = new XSSFColor(GRID, null);
                s.setBottomBorderColor(grid);
                s.setTopBorderColor(grid);
                s.setLeftBorderColor(grid);
                s.setRightBorderColor(grid);
            }
            return s;
        }
    }
}
