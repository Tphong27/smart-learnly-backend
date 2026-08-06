package com.smartlearnly.backend.integration;

import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IntegrationDatabaseCleaner {
    private final JdbcTemplate jdbcTemplate;

    public void clean() {
        List<String> tables = jdbcTemplate.queryForList("""
                select tablename
                from pg_tables
                where schemaname = 'public'
                  and tablename <> 'flyway_schema_history'
                order by tablename
                """, String.class);

        if (tables.isEmpty()) {
            return;
        }

        String quotedTables = tables.stream()
                .map(name -> "public.\"" + name.replace("\"", "\"\"") + "\"")
                .collect(Collectors.joining(", "));
        jdbcTemplate.execute("TRUNCATE TABLE " + quotedTables + " RESTART IDENTITY CASCADE");
    }
}