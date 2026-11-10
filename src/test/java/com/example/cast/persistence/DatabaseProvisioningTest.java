package com.example.cast.persistence;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;
import java.nio.file.Path;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;
/** 실제 배포 스크립트의 기존 볼륨 전환과 재실행을 임시 컨테이너에서 검증한다. */
class DatabaseProvisioningTest {
    @Test void provisionsLegacyDatabaseWithoutChangingOtherDatabases() throws Exception {
        CastPersistenceTestSafety.requireExplicitApproval();
        try(var postgres=new PostgreSQLContainer("postgres:17").withDatabaseName("cast_dev")
                .withUsername("postgres").withPassword("admin-test")
                .withEnv("CAST_DB_PASSWORD","backend-test-password")
                .withEnv("CAST_AI_DB_PASSWORD","ai-test-password")
                .withEnv("CAST_MIGRATION_DB_PASSWORD","migration-test-password")) {
            postgres.start();
            try(var c=DriverManager.getConnection(postgres.getJdbcUrl(),"postgres","admin-test");var s=c.createStatement()) {
                s.execute("create role cast_app login password 'old-password'");
                s.execute("alter database cast_dev owner to cast_app");
                s.execute("grant usage,create on schema public to cast_app");
                s.execute("create database unrelated_db owner cast_app");
            }
            Flyway.configure().dataSource(postgres.getJdbcUrl(),"cast_app","old-password").target("5").load().migrate();
            postgres.copyFileToContainer(MountableFile.forHostPath(Path.of("docker/postgres/init/01-create-app-user.sh").toAbsolutePath()),"/tmp/provision.sh");
            var provision=postgres.execInContainer("sh","/tmp/provision.sh");
            assertEquals(0,provision.getExitCode(),provision.getStderr());
            var latest=Flyway.configure().dataSource(postgres.getJdbcUrl(),"cast_migrator","migration-test-password").load();
            latest.migrate();latest.validate();
            var again=postgres.execInContainer("sh","/tmp/provision.sh");
            assertEquals(0,again.getExitCode(),again.getStderr());
            try(var c=DriverManager.getConnection(postgres.getJdbcUrl(),"postgres","admin-test");var s=c.createStatement()) {
                try(var rs=s.executeQuery("select pg_get_userbyid(datdba) from pg_database where datname='unrelated_db'")) {assertTrue(rs.next());assertEquals("cast_app",rs.getString(1));}
                try(var rs=s.executeQuery("select count(*) from pg_tables where schemaname in ('backend','ai') and tableowner <> 'cast_migrator'")) {assertTrue(rs.next());assertEquals(0,rs.getInt(1));}
            }
            try(var c=DriverManager.getConnection(postgres.getJdbcUrl(),"cast_app","backend-test-password");var s=c.createStatement()) {
                s.executeQuery("select * from backend.ai_job").close();
                assertEquals("42501",assertThrows(SQLException.class,()->s.executeQuery("select * from ai.document")).getSQLState());
                assertEquals("42501",assertThrows(SQLException.class,()->s.execute("create table backend.forbidden(id bigint)")).getSQLState());
            }
        }
    }
}
