/** The required Excel format. Kept word for word the same as the backend's header-mismatch message. */
export const BULK_UPLOAD_GUIDE = `The Excel file must contain exactly these column headers in row 1 (spelling, case, and order do not need to match order, but names must match exactly):

full_name       (required, text)
email           (required, text, must be unique)
phone           (optional, text)
date_of_birth   (optional, date — format YYYY-MM-DD)
joining_date    (required, date — format YYYY-MM-DD)
job_title       (required, text)
department      (required, text — must match an existing department name exactly)
designation     (optional, text — must match an existing designation title exactly)
manager_email   (optional, text — email of an existing employee to set as manager)
employment_status (optional, text — one of ACTIVE, PROBATION; defaults to ACTIVE if blank)

Do not rename, remove, or add columns beyond this list. Any row missing a required column value, or containing a department/designation/manager_email that does not already exist in the system, will be rejected and reported as an error for that row — the rest of the valid rows should still be processed.`
