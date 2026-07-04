package com.throttle;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * A purely in-memory token bucket used as a fallback when Redis is
 * unreachable. State is per-instance (not shared), so limiting degrades
 * from global to local — but the API keeps protecting itself rather than
 * failing open or returning errors.
 */
@Component
public class InMemoryFallbackLimiter {

    private final RateLimitProperties props;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public InMemoryFallbackLimiter(RateLimitProperties props) {
        this.props = props;
    }

    public RateLimitResult tryAcquire(String clientId) {
        Bucket bucket = buckets.computeIfAbsent(clientId,
                k -> new Bucket(props.getCapacity(), System.currentTimeMillis()));

        synchronized (bucket) {
            long now = System.currentTimeMillis();
            double refillRate = props.getRefillRate();

            // Lazy refill, same model as the Redis token bucket.
            double elapsedSeconds = (now - bucket.lastRefill) / 1000.0;
            double refilled = elapsedSeconds * refillRate;
            bucket.tokens = Math.min(props.getCapacity(), bucket.tokens + refilled);
            bucket.lastRefill = now;

            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0;
                return new RateLimitResult(true, (long) Math.floor(bucket.tokens), 0);
            } else {
                double deficit = 1.0 - bucket.tokens;
                long retryAfterMs = (long) Math.ceil((deficit / refillRate) * 1000);
                return new RateLimitResult(false, 0, retryAfterMs);
            }
        }
    }

    private static final class Bucket {
        double tokens;
        long lastRefill;

        Bucket(double tokens, long lastRefill) {
            this.tokens = tokens;
            this.lastRefill = lastRefill;
        }
    }
}