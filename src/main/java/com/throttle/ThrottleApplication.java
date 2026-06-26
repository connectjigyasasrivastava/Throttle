package com.throttle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(RateLimitProperties.class)
public class ThrottleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ThrottleApplication.class, args);
    }
}