-- Adds the permanent, server-generated upload time to employee documents.
-- Run once against a database created before this change. A fresh database created from the
-- current schema.sql does NOT need it.
-- Documents uploaded earlier are given their original creation time as their upload time.

USE staffsuite;

ALTER TABLE employee_documents
    ADD COLUMN uploaded_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) AFTER visibility;

-- "WHERE id > 0" matches every row; it is there because MySQL Workbench's safe update mode refuses an
-- UPDATE that has no key in its WHERE clause.
UPDATE employee_documents SET uploaded_at = created_at WHERE id > 0;
