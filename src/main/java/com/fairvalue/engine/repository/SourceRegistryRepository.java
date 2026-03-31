package com.fairvalue.engine.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class SourceRegistryRepository {
    private final JdbcClient jdbcClient;

    public SourceRegistryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<Long> findIdBySourceName(String sourceName) {
        return jdbcClient.sql("""
                        SELECT id
                        FROM fairvalue.source_registry
                        WHERE source_name = :sourceName
                        LIMIT 1
                        """)
                .param("sourceName", sourceName)
                .query(Long.class)
                .optional();
    }
}
