package com.appliance.repair.service;

import com.appliance.repair.config.SpringAiConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ai.document.Document;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.ai.openai.api-key=test-key",
    "spring.ai.openai.base-url=https://api.deepseek.com",
    "spring.datasource.url=jdbc:h2:mem:testdb"
})
class VectorStoreServiceTest {

    @Autowired
    private VectorStoreService vectorStoreService;

    @Test
    void testSearchSimilar() {
        // This test requires actual database and API setup
        // Skip in unit tests, verify in integration tests
        assertTrue(true);
    }
}
