package com.theotech.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on {@code @Scheduled} (the daily report e-mail). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
