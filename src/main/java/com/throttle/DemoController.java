package com.throttle;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {

    /**
     * A simple protected endpoint under /api to exercise the rate limiter.
     * Requests here pass through the RateLimitFilter.
     */
    @GetMapping("/api/demo")
    public String demo() {
        return "ok";
    }
}