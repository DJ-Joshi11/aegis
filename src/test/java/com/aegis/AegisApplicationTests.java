package com.aegis;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test: the app context loads. Each package's own tests
 * (AnomalyDetectionServiceTest, PayoutEngineServiceTest, etc.) go in
 * their matching src/test/java/com/aegis/<package>/ folder.
 */
@SpringBootTest
class AegisApplicationTests {

    @Test
    void contextLoads() {
    }
}
