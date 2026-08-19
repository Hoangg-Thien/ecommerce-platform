package com.ecommerce.ratelimit;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;

/**
 * Registry that creates and caches rate-limit {@link Bucket} instances per key.
 *
 * <p>Design decisions:
 * <ul>
 *   <li>Uses Caffeine (already a project dependency) as the backing cache — no new deps needed.</li>
 *   <li>maximumSize(10_000): caps memory usage. If an attacker floods with unique IPs, Caffeine
 *       evicts the least-recently-used entries automatically (LRU policy).</li>
 *   <li>expireAfterAccess(2 minutes): a bucket with no traffic for 2 min is evicted and its
 *       token count resets. This prevents stale state accumulation.</li>
 *   <li>Bucket4j buckets are thread-safe by design — no external synchronization needed.</li>
 *   <li>{@link Cache#get(Object, java.util.function.Function)} is atomic in Caffeine — the
 *       function is called at most once per key even under concurrent load.</li>
 * </ul>
 *
 * <p>Interview: Token Bucket algorithm — the bucket holds up to {@code capacity} tokens.
 * Each request consumes 1 token. Tokens refill at a fixed rate (refillTokens per refillDuration).
 * When the bucket is empty, the next consume call returns false → 429.
 */

@Slf4j
@Component
public class RateLimitBucketRegistry {
    
    /**
     * Caffeine cache: key → Bucket.
     * Separate from the application CacheManager to avoid TTL conflicts with product/category caches.
    */

    private final Cache<String, Bucket> cache;

    public RateLimitBucketRegistry(){
        this.cache = Caffeine.newBuilder()
        .maximumSize(10_000) // cap at 10k entries to prevent OOM
        .expireAfterAccess(2, TimeUnit.MINUTES) // evict idle keys after 2 min
        .build();
    }

       /**
     * Returns the existing bucket for the given key, or creates a new one from the provided config.
     * This method is thread-safe.
     *
     * @param key    the rate-limit key (IP address or user email)
     * @param config the limit configuration to use when creating a new bucket
     * @return the bucket for this key
     */

       public Bucket resolveBucket(String key, RateLimitProperties.GroupLimit config){
        return cache.get(key, k -> createBucket(config));
       }

    /**
     * Creates a new Bucket with a greedy refill bandwidth.
     *
     * <p>Greedy refill: tokens are added continuously at a constant rate throughout the period,
     * rather than all at once at the end. This gives smoother rate limiting behaviour.
     */

    private Bucket createBucket(RateLimitProperties.GroupLimit config){
        Bandwidth limit = Bandwidth.builder()
        .capacity(config.getCapacity())
        .refillGreedy(config.getRefillTokens(), Duration.ofSeconds(config.getRefillDurationSeconds()))
        .build();

        return Bucket.builder()
        .addLimit(limit)
        .build();
    }
}
