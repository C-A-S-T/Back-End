package com.example.cast.persistence;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;
/** V1 원본을 바꾸지 않고 데이터/인용/저장 출처를 V7까지 보존하는지 검증한다. */
class MigrationUpgradeTest {
    @Test void upgradesExistingDataAndDetachesAiReferences() throws Exception {
        CastPersistenceTestSafety.requireExplicitApproval();
        try(var postgres=new PostgreSQLContainer("postgres:17").withDatabaseName("cast_persistence_test").withInitScript("db/roles.sql")) {
            postgres.start();
            String url=postgres.getJdbcUrl();
            Flyway.configure().dataSource(url,"cast_migrator","migration-test-password").target("1").load().migrate();
            try(var c=DriverManager.getConnection(url,"cast_migrator","migration-test-password");var s=c.createStatement()) {
                s.executeUpdate("insert into app_user(email,password_hash) values ('legacy@example.invalid','hash')");
                s.executeUpdate("insert into research_query(user_id,question) values (1,'question')");
                s.executeUpdate("insert into research_task(query_id) values (1)");
                s.executeUpdate("insert into answer(task_id) values (1)");
                s.executeUpdate("insert into claim(answer_id,claim_index,claim_content) values (1,0,'claim')");
                s.executeUpdate("insert into verification(claim_id,ver_attempt_number) values (1,1)");
                s.executeUpdate("insert into document(doc_title) values ('legacy document')");
                s.executeUpdate("insert into document_chunk(document_id,chunk_index,chunk_content,page_start,page_end,section_title) values (1,0,'chunk',2,3,'section')");
                s.executeUpdate("insert into evidence(verification_id,chunk_id,evidence_text,evidence_relation) values (1,1,'original quote','SUPPORTS')");
                s.executeUpdate("insert into saved_source(user_id,document_id,save_method) values (1,1,'MANUAL')");
                s.executeUpdate("insert into retrieval_run(task_id,claim_id,run_type,search_query) values (1,1,'CLAIM_SEARCH','search')");
                s.executeUpdate("insert into evaluation_case(question,expected_claims,relevant_chunk_ids) values ('legacy','[\"first\",\"second\"]'::jsonb,'[1]'::jsonb)");
            }
            Flyway.configure().dataSource(url,"cast_migrator","migration-test-password").target("5").load().migrate();
            try(var c=DriverManager.getConnection(url,"cast_migrator","migration-test-password");var s=c.createStatement()) {
                s.executeUpdate("update verification set run_id=1");
            }
            var latest=Flyway.configure().dataSource(url,"cast_migrator","migration-test-password").load();
            latest.migrate();latest.validate();
            try(var c=DriverManager.getConnection(url,"cast_migrator","migration-test-password");var s=c.createStatement()) {
                try(var rs=s.executeQuery("select doc_title,ai_document_id from backend.document where document_id=1")) {
                    assertTrue(rs.next());assertEquals("legacy document",rs.getString(1));assertEquals(1,rs.getLong(2));
                }
                try(var rs=s.executeQuery("select document_id,ai_chunk_id,evidence_text,page_start,page_end from backend.evidence")) {
                    assertTrue(rs.next());assertEquals(1,rs.getLong(1));assertEquals(1,rs.getLong(2));assertEquals("original quote",rs.getString(3));
                    assertEquals(2,rs.getInt(4));assertEquals(3,rs.getInt(5));
                }
                try(var rs=s.executeQuery("select ai_run_id from backend.verification")){assertTrue(rs.next());assertEquals(1,rs.getLong(1));}
                try(var rs=s.executeQuery("select count(*) from backend.saved_source s join backend.document d on s.document_id=d.document_id")) {assertTrue(rs.next());assertEquals(1,rs.getInt(1));}
                try(var rs=s.executeQuery("select expected_claims->0->>'content',expected_claims->1->>'content',expected_claims->0->>'type' from ai.evaluation_case")) {
                    assertTrue(rs.next());assertEquals("first",rs.getString(1));assertEquals("second",rs.getString(2));assertNull(rs.getString(3));
                }
                try(var rs=s.executeQuery("insert into backend.document(ai_document_id,doc_title) values (100,'new') returning document_id")) {assertTrue(rs.next());assertTrue(rs.getLong(1)>1);}
                // AI 데이터를 정리해도 백엔드 인용 스냅샷은 남는다.
                s.executeUpdate("delete from ai.retrieval_run");s.executeUpdate("delete from ai.document_chunk");s.executeUpdate("delete from ai.document");
                try(var rs=s.executeQuery("select evidence_text from backend.evidence")){assertTrue(rs.next());assertEquals("original quote",rs.getString(1));}
            }
        }
    }
}
