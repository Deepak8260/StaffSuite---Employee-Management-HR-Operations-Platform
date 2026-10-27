-- Sample data: a generic 2026 national / festival holiday list for demos and testing.
-- Replace with your organisation's official list before real use (Admin -> Public Holidays).
-- Safe to run more than once: dates that already exist are skipped (holiday_date is unique).
-- Festival dates are indicative; verify them against the official calendar you follow.

USE staffsuite;

INSERT IGNORE INTO public_holidays (name, holiday_date, description, is_optional) VALUES
    ('New Year''s Day',        '2026-01-01', 'National holiday',                     FALSE),
    ('Makar Sankranti',        '2026-01-14', 'Harvest festival',                     FALSE),
    ('Republic Day',           '2026-01-26', 'National holiday',                     FALSE),
    ('Ram Navami',             '2026-03-26', 'Festival holiday',                     FALSE),
    ('Mahavir Jayanti',        '2026-03-31', 'Festival holiday (optional)',          TRUE),
    ('Dr. Ambedkar Jayanti',   '2026-04-14', 'National observance',                  FALSE),
    ('Labour Day',             '2026-05-01', 'International Workers'' Day',          FALSE),
    ('Independence Day',       '2026-08-15', 'National holiday',                     FALSE),
    ('Janmashtami',            '2026-09-04', 'Festival holiday',                     FALSE),
    ('Ganesh Chaturthi',       '2026-09-14', 'Festival holiday',                     FALSE),
    ('Gandhi Jayanti',         '2026-10-02', 'National holiday',                     FALSE),
    ('Guru Nanak Jayanti',     '2026-11-24', 'Festival holiday (optional)',          TRUE),
    ('Christmas Eve',          '2026-12-24', 'Optional holiday',                     TRUE),
    ('Christmas Day',          '2026-12-25', 'Festival holiday',                     FALSE);
