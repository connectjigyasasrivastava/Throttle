package com.throttle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RateLimitIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    StringRedisTemplate redis;

    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void clearBuckets() {
        // Start each test from a clean slate so token counts are deterministic.
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    private HttpResponse<String> hit() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/demo"))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void allowsRequestsUpToCapacityThenDenies() throws Exception {
        int allowed = 0;
        int denied = 0;
        for (int i = 0; i < 15; i++) {
            int status = hit().statusCode();
            if (status == 200) allowed++;
            else if (status == 429) denied++;
        }
        // Burst capacity (10) should pass; excess should be denied.
        assertThat(allowed).isGreaterThanOrEqualTo(10);
        assertThat(denied).isGreaterThan(0);
    }

    @Test
    void setsRateLimitHeaders() throws Exception {
        HttpResponse<String> response = hit();
        assertThat(response.headers().firstValue("X-RateLimit-Limit"))
                .contains("10");
        assertThat(response.headers().firstValue("X-RateLimit-Remaining"))
                .isPresent();
    }

    @Test
    void refillsTokensAfterWaiting() throws Exception {
        // Exhaust the bucket.
        for (int i = 0; i < 15; i++) {
            hit();
        }
        // Next request should be denied.
        assertThat(hit().statusCode()).isEqualTo(429);

        // Wait for tokens to refill (5/sec).
        Thread.sleep(1200);

        // Now a request should succeed again.
        assertThat(hit().statusCode()).isEqualTo(200);
    }
}