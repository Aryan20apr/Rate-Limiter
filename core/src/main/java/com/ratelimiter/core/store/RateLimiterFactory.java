package com.ratelimiter.core.store;

import com.ratelimiter.core.dtos.RateLimitRule;
import com.ratelimiter.core.strategy.FixedWindowRateLimiter;
import com.ratelimiter.core.strategy.LeakyBucketRateLimiter;
import com.ratelimiter.core.strategy.RateLimiter;
import com.ratelimiter.core.strategy.SlidingWindowCounterRateLimiter;
import com.ratelimiter.core.strategy.SlidingWindowLogRateLimiter;
import com.ratelimiter.core.strategy.TokenBucketRateLimiter;

public class RateLimiterFactory {

    private static final int MAX_KEYS = 10_000;
    private static final long TTL_MILLIS = 60_000;

    private RateLimiterFactory() {
    }

    public static RateLimiter create(RateLimitRule rule) {
        return switch (rule.getAlgorithm().toLowerCase()) {
            case "fixed" ->
                new FixedWindowRateLimiter(
                rule.getMaxRequests(),
                rule.getWindowMillis(),
                MAX_KEYS, TTL_MILLIS);

            case "sliding-log" ->
                new SlidingWindowLogRateLimiter(
                rule.getMaxRequests(),
                rule.getWindowMillis(),
                MAX_KEYS, TTL_MILLIS, 1000);

            case "sliding-counter" ->
                new SlidingWindowCounterRateLimiter(
                rule.getMaxRequests(),
                rule.getWindowMillis(),
                MAX_KEYS, TTL_MILLIS);

            case "token" ->
                new TokenBucketRateLimiter(
                rule.getCapacity(),
                rule.getRefillPerSecond(),
                MAX_KEYS, TTL_MILLIS);

            case "leaky" ->
                new LeakyBucketRateLimiter(
                rule.getCapacity(),
                rule.getRefillPerSecond(),
                MAX_KEYS, TTL_MILLIS);

            default ->
                throw new IllegalArgumentException(
                        "Unknown algorithm: " + rule.getAlgorithm());
        };
    }

    public static boolean isRedisSupported(String algorithm) {
        return switch (algorithm.toLowerCase()) {
            case "token", "sliding-counter" ->
                true;
            default ->
                false;
        };
    }
}
