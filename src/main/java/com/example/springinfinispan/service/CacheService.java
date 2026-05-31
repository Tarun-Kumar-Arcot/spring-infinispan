package com.example.springinfinispan.service;

import org.infinispan.Cache;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
public class CacheService {

    private final Cache<String, String> cache;

    public CacheService(@Lazy Cache<String, String> cache) {
        this.cache = cache;
    }

    public void put(String key, String value) {
        cache.put(key, value);
    }

    public String get(String key) {
        return cache.get(key);
    }

    public boolean remove(String key) {
        return cache.remove(key) != null;
    }

    public boolean containsKey(String key) {
        return cache.containsKey(key);
    }

    public Map<String, String> getAll() {
        Map<String, String> result = new HashMap<>();
        Set<String> keys = cache.keySet();
        for (String key : keys) {
            result.put(key, cache.get(key));
        }
        return result;
    }

    public int size() {
        return cache.size();
    }

    public void clear() {
        cache.clear();
    }
}
