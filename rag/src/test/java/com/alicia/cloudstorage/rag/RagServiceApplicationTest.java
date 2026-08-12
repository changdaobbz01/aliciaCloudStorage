package com.alicia.cloudstorage.rag;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "alicia.rag.deepseek.api-key=",
                "alicia.rag.storage-api.base-url="
        }
)
class RagServiceApplicationTest {

    @Test
    void applicationContextLoads() {
    }
}
