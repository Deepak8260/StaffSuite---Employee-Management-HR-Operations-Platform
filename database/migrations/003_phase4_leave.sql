-- Phase 4 migration: reshape the leave tables for Leave Management.
-- Run once against a database created from the Phase 0-3 schema.sql.
-- A fresh database created from the current schema.sql does NOT need this.
-- Assumes the leave tables are still empty (nothing has used them before Phase 4).

USE staffsuite;

-- leave_types: entitlement and carry-forward rules replace code / paid / carry flags
ALTER TABLE leave_types DROP INDEX uq_leave_types_code;

ALTER TABLE leave_types
    DROP COLUMN code,
    DROP COLUMN default_annual_days,
    DROP COLUMN is_paid,
    DROP COLUMN is_carry_forward,
    ADD COLUMN annual_entitlement_days DECIMAL(5,2) NOT NULL DEFAULT 0 AFTER description,
    ADD COLUMN carry_forward_allowed   BOOLEAN      NOT NULL DEFAULT FALSE AFTER annual_entitlement_days,
    ADD COLUMN max_carry_forward_days  DECIMAL(5,2) NOT NULL DEFAULT 0 AFTER carry_forward_allowed;

-- leave_balances: explicit *_days columns, stored remaining_days; `year` -> `balance_year`
ALTER TABLE leave_balances
    CHANGE COLUMN year            balance_year         SMALLINT     NOT NULL,
    CHANGE COLUMN allocated       allocated_days       DECIMAL(5,2) NOT NULL DEFAULT 0,
    CHANGE COLUMN used            used_days            DECIMAL(5,2) NOT NULL DEFAULT 0,
    CHANGE COLUMN carried_forward carried_forward_days DECIMAL(5,2) NOT NULL DEFAULT 0,
    ADD COLUMN remaining_days DECIMAL(5,2) NOT NULL DEFAULT 0 AFTER carried_forward_days;

-- leave_requests: approver naming + attachment.
-- The foreign key on the renamed column is dropped and re-added; renaming it in place can be
-- rejected by MySQL depending on the version.
ALTER TABLE leave_requests DROP FOREIGN KEY fk_leave_requests_reviewer;

ALTER TABLE leave_requests
    CHANGE COLUMN reviewed_by    approver_id      BIGINT        NULL,
    CHANGE COLUMN review_comment approver_comment VARCHAR(1000) NULL,
    ADD COLUMN attachment_url VARCHAR(500) NULL AFTER approver_comment;

ALTER TABLE leave_requests
    ADD CONSTRAINT fk_leave_requests_reviewer FOREIGN KEY (approver_id) REFERENCES employees (id);
