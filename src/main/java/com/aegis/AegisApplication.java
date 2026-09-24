package com.aegis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AEGIS — AI Exposure Governance & Insurance Shield.
 *
 * Entry point for the single-module Spring Boot app. Each teammate builds
 * inside their own package (com.aegis.ingestion / detection / payout) —
 * see this package's README.md and each package's own README.md for scope.
 */
@SpringBootApplication
public class AegisApplication {

    public static void main(String[] args) {
        SpringApplication.run(AegisApplication.class, args);
    }
}
