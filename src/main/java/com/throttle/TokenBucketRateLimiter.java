package com.throttle;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class TokenBucketRateLimiter {

    private final StringRedisTemplate redis;
    private final RateLimitProperties props;
    private final DefaultRedisScript<List> script;

    public TokenBucketRateLimiter(StringRedisTemplate redis, RateLimitProperties props) {
        this.redis = redis;
        this.props = props;

        // Load the Lua script from the classpath once; Redis caches it by SHA.
        this.script = new DefaultRedisScript<>();
        this.script.setScriptSource(
                new ResourceScriptSource(new ClassPathResource("scripts/token_bucket.lua")));
        this.script.setResultType(List.class);
    }

    public RateLimitResult tryAcquire(String clientId) {
        String key = "rl:token_bucket:" + clientId;
        long now = System.currentTimeMillis();

        List<Long> result = redis.execute(
                script,
                Collections.singletonList(key),
                String.valueOf(props.getCapacity()),
                String.valueOf(props.getRefillRate()),
                String.valueOf(now),
                "1" // tokens requested per call
        );

        long allowed = result.get(0);
        long remaining = result.get(1);
        long retryAfterMs = result.get(2);

        return new RateLimitResult(allowed == 1, remaining, retryAfterMs);
    }
}