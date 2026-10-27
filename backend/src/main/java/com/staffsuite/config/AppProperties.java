package com.staffsuite.config;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "staffsuite")
public record AppProperties(Cors cors, Jwt jwt, Storage storage, Bootstrap bootstrap, PasswordReset passwordReset,
        Attendance attendance, Documents documents) {

    public record Cors(List<String> allowedOrigins) {
    }

    public record Jwt(String secret, long expiryMs) {
    }

    /** type "local" stores files under {@code path}; a future implementation can register another type. */
    public record Storage(String path, String type) {
    }

    public record Bootstrap(String adminEmail, String adminPassword) {
    }

    public record PasswordReset(long expiryMinutes) {
    }

    /**
     * lateCutoff: a check-in after this time of day is LATE.
     * halfDayHours: working hours below this make the day a HALF_DAY.
     * timezone: the zone used to decide "today" and the wall-clock times that are stored.
     * correctionWindowDays: how far back a correction may be requested.
     */
    public record Attendance(LocalTime lateCutoff, BigDecimal halfDayHours, String timezone, int correctionWindowDays) {
    }

    /** expiryWindowDays: default look-ahead for the "expiring documents" list. */
    public record Documents(int expiryWindowDays) {
    }
}
