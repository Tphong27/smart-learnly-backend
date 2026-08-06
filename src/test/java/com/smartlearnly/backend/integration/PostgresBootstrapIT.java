package com.smartlearnly.backend.integration;

import org.junit.jupiter.api.Test;

/** Verifies that a clean PostgreSQL container can apply Flyway migrations and start Hibernate validation. */
class PostgresBootstrapIT extends AbstractPostgresIntegrationTest {

    @Test
    void itDb00_startsWithCleanPostgresSchema() {
        // Spring context startup is the assertion for this database bootstrap gate.
    }
}
