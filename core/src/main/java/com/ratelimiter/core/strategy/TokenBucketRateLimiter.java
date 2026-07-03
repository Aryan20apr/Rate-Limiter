package com.ratelimiter.core.strategy;

import com.ratelimiter.core.dtos.RateLimitResult;

public class TokenBucketRateLimiter extends AbstractRateLimiter {

    private static class BucketState implements ExpirableState {
        double tokens;
        long lastRefill;
        long lastAccessNanos;

        @Override
        public long getLastAccessNanos() {
            return lastAccessNanos;
        }
    }

    private final double capacity;
    private final double refillPerNano;

    public TokenBucketRateLimiter(double capacity,
                                    double refillPerSecond,
                                    int maxKeys,
                                    long ttlMillis) {
        super(maxKeys, ttlMillis);
        this.capacity = capacity;
        this.refillPerNano = refillPerSecond / 1_000_000_000.0;
    }

    @Override
    public RateLimitResult allow(String key) {

        long limit = (long) capacity;
        long now = System.nanoTime();

        if (isKeyLimitExceeded(key)) {
            long resetAt = (System.currentTimeMillis() / 1000) + 60;
            return new RateLimitResult(false, 0, 0, limit, resetAt);
        }

        final RateLimitResult[] out = {null};

        store.compute(key, (k, existing) -> {
            BucketState state;
            if (existing == null) {
                state = new BucketState();
                state.tokens = capacity;
                state.lastRefill = now;
            } else {
                state = (BucketState) existing;
            }

            state.lastAccessNanos = now;

            long delta = now - state.lastRefill;
            state.tokens = Math.min(capacity, state.tokens + delta * refillPerNano);
            state.lastRefill = now;

            if (state.tokens >= 1) {
                state.tokens -= 1;
                long resetAt = (System.currentTimeMillis() / 1000) + 60;
                out[0] = new RateLimitResult(true, (long) state.tokens, 0, limit, resetAt);
            } else {
                long retry = (long) ((1 - state.tokens) / refillPerNano) / 1_000_000;
                long resetAt = (System.currentTimeMillis() + retry) / 1000;
                out[0] = new RateLimitResult(false, 0, retry, limit, resetAt);
            }

            return state;
        });

        return out[0];
    }
}
