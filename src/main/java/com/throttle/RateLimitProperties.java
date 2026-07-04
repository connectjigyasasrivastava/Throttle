package com.throttle;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ratelimit")
public class RateLimitProperties {

    /** Max tokens a bucket can hold (burst size). */
    private int capacity = 10;

    /** Tokens refilled per second (sustained rate). */
    private double refillRate = 5.0;

    /** Only requests whose path starts with this prefix are rate limited. */
    private String pathPrefix = "/api";

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public double getRefillRate() {
        return refillRate;
    }

    public void setRefillRate(double refillRate) {
        this.refillRate = refillRate;
    }

    public String getPathPrefix() {
        return pathPrefix;
    }

    public void setPathPrefix(String pathPrefix) {
        this.pathPrefix = pathPrefix;
    }

    /** Max requests per window (used by sliding window algorithms). */
    private int limit = 10;

    /** Window size in milliseconds (used by sliding window algorithms). */
    private long windowMs = 2000;

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public long getWindowMs() {
        return windowMs;
    }

    public void setWindowMs(long windowMs) {
        this.windowMs = windowMs;
    }
}