package com.example.springinfinispan.config;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InfinispanConfig {

    @Value("${infinispan.cache.name:custom-cache}")
    private String cacheName;

    @Value("${infinispan.store.location:/tmp/infinispan-store}")
    private String storeLocation;

    // Creates an embedded cache manager using JBoss EAP's Infinispan JARs (provided scope).
    // Avoids JNDI entirely — JBoss EAP's lazy-start container never satisfies a direct
    // InitialContext.lookup() during Spring context initialisation.
    @Bean(destroyMethod = "stop")
    public EmbeddedCacheManager cacheManager() {
        GlobalConfigurationBuilder globalBuilder = new GlobalConfigurationBuilder();
        globalBuilder.nonClusteredDefault();

        ConfigurationBuilder cacheBuilder = new ConfigurationBuilder();
        cacheBuilder
            .persistence()
                .addSingleFileStore()
                    .location(storeLocation)
                    .segmented(false)
                    .shared(false)
                    .preload(true)
                    .fetchPersistentState(true)
                    .purgeOnStartup(false);

        DefaultCacheManager manager = new DefaultCacheManager(globalBuilder.build());
        manager.defineConfiguration(cacheName, cacheBuilder.build());
        return manager;
    }

    @Bean
    public Cache<String, String> customCache(EmbeddedCacheManager cacheManager) {
        return cacheManager.getCache(cacheName);
    }
}
