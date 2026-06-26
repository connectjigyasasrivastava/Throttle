package com.throttle;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PingController {

    private final StringRedisTemplate redis;

    public PingController(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @GetMapping("/ping")
    public String ping() {
        redis.opsForValue().set("throttle:ping", "ok");
        return "app=ok, redis=" + redis.opsForValue().get("throttle:ping");
    }
}