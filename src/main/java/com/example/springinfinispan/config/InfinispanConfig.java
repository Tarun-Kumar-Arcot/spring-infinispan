package com.example.springinfinispan.config;

import org.infinispan.Cache;
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

    // @Lazy defers the JNDI lookup until the first real request, which is after JBoss EAP's
    // Infinispan subsystem has finished initialising — avoids the lazy-start timing problem.
    // The cache lifecycle is managed by EAP; the app must not stop it.
    @Bean
    @Lazy
    @SuppressWarnings("unchecked")
    public Cache<String, String> customCache() throws NamingException {
        String jndiName = "java:jboss/infinispan/cache/" + containerName + "/" + cacheName;
        return (Cache<String, String>) new InitialContext().lookup(jndiName);
    }
}
