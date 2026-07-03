package com.ratelimiter.core.strategy;

import com.ratelimiter.core.dtos.RateLimitResult;

public class FixedWindowRateLimiter extends AbstractRateLimiter {

    private static class WindowState implements ExpirableState {
        long windowStart;
        long counter;
        long lastAccessNanos;

        @Override
        public long getLastAccessNanos() {
            return lastAccessNanos;
        }
    }

    private final long windowSizeNanos;
    private final long maxRequests;

    public FixedWindowRateLimiter(long maxRequests,
                                    long windowSizeMillis,
                                    int maxKeys,
                                    long ttlMillis) {
        super(maxKeys, ttlMillis);
        this.maxRequests = maxRequests;
        this.windowSizeNanos = windowSizeMillis * 1_000_000;
    }

    @Override
    public RateLimitResult allow(String key) {

        long limit = maxRequests;
        long now = System.nanoTime();

        if (isKeyLimitExceeded(key)) {
            return new RateLimitResult(false, 0, 0, limit, (System.currentTimeMillis() / 1000) + 60);
        }

        final RateLimitResult[] out = {null};

        store.compute(key, (k, existing) -> {
            WindowState state;
            if (existing == null) {
                state = new WindowState();
                state.windowStart = now;
            } else {
                state = (WindowState) existing;
            }

            state.lastAccessNanos = now;

            long elapsed = now - state.windowStart;
            if (elapsed >= windowSizeNanos) {
                state.windowStart = now;
                state.counter = 0;
                elapsed = 0;
            }

            long count = ++state.counter;

            long retryMillis = (windowSizeNanos - elapsed) / 1_000_000;
            long resetAt = (System.currentTimeMillis() + retryMillis) / 1000;

            out[0] = count <= maxRequests
                    ? new RateLimitResult(true, maxRequests - count, 0, limit, resetAt)
                    : new RateLimitResult(false, 0, retryMillis, limit, resetAt);

            return state;
        });

        return out[0];
    }
}
