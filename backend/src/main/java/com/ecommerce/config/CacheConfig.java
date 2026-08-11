package com.ecommerce.config;

import java.util.concurrent.TimeUnit;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public CacheManager cacheManager(){
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
            "categories", // ten cache vung cho category
            "product" // ten cache vung cho product
        );

        cacheManager.setCaffeine(Caffeine.newBuilder()
        .initialCapacity(100) // khoi tao 100 cho trong
        .maximumSize(500) // toi da 500 records cho moi vung
        .expireAfterWrite(30, TimeUnit.MINUTES)  // tu dong xoa data sau 30p ke tu khi luu
        .recordStats());

        return cacheManager;
    }
}
