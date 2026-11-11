package com.example.cast;
import com.example.cast.persistence.TestDatabaseSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
/** Flyway와 ddl-auto=validate를 함께 실행하는 컨텍스트 테스트. */
@SpringBootTest
@ActiveProfiles("test")
class CastApplicationTests extends TestDatabaseSupport {
    @org.springframework.beans.factory.annotation.Autowired
    com.example.cast.integration.ai.config.AiProperties ai;
    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.context.ApplicationContext context;
    @Test void contextLoads() {
        org.junit.jupiter.api.Assertions.assertFalse(ai.enabled());
        org.junit.jupiter.api.Assertions.assertTrue(ai.apiToken().isEmpty());
        org.junit.jupiter.api.Assertions.assertEquals(TEST_CALLBACK_TOKEN, ai.callbackToken());
        org.junit.jupiter.api.Assertions.assertTrue(context.getBeansOfType(
                com.example.cast.integration.ai.service.AiJobDispatcher.class).isEmpty());
    }
}
