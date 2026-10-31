-- Profile completion: remember when an employee first completed their own profile, so the
-- "profile completed" notification goes out only once.
-- Run once against a database created before this change. A fresh database created from the
-- current schema.sql does NOT need it.

USE staffsuite;

ALTER TABLE employees
    ADD COLUMN profile_completed_at TIMESTAMP NULL AFTER profile_photo_url;
