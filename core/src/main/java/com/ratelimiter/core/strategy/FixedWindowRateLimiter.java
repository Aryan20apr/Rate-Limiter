package com.ratelimiter.core.strategy;

import java.util.concurrent.atomic.LongAdder;

import com.ratelimiter.core.dtos.RateLimitResult;

public class FixedWindowRateLimiter extends AbstractRateLimiter {

    private static class WindowState implements ExpirableState {
        volatile long windowStart;
        final LongAdder counter = new LongAdder();
        volatile long lastAccessNanos;
        @Override
        public long getLastAccessNanos() {
            return lastAccessNanos;
        }
    }

    private final long windowSizeNanos;
    private final long maxRequests;

    public FixedWindowRateLimiter(long maxRequests,
                                    long windowSizeMillis,
                                    int stripes,
                                    int maxKeys,
                                    long ttlMillis) {
        super(stripes, maxKeys, ttlMillis);
        this.maxRequests = maxRequests;
        this.windowSizeNanos = windowSizeMillis * 1_000_000;
    }

    @Override
    public RateLimitResult allow(String key) {

        long now = System.nanoTime();

        if (isKeyLimitExceeded(key)) {
            return new RateLimitResult(false, 0, 0);
        }

        WindowState state = (WindowState) store.computeIfAbsent(key, k -> {
            WindowState ws = new WindowState();
            ws.windowStart = now;
            ws.lastAccessNanos = now;
            return ws;
        });

        Object lock = stripe(key);

        synchronized (lock) {

            state.lastAccessNanos = now;

            long elapsed = now - state.windowStart;
            if (elapsed >= windowSizeNanos) {
                state.windowStart = now;
                state.counter.reset();
            }

            state.counter.increment();
            long count = state.counter.sum();

            if (count <= maxRequests) {
                return new RateLimitResult(true,
                        maxRequests - count,
                        0);
            }

            long retry = (windowSizeNanos - elapsed) / 1_000_000;
            return new RateLimitResult(false, 0, retry);
        }
    }
}
