package com.example.springinfinispan;

import com.example.springinfinispan.service.CacheService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SpringInfinispanApplicationTests {

    @Autowired
    private CacheService cacheService;

    @AfterEach
    void cleanup() {
        cacheService.clear();
    }

    @Test
    void putAndGetEntry() {
        cacheService.put("greet", "hello");
        assertThat(cacheService.get("greet")).isEqualTo("hello");
    }

    @Test
    void getMissingKeyReturnsNull() {
        assertThat(cacheService.get("missing")).isNull();
    }

    @Test
    void removeEntry() {
        cacheService.put("temp", "value");
        assertThat(cacheService.remove("temp")).isTrue();
        assertThat(cacheService.get("temp")).isNull();
    }

    @Test
    void getAllEntries() {
        cacheService.put("k1", "v1");
        cacheService.put("k2", "v2");
        Map<String, String> all = cacheService.getAll();
        assertThat(all).containsEntry("k1", "v1").containsEntry("k2", "v2");
    }

    @Test
    void updateExistingKey() {
        cacheService.put("key", "original");
        cacheService.put("key", "updated");
        assertThat(cacheService.get("key")).isEqualTo("updated");
    }
}
