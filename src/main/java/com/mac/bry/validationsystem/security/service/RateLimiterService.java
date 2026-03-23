package com.mac.bry.validationsystem.security.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Map;

/**
 * GMP COMPLIANCE: Simplified rate limiting service with local cache and cleanup
 *
 * This is a temporary implementation for audit readiness.
 * Production should use Redis-backed distributed rate limiting.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimiterService {

    // Local cache with automatic cleanup
    private final ConcurrentHashMap<String, BucketEntry> buckets = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newScheduledThreadPool(1);

    @Value("${app.rate-limit.login.capacity:5}")
    private int loginCapacity;

    @Value("${app.rate-limit.login.refill-tokens:5}")
    private int loginRefillTokens;

    @Value("${app.rate-limit.login.refill-duration:300}")
    private int loginRefillDurationSeconds;

    @Value("${app.rate-limit.audit.capacity:50}")
    private int auditCapacity;

    @Value("${app.rate-limit.audit.refill-tokens:50}")
    private int auditRefillTokens;

    @Value("${app.rate-limit.audit.refill-duration:60}")
    private int auditRefillDurationSeconds;

    public boolean allowLoginAttempt(String ipAddress) {
        String key = "login:" + ipAddress;
        return tryConsume(key, loginCapacity, loginRefillTokens, Duration.ofSeconds(loginRefillDurationSeconds));
    }

    public boolean allowAuditLog(String ipAddress) {
        String key = "audit:" + ipAddress;
        return tryConsume(key, auditCapacity, auditRefillTokens, Duration.ofSeconds(auditRefillDurationSeconds));
    }

    public void resetLoginAttempts(String ipAddress) {
        String key = "login:" + ipAddress;
        buckets.remove(key);
        log.info("🔄 Reset login rate limit for IP: {} (Local cache)", ipAddress);
    }

    private boolean tryConsume(String key, int capacity, int refillTokens, Duration refillPeriod) {
        try {
            BucketEntry entry = buckets.computeIfAbsent(key, k -> new BucketEntry(
                createBucket(capacity, refillTokens, refillPeriod),
                System.currentTimeMillis()
            ));

            // Update last access time
            entry.lastAccess = System.currentTimeMillis();

            boolean allowed = entry.bucket.tryConsume(1);
            if (!allowed) {
                log.debug("🚫 Rate limit exceeded for key: {} (Local cache)", key);
            }
            return allowed;

        } catch (Exception e) {
            log.error("❌ Rate limiting error for key: {}, allowing request: {}", key, e.getMessage());
            return true; // Fail-open for availability
        }
    }

    private Bucket createBucket(int capacity, int refillTokens, Duration refillPeriod) {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(capacity, Refill.intervally(refillTokens, refillPeriod)))
                .build();
    }

    // Periodic cleanup of old entries
    @jakarta.annotation.PostConstruct
    public void startCleanup() {
        cleanupExecutor.scheduleAtFixedRate(this::cleanupOldEntries, 5, 5, TimeUnit.MINUTES);
        log.info("✅ Rate limiting service initialized with local cache cleanup");
    }

    @jakarta.annotation.PreDestroy
    public void shutdown() {
        cleanupExecutor.shutdown();
        log.info("📝 Rate limiting service shutdown completed");
    }

    private void cleanupOldEntries() {
        long cutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1); // 1 hour old
        int removed = 0;

        for (Map.Entry<String, BucketEntry> entry : buckets.entrySet()) {
            if (entry.getValue().lastAccess < cutoff) {
                buckets.remove(entry.getKey());
                removed++;
            }
        }

        if (removed > 0) {
            log.debug("🧹 Cleaned up {} old rate limit entries", removed);
        }
    }

    private static class BucketEntry {
        final Bucket bucket;
        volatile long lastAccess;

        BucketEntry(Bucket bucket, long lastAccess) {
            this.bucket = bucket;
            this.lastAccess = lastAccess;
        }
    }
}