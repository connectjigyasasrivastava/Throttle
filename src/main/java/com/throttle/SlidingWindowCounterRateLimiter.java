package com.throttle;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class SlidingWindowCounterRateLimiter implements RateLimiter {

    private final StringRedisTemplate redis;
    private final RateLimitProperties props;
    private final DefaultRedisScript<List> script;

    public SlidingWindowCounterRateLimiter(StringRedisTemplate redis, RateLimitProperties props) {
        this.redis = redis;
        this.props = props;
        this.script = new DefaultRedisScript<>();
        this.script.setScriptSource(
                new ResourceScriptSource(new ClassPathResource("scripts/sliding_window_counter.lua")));
        this.script.setResultType(List.class);
    }

    @Override
    public RateLimitResult tryAcquire(String clientId) {
        String key = "rl:sliding_counter:" + clientId;
        long now = System.currentTimeMillis();

        List<Long> result = redis.execute(
                script,
                Collections.singletonList(key),
                String.valueOf(props.getLimit()),
                String.valueOf(props.getWindowMs()),
                String.valueOf(now)
        );

        long allowed = result.get(0);
        long remaining = result.get(1);
        long retryAfterMs = result.get(2);

        return new RateLimitResult(allowed == 1, remaining, retryAfterMs);
    }

    @Override
    public int capacity() {
        return props.getLimit();
    }
}