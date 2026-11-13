package com.example.cast.persistence;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;
/** ORM 테스트와 별도로 실제 PostgreSQL 계정 권한 및 AI 데이터 제약을 검증한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class SchemaOwnershipIntegrationTest extends TestDatabaseSupport {
    Connection connect(String role,String password)throws SQLException{return DriverManager.getConnection(postgres.getJdbcUrl(),role,password);}
    void denied(String role,String password,String sql)throws Exception {
        try(var c=connect(role,password);var s=c.createStatement()) {
            var e=assertThrows(SQLException.class,()->s.execute(sql));assertEquals("42501",e.getSQLState());
        }
    }
    @Test void backendCannotReadAi(){assertDoesNotThrow(()->denied("cast_app","backend-test-password","select * from ai.document"));}
    @Test void backendCannotWriteAi(){assertDoesNotThrow(()->denied("cast_app","backend-test-password","insert into ai.document(doc_title) values ('forbidden')"));}
    @Test void aiCannotReadUsers(){assertDoesNotThrow(()->denied("cast_ai","ai-test-password","select * from backend.app_user"));}
    @Test void aiCannotChangeTasks(){assertDoesNotThrow(()->denied("cast_ai","ai-test-password","update backend.research_task set status='COMPLETED'"));}
    @Test void runtimeCannotCreateTables()throws Exception {
        denied("cast_app","backend-test-password","create table backend.forbidden(id bigint)");
        denied("cast_ai","ai-test-password","create table ai.forbidden(id bigint)");
    }
    @Test void noCrossSchemaForeignKeys()throws Exception {
        try(var c=connect("cast_migrator","migration-test-password");var s=c.createStatement();var rs=s.executeQuery("""
            select count(*) from pg_constraint fk join pg_class src on src.oid=fk.conrelid
            join pg_class dst on dst.oid=fk.confrelid
            where fk.contype='f' and src.relnamespace <> dst.relnamespace
            """)){assertTrue(rs.next());assertEquals(0,rs.getInt(1));}
    }
    @Test void aiOwnsIngestionAndEvaluationConstraints()throws Exception {
        try(var c=connect("cast_ai","ai-test-password");var s=c.createStatement()) {
            c.setAutoCommit(false);
            s.executeUpdate("insert into ai.document(doc_title,content_hash) values ('original',repeat('a',64))");
            var e=assertThrows(SQLException.class,()->s.executeUpdate("insert into ai.document(doc_title,content_hash) values ('duplicate',repeat('a',64))"));
            assertEquals("23505",e.getSQLState());c.rollback();
            e=assertThrows(SQLException.class,()->s.executeUpdate("insert into ai.evaluation_case(question,expected_claims) values ('q','{}'::jsonb)"));
            assertEquals("23514",e.getSQLState());c.rollback();
            e=assertThrows(SQLException.class,()->s.executeUpdate("insert into ai.retrieval_run(task_id,claim_id,run_type,re_attempt_number,search_query) values (1,1,'RETRY',4,'q')"));
            assertEquals("23514",e.getSQLState());c.rollback();
        }
    }
    @Test void migrationAccountOwnsTablesAndNewBackendTableIsGranted()throws Exception {
        try(var c=connect("cast_migrator","migration-test-password");var s=c.createStatement();var rs=s.executeQuery("""
            select count(*) from pg_tables where schemaname in ('backend','ai') and tableowner <> 'cast_migrator'
            """)){assertTrue(rs.next());assertEquals(0,rs.getInt(1));}
        try(var c=connect("cast_app","backend-test-password");var s=c.createStatement()){s.executeQuery("select * from backend.ai_job").close();}
    }
}
