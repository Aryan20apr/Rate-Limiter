package com.ratelimiter.core.strategy;

import java.util.ArrayDeque;
import java.util.Deque;

import com.ratelimiter.core.dtos.RateLimitResult;

public class SlidingWindowLogRateLimiter extends AbstractRateLimiter {

    private static class LogState implements ExpirableState {
        final Deque<Long> timestamps = new ArrayDeque<>();
        long lastAccess;

        @Override
        public long getLastAccessNanos() {
            return lastAccess;
        }
    }

    private final long maxRequests;
    private final long windowNanos;
    private final int hardCap;

    public SlidingWindowLogRateLimiter(long maxRequests,
                                         long windowMillis,
                                         int maxKeys,
                                         long ttlMillis,
                                         int hardCap) {
        super(maxKeys, ttlMillis);
        this.maxRequests = maxRequests;
        this.windowNanos = windowMillis * 1_000_000;
        this.hardCap = hardCap;
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
            LogState state = existing == null ? new LogState() : (LogState) existing;
            state.lastAccess = now;

            Deque<Long> deque = state.timestamps;

            while (!deque.isEmpty() && now - deque.peekFirst() > windowNanos) {
                deque.pollFirst();
            }

            if (deque.size() >= hardCap) {
                long resetAt = (System.currentTimeMillis() / 1000) + 60;
                out[0] = new RateLimitResult(false, 0, 0, limit, resetAt);
                return state;
            }

            if (deque.size() < maxRequests) {
                deque.addLast(now);
                long resetAt = (System.currentTimeMillis() / 1000) + 60;
                out[0] = new RateLimitResult(true, maxRequests - deque.size(), 0, limit, resetAt);
                return state;
            }

            long oldest = deque.peekFirst();
            long retryMillis = (windowNanos - (now - oldest)) / 1_000_000;
            long resetAt = (System.currentTimeMillis() + retryMillis) / 1000;
            out[0] = new RateLimitResult(false, 0, retryMillis, limit, resetAt);
            return state;
        });

        return out[0];
    }
}
