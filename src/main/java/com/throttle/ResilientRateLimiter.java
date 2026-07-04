package com.throttle;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Wraps the Redis-backed limiter with a circuit breaker. When Redis calls
 * fail repeatedly the breaker opens and requests are served by the local
 * in-memory fallback, so the API degrades gracefully instead of erroring.
 */
@Component
public class ResilientRateLimiter implements RateLimiter {

    private final RateLimiter delegate;              // the Redis-backed limiter
    private final InMemoryFallbackLimiter fallback;  // local in-memory limiter
    private final CircuitBreaker circuitBreaker;

    public ResilientRateLimiter(RateLimiterProvider provider,
                                InMemoryFallbackLimiter fallback) {
        this.delegate = provider.get();
        this.fallback = fallback;

        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)                        // open if >=50% of calls fail
                .slidingWindowSize(10)                           // over the last 10 calls
                .waitDurationInOpenState(Duration.ofSeconds(5))  // stay open 5s, then probe
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();

        this.circuitBreaker = CircuitBreaker.of("redisRateLimiter", config);
    }

    @Override
    public RateLimitResult tryAcquire(String clientId) {
        Supplier<RateLimitResult> redisCall =
                CircuitBreaker.decorateSupplier(circuitBreaker,
                        () -> delegate.tryAcquire(clientId));
        try {
            return redisCall.get();
        } catch (Exception e) {
            // Breaker open, or Redis call failed: serve from local fallback.
            return fallback.tryAcquire(clientId);
        }
    }

    @Override
    public int capacity() {
        return delegate.capacity();
    }
}