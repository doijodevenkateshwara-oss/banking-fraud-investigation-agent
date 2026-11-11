package com.bfs.fraudcase.config;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Venkateshwara Doijode
 */
@Configuration
@EnableConfigurationProperties(CapstoneProperties.class)
public class AppConfig {

    /** A fixed clock (capstone.fixed-clock) makes fixtures, IDs and SLA targets reproducible in tests and demos. */
    @Bean
    Clock clock(CapstoneProperties props) {
        if (props.fixedClock() != null && !props.fixedClock().isBlank()) {
            return Clock.fixed(Instant.parse(props.fixedClock()), ZoneOffset.UTC);
        }
        return Clock.systemUTC();
    }
}
