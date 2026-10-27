package com.staffsuite.entity;

public enum DocumentVisibility {
    /** The employee, their direct manager and HR/Admin can see it. */
    SELF_AND_HR,
    /** Only HR/Admin can see it; the employee and their manager cannot. */
    HR_ONLY
}
