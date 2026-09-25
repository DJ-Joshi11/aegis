package com.aegis.ingestion;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on {@code @Scheduled} support for {@link MockDecisionGenerator}.
 *
 * Kept as its own small config class inside {@code com.aegis.ingestion}
 * rather than added to {@code AegisApplication} directly, so this package
 * stays self-contained and doesn't require editing a file another teammate
 * might also need to touch.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
