package com.throttle;

/**
 * A rate limiting strategy. Implementations decide whether a given client
 * may proceed, backed by shared state in Redis so the decision is consistent
 * across horizontally scaled instances.
 */
public interface RateLimiter {

    /**
     * Attempt to consume one unit of quota for the given client.
     *
     * @param clientId stable identity of the caller (e.g. resolved IP)
     * @return the outcome, including remaining quota and retry hint
     */
    RateLimitResult tryAcquire(String clientId);

    /** The configured limit (for X-RateLimit-Limit header). */
    int capacity();
}