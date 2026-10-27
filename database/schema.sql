-- StaffSuite - MySQL 8 schema (scaffolding; no seed data, no business logic)
-- Usage: mysql -u <user> -p < schema.sql

CREATE DATABASE IF NOT EXISTS staffsuite CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE staffsuite;

-- ---------------------------------------------------------------------------
-- Reference / organisation tables
-- ---------------------------------------------------------------------------

CREATE TABLE departments (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    name             VARCHAR(100) NOT NULL,
    description      VARCHAR(500) NULL,
    head_employee_id BIGINT       NULL,  -- FK added after employees exists
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_departments_name UNIQUE (name)
) ENGINE=InnoDB;

CREATE TABLE designations (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    title         VARCHAR(100) NOT NULL,
    description   VARCHAR(500) NULL,
    department_id BIGINT       NULL,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_designations_title_dept UNIQUE (title, department_id),
    CONSTRAINT fk_designations_department FOREIGN KEY (department_id) REFERENCES departments (id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------------
-- Users & employees
-- ---------------------------------------------------------------------------

CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(150) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          ENUM('SYSTEM_ADMIN','HR_ADMIN','MANAGER','EMPLOYEE') NOT NULL DEFAULT 'EMPLOYEE',
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    employee_id   BIGINT       NULL,  -- FK added after employees exists
    last_login_at TIMESTAMP    NULL,
    reset_token_hash   VARCHAR(64) NULL,  -- SHA-256 of the reset token; raw token is never stored
    reset_token_expiry TIMESTAMP   NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email)
) ENGINE=InnoDB;

CREATE TABLE employees (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    employee_code     VARCHAR(30)  NOT NULL,
    full_name         VARCHAR(160) NOT NULL,
    email             VARCHAR(150) NOT NULL,
    phone             VARCHAR(25)  NULL,
    address           VARCHAR(500) NULL,
    emergency_contact VARCHAR(150) NULL,
    date_of_birth     DATE         NULL,
    joining_date      DATE         NOT NULL,
    job_title         VARCHAR(100) NOT NULL,
    employment_status ENUM('ACTIVE','ON_LEAVE','PROBATION','TERMINATED') NOT NULL DEFAULT 'ACTIVE',
    department_id     BIGINT       NULL,
    designation_id    BIGINT       NULL,
    manager_id        BIGINT       NULL,
    is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE,  -- soft delete (offboarded)
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_employees_code  UNIQUE (employee_code),
    CONSTRAINT uq_employees_email UNIQUE (email),
    INDEX idx_employees_full_name (full_name),
    INDEX idx_employees_deleted_status (is_deleted, employment_status),
    CONSTRAINT fk_employees_department FOREIGN KEY (department_id)  REFERENCES departments (id),
    CONSTRAINT fk_employees_designation FOREIGN KEY (designation_id) REFERENCES designations (id),
    CONSTRAINT fk_employees_manager     FOREIGN KEY (manager_id)     REFERENCES employees (id)
) ENGINE=InnoDB;

ALTER TABLE departments
    ADD CONSTRAINT fk_departments_head FOREIGN KEY (head_employee_id) REFERENCES employees (id);

ALTER TABLE users
    ADD CONSTRAINT uq_users_employee UNIQUE (employee_id),
    ADD CONSTRAINT fk_users_employee FOREIGN KEY (employee_id) REFERENCES employees (id);

-- ---------------------------------------------------------------------------
-- Leave management
-- ---------------------------------------------------------------------------

CREATE TABLE leave_types (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    name                    VARCHAR(80)  NOT NULL,
    description             VARCHAR(500) NULL,
    annual_entitlement_days DECIMAL(5,2) NOT NULL DEFAULT 0,
    carry_forward_allowed   BOOLEAN      NOT NULL DEFAULT FALSE,
    max_carry_forward_days  DECIMAL(5,2) NOT NULL DEFAULT 0,
    is_active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_leave_types_name UNIQUE (name)
) ENGINE=InnoDB;

-- allocated_days = annual entitlement + carried_forward_days; remaining_days = allocated_days - used_days
CREATE TABLE leave_balances (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    employee_id         BIGINT       NOT NULL,
    leave_type_id       BIGINT       NOT NULL,
    balance_year        SMALLINT     NOT NULL,
    allocated_days      DECIMAL(5,2) NOT NULL DEFAULT 0,
    used_days           DECIMAL(5,2) NOT NULL DEFAULT 0,
    carried_forward_days DECIMAL(5,2) NOT NULL DEFAULT 0,
    remaining_days      DECIMAL(5,2) NOT NULL DEFAULT 0,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_leave_balances_emp_type_year UNIQUE (employee_id, leave_type_id, balance_year),
    CONSTRAINT fk_leave_balances_employee   FOREIGN KEY (employee_id)   REFERENCES employees (id),
    CONSTRAINT fk_leave_balances_leave_type FOREIGN KEY (leave_type_id) REFERENCES leave_types (id)
) ENGINE=InnoDB;

