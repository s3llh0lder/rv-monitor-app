package com.example.rvmonitor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RvMonitorApplicationTest {

    @Test
    void contextLoads() {
        // Spring context should bootstrap with the test profile (scheduling off).
    }
}
