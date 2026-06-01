package com.example.springinfinispan.config;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.EmbeddedCacheManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import javax.naming.InitialContext;
import javax.naming.NamingException;

@Configuration
public class InfinispanConfig {

    @Value("${infinispan.cache.name:custom-cache}")
    private String cacheName;

    @Value("${infinispan.container.name:custom-app-cache}")
    private String containerName;

    // EAP 7.4 only supports start=LAZY for Infinispan caches. With LAZY start, the cache MSC
    // service hasn't started at lookup time, so its configuration is not yet registered in the
    // EmbeddedCacheManager's ConfigurationManager — getCache(name) throws ISPN000436.
    // Fix: define the configuration programmatically if not already registered, then getCache().
    // The manager's JGroups transport (from the subsystem) ensures distribution across nodes.
    @Bean
    @Lazy
    @SuppressWarnings("unchecked")
    public Cache<String, String> customCache() throws NamingException {
        String jndiName = "java:jboss/infinispan/container/" + containerName;
        EmbeddedCacheManager manager = (EmbeddedCacheManager) new InitialContext().lookup(jndiName);
        if (manager.getCacheConfiguration(cacheName) == null) {
            manager.defineConfiguration(cacheName, new ConfigurationBuilder()
                    .clustering().cacheMode(CacheMode.DIST_SYNC)
                    .hash().numOwners(2)
                    .statistics().enabled(true)
                    .build());
        }
        return manager.getCache(cacheName);
    }
}
