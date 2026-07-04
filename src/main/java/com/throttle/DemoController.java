package com.throttle;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class DemoController {

    /**
     * A protected API resource. Every request here passes through the
     * RateLimitFilter, so callers exceeding their bucket receive a 429.
     */
    @GetMapping("/api/demo")
    public Map<String, Object> demo() {
        return Map.of(
                "message", "request served",
                "timestamp", Instant.now().toString()
        );
    }

    /**
     * A second protected resource to show the limiter applies across all
     * routes under the configured prefix, keyed per client (not per path).
     */
    @GetMapping("/api/resource/{id}")
    public Map<String, Object> resource(@PathVariable String id) {
        return Map.of(
                "resourceId", id,
                "status", "available",
                "timestamp", Instant.now().toString()
        );
    }
}