package com.example.AndesStay.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ClockConfig {
    @Bean
    public Clock businessClock(@Value("${APP_TIME_ZONE:America/Santiago}") String timeZone) {
        return Clock.system(ZoneId.of(timeZone));
    }
}
