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
    protected static final String TEST_CALLBACK_TOKEN = "callback-test-token-at-least-32-characters";

    /** 환경변수보다 우선하는 테스트 설정으로 실제 AI 서버 연결을 차단한다. */
    @org.springframework.test.context.DynamicPropertySource
    static void isolateAiConnection(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("cast.ai.enabled", () -> false);
        registry.add("cast.ai.base-url", () -> "");
        registry.add("cast.ai.api-token", () -> "");
        registry.add("cast.ai.callback-token", () -> TEST_CALLBACK_TOKEN);
    }

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
