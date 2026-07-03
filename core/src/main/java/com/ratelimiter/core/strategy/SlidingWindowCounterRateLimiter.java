package com.ratelimiter.core.strategy;

import com.ratelimiter.core.dtos.RateLimitResult;

public class SlidingWindowCounterRateLimiter extends AbstractRateLimiter {

    private static class CounterState implements ExpirableState {
        long windowStart;
        long currentCount;
        long previousCount;
        long lastAccess;

        @Override
        public long getLastAccessNanos() {
            return lastAccess;
        }
    }

    private final long maxRequests;
    private final long windowNanos;

    public SlidingWindowCounterRateLimiter(long maxRequests,
                                             long windowMillis,
                                             int maxKeys,
                                             long ttlMillis) {
        super(maxKeys, ttlMillis);
        this.maxRequests = maxRequests;
        this.windowNanos = windowMillis * 1_000_000;
    }

    @Override
    public RateLimitResult allow(String key) {

        long limit = maxRequests;

        if (isKeyLimitExceeded(key)) {
            long resetAt = (System.currentTimeMillis() / 1000) + 60;
            return new RateLimitResult(false, 0, 0, limit, resetAt);
        }

        long now = System.nanoTime();

        final RateLimitResult[] out = {null};

        store.compute(key, (k, existing) -> {
            CounterState state;
            if (existing == null) {
                state = new CounterState();
                state.windowStart = now;
            } else {
                state = (CounterState) existing;
            }

            state.lastAccess = now;

            long elapsed = now - state.windowStart;

            if (elapsed >= windowNanos) {
                state.previousCount = state.currentCount;
                state.currentCount = 0;
                state.windowStart = now;
                elapsed = 0;
            }

            double weight = (double) elapsed / windowNanos;
            long estimated = (long) (state.previousCount * (1 - weight)) + state.currentCount;

            if (estimated >= maxRequests) {
                long retryMillis = (windowNanos - elapsed) / 1_000_000;
                long resetAt = (System.currentTimeMillis() + retryMillis) / 1000;
                out[0] = new RateLimitResult(false, 0, retryMillis, limit, resetAt);
                return state;
            }

            state.currentCount++;

            long resetAt = (System.currentTimeMillis() / 1000) + 60;
            out[0] = new RateLimitResult(true, maxRequests - estimated - 1, 0, limit, resetAt);
            return state;
        });

        return out[0];
    }
}
