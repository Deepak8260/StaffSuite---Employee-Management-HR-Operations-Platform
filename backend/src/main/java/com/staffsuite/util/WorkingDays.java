package com.staffsuite.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/** Working-day arithmetic: Monday-Friday, minus the supplied holiday dates. */
public final class WorkingDays {

    private WorkingDays() {
    }

    public static boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    public static boolean isWorkingDay(LocalDate date, Set<LocalDate> holidays) {
        return !isWeekend(date) && !holidays.contains(date);
    }

    /** Number of working days in [start, end] inclusive. */
    public static int count(LocalDate start, LocalDate end, Set<LocalDate> holidays) {
        int days = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (isWorkingDay(d, holidays)) {
                days++;
            }
        }
        return days;
    }
}
