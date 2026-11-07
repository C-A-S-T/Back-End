package com.example.cast;
import com.example.cast.persistence.TestDatabaseSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
/** Flyway와 ddl-auto=validate를 함께 실행하는 컨텍스트 테스트. */
@SpringBootTest
@ActiveProfiles("test")
class CastApplicationTests extends TestDatabaseSupport {
    @Test void contextLoads() {}
}
