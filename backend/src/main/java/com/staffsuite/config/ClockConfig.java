package com.staffsuite.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** One shared clock so "today" and "now" for attendance use the configured zone (and can be faked in tests). */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(AppProperties props) {
        return Clock.system(ZoneId.of(props.attendance().timezone()));
    }
}
