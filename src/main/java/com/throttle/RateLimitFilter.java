package com.throttle;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private final TokenBucketRateLimiter rateLimiter;
    private final ClientKeyResolver keyResolver;

    public RateLimitFilter(TokenBucketRateLimiter rateLimiter, ClientKeyResolver keyResolver) {
        this.rateLimiter = rateLimiter;
        this.keyResolver = keyResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String clientKey = keyResolver.resolve(request);
        RateLimitResult result = rateLimiter.tryAcquire(clientKey);

        // Informational headers on every response.
        response.setHeader("X-RateLimit-Limit", String.valueOf(rateLimiter.capacity()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));

        if (result.allowed()) {
            filterChain.doFilter(request, response); // pass through
        } else {
            // Retry-After is conventionally in seconds; round up from ms.
            long retryAfterSeconds = (result.retryAfterMs() + 999) / 1000;
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setStatus(429); // 429 Too Many Requests
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"rate limit exceeded\",\"retryAfterMs\":"
                            + result.retryAfterMs() + "}");
        }
    }
}