CREATE TABLE leave_requests (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    employee_id    BIGINT        NOT NULL,
    leave_type_id  BIGINT        NOT NULL,
    start_date     DATE          NOT NULL,
    end_date       DATE          NOT NULL,
    total_days     DECIMAL(5,2)  NOT NULL,
    reason         VARCHAR(1000) NULL,
    status         ENUM('PENDING','APPROVED','REJECTED','CANCELLED') NOT NULL DEFAULT 'PENDING',
    approver_id      BIGINT        NULL,  -- employee who approved/rejected (NULL if the approver has no employee record)
    reviewed_at      TIMESTAMP     NULL,
    approver_comment VARCHAR(1000) NULL,
    attachment_url   VARCHAR(500)  NULL,  -- relative path under FILE_STORAGE_PATH
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_leave_requests_employee_status (employee_id, status),
    INDEX idx_leave_requests_dates (start_date, end_date),
    CONSTRAINT fk_leave_requests_employee   FOREIGN KEY (employee_id)   REFERENCES employees (id),
    CONSTRAINT fk_leave_requests_leave_type FOREIGN KEY (leave_type_id) REFERENCES leave_types (id),
    CONSTRAINT fk_leave_requests_reviewer   FOREIGN KEY (approver_id)   REFERENCES employees (id),
    CONSTRAINT chk_leave_requests_dates CHECK (end_date >= start_date)
) ENGINE=InnoDB;

CREATE TABLE public_holidays (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    name         VARCHAR(120) NOT NULL,
    holiday_date DATE         NOT NULL,
    description  VARCHAR(500) NULL,
    is_optional  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_public_holidays_date UNIQUE (holiday_date)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------------
-- Attendance
-- ---------------------------------------------------------------------------

CREATE TABLE attendance_records (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    employee_id     BIGINT       NOT NULL,
    attendance_date DATE         NOT NULL,
    check_in_time   DATETIME     NULL,
    check_out_time  DATETIME     NULL,
    work_hours      DECIMAL(5,2) NULL,
    status          ENUM('PRESENT','ABSENT','HALF_DAY','ON_LEAVE','HOLIDAY','WEEKEND') NOT NULL DEFAULT 'PRESENT',
    notes           VARCHAR(500) NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_attendance_employee_date UNIQUE (employee_id, attendance_date),
    CONSTRAINT fk_attendance_employee FOREIGN KEY (employee_id) REFERENCES employees (id)
) ENGINE=InnoDB;

CREATE TABLE attendance_corrections (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    attendance_record_id BIGINT        NOT NULL,
    employee_id          BIGINT        NOT NULL,
    requested_check_in   DATETIME      NULL,
    requested_check_out  DATETIME      NULL,
    reason               VARCHAR(1000) NOT NULL,
    status               ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
    reviewed_by          BIGINT        NULL,
    reviewed_at          TIMESTAMP     NULL,
    review_comment       VARCHAR(1000) NULL,
    created_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_attendance_corrections_status (status),
    CONSTRAINT fk_attendance_corrections_record   FOREIGN KEY (attendance_record_id) REFERENCES attendance_records (id),
    CONSTRAINT fk_attendance_corrections_employee FOREIGN KEY (employee_id)          REFERENCES employees (id),
    CONSTRAINT fk_attendance_corrections_reviewer FOREIGN KEY (reviewed_by)          REFERENCES employees (id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------------
-- Documents
-- ---------------------------------------------------------------------------

CREATE TABLE document_categories (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_document_categories_name UNIQUE (name)
) ENGINE=InnoDB;

CREATE TABLE employee_documents (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    employee_id  BIGINT       NOT NULL,
    category_id  BIGINT       NOT NULL,
    file_name    VARCHAR(255) NOT NULL,
    stored_path  VARCHAR(500) NOT NULL,
    content_type VARCHAR(100) NULL,
    file_size    BIGINT       NULL,
    uploaded_by  BIGINT       NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_employee_documents_employee (employee_id),
    CONSTRAINT fk_employee_documents_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT fk_employee_documents_category FOREIGN KEY (category_id) REFERENCES document_categories (id),
    CONSTRAINT fk_employee_documents_uploader FOREIGN KEY (uploaded_by) REFERENCES users (id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------------
-- Notifications & audit
-- ---------------------------------------------------------------------------

CREATE TABLE notifications (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    user_id    BIGINT        NOT NULL,
    type       VARCHAR(50)   NOT NULL,
    title      VARCHAR(150)  NOT NULL,
    message    VARCHAR(1000) NOT NULL,
    is_read    BOOLEAN       NOT NULL DEFAULT FALSE,
    read_at    TIMESTAMP     NULL,
    created_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_notifications_user_read (user_id, is_read),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE audit_logs (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NULL,
    action      VARCHAR(50) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id   BIGINT      NULL,
    details     JSON        NULL,
    ip_address  VARCHAR(45) NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_audit_logs_entity (entity_type, entity_id),
    INDEX idx_audit_logs_user (user_id),
    CONSTRAINT fk_audit_logs_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;
