package com.example.cast.persistence;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.postgresql.PostgreSQLContainer;
/** 컨테이너 생명주기를 Spring 컨텍스트에 묶어 캐시된 DataSource와 함께 유지한다. */
@Import(TestDatabaseSupport.DatabaseConfig.class)
public abstract class TestDatabaseSupport {
    @Autowired protected PostgreSQLContainer postgres;
    @TestConfiguration(proxyBeanMethods = false)
    public static class DatabaseConfig {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            CastPersistenceTestSafety.requireExplicitApproval();
            return new PostgreSQLContainer("postgres:17")
                    .withDatabaseName("cast_persistence_test")
                    .withUsername("cast_persistence")
                    .withPassword("test-only-password");
        }
    }
}
