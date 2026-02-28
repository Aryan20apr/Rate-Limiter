package com.ratelimiter.core.service;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.ratelimiter.core.config.RateLimitProperties;
import com.ratelimiter.core.dtos.RateLimitResult;
import com.ratelimiter.core.dtos.RateLimitRule;
import com.ratelimiter.core.strategy.FixedWindowRateLimiter;
import com.ratelimiter.core.strategy.LeakyBucketRateLimiter;
import com.ratelimiter.core.strategy.RateLimiter;
import com.ratelimiter.core.strategy.SlidingWindowCounterRateLimiter;
import com.ratelimiter.core.strategy.SlidingWindowLogRateLimiter;
import com.ratelimiter.core.strategy.TokenBucketRateLimiter;

@Service
@Slf4j
public class RateLimitManager {

    private final Map<String, RateLimiter> limiterMap = new ConcurrentHashMap<>();
    private final List<RateLimitRule> rules;
    private final MeterRegistry meterRegistry;

    public RateLimitManager(RateLimitProperties properties,
                            MeterRegistry meterRegistry) {

        this.rules = properties.getRules();
        this.meterRegistry = meterRegistry;

        for (RateLimitRule rule : rules) {
            limiterMap.put(rule.getName(), createLimiter(rule));
        }
    }

    public RateLimitResult evaluate(HttpServletRequest request) {

        String user = Optional.ofNullable(request.getHeader("X-User-Id"))
                .orElse("anonymous");

        String endpoint = request.getRequestURI();

        boolean allowed = true;
        long minRemaining = Long.MAX_VALUE;
        long maxRetry = 0;

        for (RateLimitRule rule : rules) {

            String key = generateKey(rule, user, endpoint);

            RateLimiter limiter = limiterMap.get(rule.getName());

            RateLimitResult result = limiter.allow(key);

            log.info("RateLimit Rule: {}-{}, Key: {}, Result: {}", rule.getName(), rule.getAlgorithm(), key, result);


            if (!result.allowed()) {
                allowed = false;
                maxRetry = Math.max(maxRetry, result.retryAfterMillis());
                meterRegistry.counter("rate_limit.rejected",
                        "rule", rule.getName()).increment();
            }

            minRemaining = Math.min(minRemaining, result.remainingTokens());
        }

        return new RateLimitResult(allowed, minRemaining, maxRetry);
    }

    private String generateKey(RateLimitRule rule,
                               String user,
                               String endpoint) {

        return switch (rule.getScope()) {
            case GLOBAL -> "global";
            case USER -> "user:" + user;
            case ENDPOINT -> "endpoint:" + endpoint;
            case USER_ENDPOINT -> "user:" + user + ":endpoint:" + endpoint;
        };
    }

    private RateLimiter createLimiter(RateLimitRule rule) {

        return switch (rule.getAlgorithm().toLowerCase()) {
            case "fixed" ->
                    new FixedWindowRateLimiter(
                            rule.getMaxRequests(),
                            rule.getWindowMillis(),
                            64, 10000, 60000);

            case "sliding-log" ->
                    new SlidingWindowLogRateLimiter(
                            rule.getMaxRequests(),
                            rule.getWindowMillis(),
                            64, 10000, 60000, 1000);

            case "sliding-counter" ->
                    new SlidingWindowCounterRateLimiter(
                            rule.getMaxRequests(),
                            rule.getWindowMillis(),
                            64, 10000, 60000);

            case "token" ->
                    new TokenBucketRateLimiter(
                            rule.getCapacity(),
                            rule.getRefillPerSecond(),
                            64, 10000, 60000);

            case "leaky" ->
                    new LeakyBucketRateLimiter(
                            rule.getCapacity(),
                            rule.getRefillPerSecond(),
                            64, 10000, 60000);

            default -> throw new IllegalArgumentException("Unknown algorithm");
        };
    }
}
