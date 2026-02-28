package com.ratelimiter.core.strategy;

import com.ratelimiter.core.dtos.RateLimitResult;

public class LeakyBucketRateLimiter extends AbstractRateLimiter {

    private static class BucketState implements ExpirableState {
        double water;
        long lastLeak;
        volatile long lastAccess;

        @Override
        public long getLastAccessNanos() {
            return lastAccess;
        }
    }

    private final double capacity;
    private final double leakPerNano;

    public LeakyBucketRateLimiter(double capacity,
                                    double leakPerSecond,
                                    int stripeCount,
                                    int maxKeys,
                                    long ttlMillis) {
        super(stripeCount, maxKeys, ttlMillis);
        this.capacity = capacity;
        this.leakPerNano = leakPerSecond / 1_000_000_000.0;
    }

    @Override
    public RateLimitResult allow(String key) {

        if (isKeyLimitExceeded(key)) {
            return new RateLimitResult(false, 0, 0);
        }

        long now = System.nanoTime();

        BucketState state = (BucketState) store.computeIfAbsent(key, k -> {
            BucketState bs = new BucketState();
            bs.lastLeak = now;
            bs.lastAccess = now;
            return bs;
        });

        Object lock = stripe(key);

        synchronized (lock) {

            state.lastAccess = now;

            long delta = now - state.lastLeak;
            double leaked = delta * leakPerNano;

            state.water = Math.max(0, state.water - leaked);
            state.lastLeak = now;

            if (state.water < capacity) {
                state.water += 1;
                return new RateLimitResult(true,
                        (long) (capacity - state.water),
                        0);
            }

            long retryMillis =
                    (long) ((state.water - capacity + 1) / leakPerNano) / 1_000_000;

            return new RateLimitResult(false, 0, retryMillis);
        }
    }
}
