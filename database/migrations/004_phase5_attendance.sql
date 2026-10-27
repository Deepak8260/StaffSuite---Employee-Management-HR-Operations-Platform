-- Phase 5 migration: reshape the attendance tables for Attendance Management.
-- Run once against a database created from the Phase 0-4 schema.sql.
-- A fresh database created from the current schema.sql does NOT need this.
-- Assumes the attendance tables are still empty (nothing has used them before Phase 5).
-- Run the statements in order; if one fails, send me the error before re-running anything.

USE staffsuite;

-- attendance_records: working_hours naming, and LATE replaces HOLIDAY / WEEKEND as a status
ALTER TABLE attendance_records
    CHANGE COLUMN work_hours working_hours DECIMAL(5,2) NULL,
    MODIFY COLUMN status ENUM('PRESENT','ABSENT','HALF_DAY','LATE','ON_LEAVE') NOT NULL DEFAULT 'PRESENT';

-- attendance_corrections: employee_id -> requested_by (foreign key dropped and re-added around the rename)
ALTER TABLE attendance_corrections DROP FOREIGN KEY fk_attendance_corrections_employee;

ALTER TABLE attendance_corrections
    CHANGE COLUMN employee_id requested_by BIGINT NOT NULL;

ALTER TABLE attendance_corrections
    ADD CONSTRAINT fk_attendance_corrections_employee FOREIGN KEY (requested_by) REFERENCES employees (id);
