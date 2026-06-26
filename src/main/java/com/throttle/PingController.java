package com.throttle;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PingController {

    private final StringRedisTemplate redis;
    private final TokenBucketRateLimiter rateLimiter;

    public PingController(StringRedisTemplate redis, TokenBucketRateLimiter rateLimiter) {
        this.redis = redis;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/ping")
    public String ping() {
        redis.opsForValue().set("throttle:ping", "ok");
        return "app=ok, redis=" + redis.opsForValue().get("throttle:ping");
    }

    // Temporary endpoint to manually verify the token bucket. Removed in Phase 3.
    @GetMapping("/try")
    public String tryAcquire(@RequestParam(defaultValue = "demo") String client) {
        RateLimitResult r = rateLimiter.tryAcquire(client);
        return String.format("allowed=%s remaining=%d retryAfterMs=%d",
                r.allowed(), r.remaining(), r.retryAfterMs());
    }
}