package com.staffsuite;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** A clock tests can move, so check-in times, "today" and cutoffs are deterministic. */
class MutableClock extends Clock {

    private final ZoneId zone;
    private volatile Instant instant;

    MutableClock(ZoneId zone, LocalDateTime start) {
        this.zone = zone;
        this.instant = start.atZone(zone).toInstant();
    }

    void set(LocalDateTime dateTime) {
        this.instant = dateTime.atZone(zone).toInstant();
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return new MutableClock(newZone, LocalDateTime.ofInstant(instant, newZone));
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
