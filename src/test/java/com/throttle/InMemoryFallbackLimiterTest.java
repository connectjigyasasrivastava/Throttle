package com.throttle;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryFallbackLimiterTest {

    private RateLimitProperties propsWith(int capacity, double refillRate) {
        RateLimitProperties props = new RateLimitProperties();
        props.setCapacity(capacity);
        props.setRefillRate(refillRate);
        return props;
    }

    @Test
    void allowsUpToCapacityThenDenies() {
        // Small refill rate so tokens don't meaningfully replenish mid-test.
        InMemoryFallbackLimiter limiter =
                new InMemoryFallbackLimiter(propsWith(5, 0.001));

        int allowed = 0;
        int denied = 0;
        for (int i = 0; i < 8; i++) {
            if (limiter.tryAcquire("client-a").allowed()) allowed++;
            else denied++;
        }

        assertThat(allowed).isEqualTo(5);   // exactly capacity
        assertThat(denied).isEqualTo(3);    // the rest denied
    }

    @Test
    void tracksClientsIndependently() {
        InMemoryFallbackLimiter limiter =
                new InMemoryFallbackLimiter(propsWith(2, 0.001));

        // Exhaust client-a.
        assertThat(limiter.tryAcquire("client-a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("client-a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("client-a").allowed()).isFalse();

        // client-b has its own independent bucket.
        assertThat(limiter.tryAcquire("client-b").allowed()).isTrue();
    }

    @Test
    void refillsOverTime() throws InterruptedException {
        // Refill 100/sec → a token returns roughly every 10ms.
        InMemoryFallbackLimiter limiter =
                new InMemoryFallbackLimiter(propsWith(1, 100.0));

        assertThat(limiter.tryAcquire("client-c").allowed()).isTrue();   // consume the one token
        assertThat(limiter.tryAcquire("client-c").allowed()).isFalse();  // empty now

        Thread.sleep(50); // ~5 tokens worth of time

        assertThat(limiter.tryAcquire("client-c").allowed()).isTrue();   // refilled
    }
}