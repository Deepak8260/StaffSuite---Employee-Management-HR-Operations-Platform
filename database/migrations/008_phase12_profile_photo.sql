-- Phase 12: profile photo reference on employees.
-- Run once against a database created before this change. A fresh database created from the
-- current schema.sql does NOT need it.
-- Only the storage reference is kept here; the image itself lives in file storage.

USE staffsuite;

ALTER TABLE employees
    ADD COLUMN profile_photo_url VARCHAR(500) NULL AFTER manager_id;
