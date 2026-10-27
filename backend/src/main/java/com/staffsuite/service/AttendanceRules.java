package com.staffsuite.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.staffsuite.config.AppProperties;
import com.staffsuite.entity.AttendanceStatus;

/** Working-hours arithmetic and status derivation, driven by the configurable cutoff and half-day threshold. */
@Component
public class AttendanceRules {

    private final AppProperties.Attendance config;

    public AttendanceRules(AppProperties props) {
        this.config = props.attendance();
    }

    public BigDecimal workingHours(LocalDateTime checkIn, LocalDateTime checkOut) {
        long minutes = Duration.between(checkIn, checkOut).toMinutes();
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    /**
     * Status for a day that has a check-in. Less than the half-day threshold of working hours is a
     * HALF_DAY (this takes precedence); otherwise a check-in after the cutoff is LATE; otherwise PRESENT.
     * Without a check-out yet, only lateness can be judged.
     */
    public AttendanceStatus statusFor(LocalDateTime checkIn, LocalDateTime checkOut) {
        if (checkOut != null && workingHours(checkIn, checkOut).compareTo(config.halfDayHours()) < 0) {
            return AttendanceStatus.HALF_DAY;
        }
        return checkIn.toLocalTime().isAfter(config.lateCutoff()) ? AttendanceStatus.LATE : AttendanceStatus.PRESENT;
    }

    public int correctionWindowDays() {
        return config.correctionWindowDays();
    }
}
