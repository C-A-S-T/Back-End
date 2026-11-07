package com.example.cast.persistence;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;
/** 기존 V1 데이터와 Flyway 체크섬을 유지한 상태에서 신규 버전 적용을 검증한다. */
class MigrationUpgradeTest {
    @Test void upgradesExistingV1WithoutInventingGoldValues() throws Exception {
        CastPersistenceTestSafety.requireExplicitApproval();
        try (var postgres = new PostgreSQLContainer("postgres:17")) {
            postgres.start();
            var baseline = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .target("1").load();
            baseline.migrate();
            try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 var statement = connection.createStatement()) {
                statement.executeUpdate("insert into document(doc_title) values ('legacy')");
                statement.executeUpdate("""
                    insert into evaluation_case(question, expected_claims, relevant_chunk_ids)
                    values ('legacy question', '["first", "second"]'::jsonb, '[10,20]'::jsonb)
                    """);
            }
            var latest = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).load();
            latest.migrate();
            latest.validate();
            try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 var statement = connection.createStatement()) {
                try (var result = statement.executeQuery("select content_hash from document where doc_title='legacy'")) {
                    assertTrue(result.next()); assertNull(result.getString(1));
                }
                try (var result = statement.executeQuery("""
                    select expected_claims->0->>'content', expected_claims->1->>'content',
                           expected_claims->0->>'type', relevant_chunk_ids->>0
                    from evaluation_case
                    """)) {
                    assertTrue(result.next()); assertEquals("first", result.getString(1));
                    assertEquals("second", result.getString(2)); assertNull(result.getString(3));
                    assertEquals("10", result.getString(4));
                }
            }
        }
    }
}
