package com.ratelimiter.core.strategy;

import com.ratelimiter.core.dtos.RateLimitResult;

public class LeakyBucketRateLimiter extends AbstractRateLimiter {

    private static class BucketState implements ExpirableState {
        double water;
        long lastLeak;
        long lastAccess;

        @Override
        public long getLastAccessNanos() {
            return lastAccess;
        }
    }

    private final double capacity;
    private final double leakPerNano;

    public LeakyBucketRateLimiter(double capacity,
                                    double leakPerSecond,
                                    int maxKeys,
                                    long ttlMillis) {
        super(maxKeys, ttlMillis);
        this.capacity = capacity;
        this.leakPerNano = leakPerSecond / 1_000_000_000.0;
    }

    @Override
    public RateLimitResult allow(String key) {

        long limit = (long) capacity;

        if (isKeyLimitExceeded(key)) {
            long resetAt = (System.currentTimeMillis() / 1000) + 60;
            return new RateLimitResult(false, 0, 0, limit, resetAt);
        }

        long now = System.nanoTime();

        final RateLimitResult[] out = {null};

        store.compute(key, (k, existing) -> {
            BucketState state;
            if (existing == null) {
                state = new BucketState();
                state.lastLeak = now;
            } else {
                state = (BucketState) existing;
            }

            state.lastAccess = now;

            long delta = now - state.lastLeak;
            state.water = Math.max(0, state.water - delta * leakPerNano);
            state.lastLeak = now;

            if (state.water < capacity) {
                state.water += 1;
                long resetAt = (System.currentTimeMillis() / 1000) + 60;
                out[0] = new RateLimitResult(true, (long) (capacity - state.water), 0, limit, resetAt);
            } else {
                long retryMillis = (long) ((state.water - capacity + 1) / leakPerNano) / 1_000_000;
                long resetAt = (System.currentTimeMillis() + retryMillis) / 1000;
                out[0] = new RateLimitResult(false, 0, retryMillis, limit, resetAt);
            }

            return state;
        });

        return out[0];
    }
}
