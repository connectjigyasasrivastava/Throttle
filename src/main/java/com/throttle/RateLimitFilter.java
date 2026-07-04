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

    private final RateLimiterProvider rateLimiterProvider;
    private final ClientKeyResolver keyResolver;
    private final RateLimitProperties props;

    public RateLimitFilter(RateLimiterProvider rateLimiterProvider,
                           ClientKeyResolver keyResolver,
                           RateLimitProperties props) {
        this.rateLimiterProvider = rateLimiterProvider;
        this.keyResolver = keyResolver;
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        RateLimiter rateLimiter = rateLimiterProvider.get();
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

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        // Never rate limit health/monitoring endpoints — load balancer
        // health checks must always succeed.
        if (uri.startsWith("/actuator")) {
            return true;
        }
        // Otherwise, only rate limit paths under the configured prefix.
        return !uri.startsWith(props.getPathPrefix());
    }
}