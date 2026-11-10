package com.example.cast.persistence;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.flyway.autoconfigure.FlywayConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.postgresql.PostgreSQLContainer;
/** 실제 런타임 제한 계정과 Flyway 소유자 계정을 분리해 검증한다. */
@Import(TestDatabaseSupport.DatabaseConfig.class)
public abstract class TestDatabaseSupport {
    @Autowired protected PostgreSQLContainer postgres;
    @TestConfiguration(proxyBeanMethods = false)
    public static class DatabaseConfig {
        @Bean(initMethod = "start", destroyMethod = "stop")
        PostgreSQLContainer postgres() {
            CastPersistenceTestSafety.requireExplicitApproval();
            return new PostgreSQLContainer("postgres:17").withDatabaseName("cast_persistence_test")
                    .withUsername("cast_persistence").withPassword("test-only-password")
                    .withInitScript("db/roles.sql");
        }
        @Bean JdbcConnectionDetails jdbcConnectionDetails(PostgreSQLContainer postgres) {
            return new JdbcConnectionDetails() {
                public String getJdbcUrl() { return postgres.getJdbcUrl(); }
                public String getUsername() { return "cast_app"; }
                public String getPassword() { return "backend-test-password"; }
                public String getDriverClassName() { return "org.postgresql.Driver"; }
            };
        }
        @Bean FlywayConnectionDetails flywayConnectionDetails(PostgreSQLContainer postgres) {
            return new FlywayConnectionDetails() {
                public String getJdbcUrl() { return postgres.getJdbcUrl(); }
                public String getUsername() { return "cast_migrator"; }
                public String getPassword() { return "migration-test-password"; }
                public String getDriverClassName() { return "org.postgresql.Driver"; }
            };
        }
    }
}
