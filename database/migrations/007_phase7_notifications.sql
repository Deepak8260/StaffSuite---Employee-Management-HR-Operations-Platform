-- Phase 7 migration: notifications get a recipient, a reference to the item they are about, and lose the title.
-- Run once against a database created from the Phase 0-6 schema.sql.
-- A fresh database created from the current schema.sql does NOT need this.
-- Assumes the notifications table is still empty (nothing has used it before Phase 7).
-- Run the statements in order; if one fails, send me the error before re-running anything.

USE staffsuite;

-- the foreign key and the index it relied on are replaced, so drop them before renaming the column
ALTER TABLE notifications DROP FOREIGN KEY fk_notifications_user;
ALTER TABLE notifications DROP INDEX idx_notifications_user_read;

ALTER TABLE notifications
    CHANGE COLUMN user_id recipient_id BIGINT NOT NULL,
    DROP COLUMN title,
    ADD COLUMN reference_id BIGINT NULL AFTER message,
    ADD INDEX idx_notifications_recipient_read (recipient_id, is_read),
    ADD INDEX idx_notifications_dedupe (recipient_id, type, reference_id);

ALTER TABLE notifications
    ADD CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_id) REFERENCES users (id);
