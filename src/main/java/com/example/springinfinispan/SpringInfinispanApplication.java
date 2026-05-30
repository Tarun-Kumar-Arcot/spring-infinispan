package com.example.springinfinispan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

@SpringBootApplication
public class SpringInfinispanApplication extends SpringBootServletInitializer {

    // Extends SpringBootServletInitializer so JBoss EAP discovers and bootstraps the app as a WAR.
    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(SpringInfinispanApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(SpringInfinispanApplication.class, args);
    }
}
