-- Phase 6 migration: employee_documents gets a storage reference, expiry date and visibility.
-- Run once against a database created from the Phase 0-5 schema.sql.
-- A fresh database created from the current schema.sql does NOT need this.
-- Assumes employee_documents is still empty (nothing has used it before Phase 6).

USE staffsuite;

ALTER TABLE employee_documents
    CHANGE COLUMN stored_path file_url VARCHAR(500) NOT NULL,
    ADD COLUMN expiry_date DATE NULL AFTER uploaded_by,
    ADD COLUMN visibility ENUM('SELF_AND_HR','HR_ONLY') NOT NULL DEFAULT 'SELF_AND_HR' AFTER expiry_date,
    ADD INDEX idx_employee_documents_expiry (expiry_date);
