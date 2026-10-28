-- Sample data: a starter set of leave types. You can also create these in the app
-- (Admin -> Leave Types). Safe to run more than once: existing names are skipped.
-- Employees get their balance for each type automatically the first time they open My Leave.

USE staffsuite;

INSERT IGNORE INTO leave_types
    (name, description, annual_entitlement_days, carry_forward_allowed, max_carry_forward_days) VALUES
    ('Annual Leave', 'Planned time off',               18, TRUE,  5),
    ('Sick Leave',   'Illness or medical appointments', 10, FALSE, 0),
    ('Casual Leave', 'Short-notice personal time off',   8, FALSE, 0);
