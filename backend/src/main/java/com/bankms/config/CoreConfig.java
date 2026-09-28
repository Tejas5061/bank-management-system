package com.bankms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Configuration
@EnableAsync
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class CoreConfig {

    /**
     * Every "now" in the code base comes from this clock, zoned to the bank's time zone. Business
     * dates (value dates, EMI due dates, FD maturity) are therefore consistent, and tests can pin
     * time by swapping in a fixed clock.
     */
    @Bean
    public Clock clock(AppProperties properties) {
        return Clock.system(properties.bank().timezone());
    }

    @Bean
    public DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(Instant.now(clock));
    }
}
