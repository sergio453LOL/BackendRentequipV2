package com.rentequip.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on {@code @Scheduled} jobs, such as the nightly reset of the shared demo account. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
