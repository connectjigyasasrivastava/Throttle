package com.throttle;

import org.springframework.stereotype.Component;

@Component
public class RateLimiterProvider {

    private final RateLimiter active;

    public RateLimiterProvider(RateLimitProperties props,
                               TokenBucketRateLimiter tokenBucket,
                               SlidingWindowLogRateLimiter slidingLog,
                               SlidingWindowCounterRateLimiter slidingCounter) {
        this.active = switch (props.getAlgorithm()) {
            case "sliding_window_log" -> slidingLog;
            case "sliding_window_counter" -> slidingCounter;
            case "token_bucket" -> tokenBucket;
            default -> throw new IllegalArgumentException(
                    "Unknown rate limit algorithm: " + props.getAlgorithm());
        };
    }

    public RateLimiter get() {
        return active;
    }
}