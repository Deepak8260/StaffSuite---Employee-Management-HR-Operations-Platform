-- Phase 2 migration: reshape `employees` for Employee Management.
-- Run once against a database that was created from the Phase 0/1 schema.sql.
-- A fresh database created from the current schema.sql does NOT need this.
-- Assumes the employees table is still empty (no employees created yet).

USE staffsuite;

ALTER TABLE employees
    DROP COLUMN first_name,
    DROP COLUMN last_name,
    DROP COLUMN gender,
    DROP COLUMN employment_type,
    DROP COLUMN status,
    DROP COLUMN date_of_joining,
    DROP COLUMN date_of_exit,
    ADD COLUMN full_name         VARCHAR(160) NOT NULL AFTER employee_code,
    ADD COLUMN emergency_contact VARCHAR(150) NULL AFTER address,
    ADD COLUMN joining_date      DATE         NOT NULL AFTER date_of_birth,
    ADD COLUMN job_title         VARCHAR(100) NOT NULL AFTER joining_date,
    ADD COLUMN employment_status ENUM('ACTIVE','ON_LEAVE','PROBATION','TERMINATED') NOT NULL DEFAULT 'ACTIVE' AFTER job_title,
    ADD COLUMN is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE AFTER manager_id,
    ADD INDEX idx_employees_full_name (full_name),
    ADD INDEX idx_employees_deleted_status (is_deleted, employment_status);
