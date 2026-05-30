package com.example.springinfinispan.web;

import com.example.springinfinispan.service.CacheService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/cache")
public class CacheController {

    private final CacheService cacheService;

    public CacheController(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    /** Add or update a cache entry */
    @PutMapping("/{key}")
    public ResponseEntity<Map<String, Object>> put(
            @PathVariable String key,
            @RequestBody String value) {
        cacheService.put(key, value);
        return ResponseEntity.ok(Map.of(
            "status", "stored",
            "key", key,
            "value", value
        ));
    }

    /** Retrieve a cache entry by key */
    @GetMapping("/{key}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String key) {
        String value = cacheService.get(key);
        if (value == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("status", "not_found", "key", key));
        }
        return ResponseEntity.ok(Map.of(
            "status", "found",
            "key", key,
            "value", value
        ));
    }

    /** Remove a cache entry by key */
    @DeleteMapping("/{key}")
    public ResponseEntity<Map<String, Object>> remove(@PathVariable String key) {
        boolean removed = cacheService.remove(key);
        if (!removed) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("status", "not_found", "key", key));
        }
        return ResponseEntity.ok(Map.of("status", "removed", "key", key));
    }

    /** List all cache entries */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAll() {
        Map<String, String> entries = cacheService.getAll();
        return ResponseEntity.ok(Map.of(
            "size", cacheService.size(),
            "entries", entries
        ));
    }

    /** Clear all entries */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> clear() {
        cacheService.clear();
        return ResponseEntity.ok(Map.of("status", "cleared"));
    }
}
