package com.throttle;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ClientKeyResolver {

    /**
     * Resolve a stable client identity for rate limiting.
     * Behind a proxy/load balancer the originating IP is in X-Forwarded-For
     * (first entry in the comma-separated list). Fall back to the socket address.
     */
    public String resolve(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For: client, proxy1, proxy2 — the client is first.
            int comma = forwarded.indexOf(',');
            String first = (comma == -1) ? forwarded : forwarded.substring(0, comma);
            return first.trim();
        }
        String remote = request.getRemoteAddr();
        return (remote != null) ? remote : "unknown";
    }
}