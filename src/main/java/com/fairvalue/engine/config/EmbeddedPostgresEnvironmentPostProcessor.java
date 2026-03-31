package com.fairvalue.engine.config;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class EmbeddedPostgresEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    private static final String PROPERTY_SOURCE_NAME = "embeddedPostgresProperties";
    private static final Object MONITOR = new Object();
    private static final AtomicBoolean SHUTDOWN_HOOK_REGISTERED = new AtomicBoolean(false);

    private static volatile EmbeddedPostgres embeddedPostgres;
    private static volatile Integer actualPort;
    private static volatile String databaseName;

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        boolean enabled = environment.getProperty("app.postgres.embedded.enabled", Boolean.class, true);
        String configuredDatasourceUrl = environment.getProperty("spring.datasource.url");
        if (!enabled || StringUtils.hasText(configuredDatasourceUrl)) {
            return;
        }

        startIfNecessary(environment);
        registerDatasourceProperties(environment);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private void startIfNecessary(ConfigurableEnvironment environment) {
        if (embeddedPostgres != null) {
            return;
        }

        synchronized (MONITOR) {
            if (embeddedPostgres != null) {
                return;
            }

            try {
                int requestedPort = environment.getProperty("app.postgres.embedded.port", Integer.class, 5432);
                String requestedDatabase = environment.getProperty("app.postgres.embedded.database", "fairvalue");
                validateDatabaseName(requestedDatabase);

                EmbeddedPostgres.Builder builder = EmbeddedPostgres.builder();
                if (requestedPort > 0) {
                    builder.setPort(requestedPort);
                }

                embeddedPostgres = builder.start();
                actualPort = embeddedPostgres.getPort();
                databaseName = requestedDatabase;

                ensureDatabaseExists(requestedDatabase);
                registerShutdownHook();

                System.out.println("[embedded-postgres] started on port " + actualPort + ", database=" + requestedDatabase);
            } catch (Exception ex) {
                throw new IllegalStateException("Unable to start embedded PostgreSQL", ex);
            }
        }
    }

    private void ensureDatabaseExists(String requestedDatabase) throws Exception {
        DataSource postgresDatabase = embeddedPostgres.getPostgresDatabase();
        try (Connection connection = postgresDatabase.getConnection();
             PreparedStatement check = connection.prepareStatement("select 1 from pg_database where datname = ?")) {
            check.setString(1, requestedDatabase);
            try (ResultSet resultSet = check.executeQuery()) {
                if (resultSet.next()) {
                    return;
                }
            }
        }

        try (Connection connection = postgresDatabase.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("create database \"" + requestedDatabase + "\"");
        }
    }

    private void registerDatasourceProperties(ConfigurableEnvironment environment) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.datasource.url", "jdbc:postgresql://localhost:" + actualPort + "/" + databaseName);
        properties.put("spring.datasource.username", "postgres");
        properties.put("spring.datasource.password", "postgres");
        properties.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
        properties.put("spring.flyway.enabled", "true");
        properties.put("spring.flyway.locations", "classpath:db/migration");
        properties.put("spring.flyway.schemas", "fairvalue");
        properties.put("spring.flyway.default-schema", "fairvalue");
        properties.put("spring.flyway.create-schemas", "true");
        properties.put("spring.datasource.hikari.maximum-pool-size", "4");
        properties.put("spring.datasource.hikari.minimum-idle", "1");

        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
    }

    private void validateDatabaseName(String requestedDatabase) {
        if (!requestedDatabase.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("Embedded PostgreSQL database name must match [A-Za-z0-9_]+");
        }
    }

    private void registerShutdownHook() {
        if (SHUTDOWN_HOOK_REGISTERED.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                EmbeddedPostgres running = embeddedPostgres;
                if (running != null) {
                    try {
                        running.close();
                    } catch (Exception ignored) {
                        // Ignore shutdown failures in local embedded mode.
                    }
                }
            }, "embedded-postgres-shutdown"));
        }
    }
}
