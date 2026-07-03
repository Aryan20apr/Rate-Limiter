package com.ratelimiter.core.strategy;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public abstract class AbstractRateLimiter implements RateLimiter, AutoCloseable {

    protected final ConcurrentHashMap<String, Object> store = new ConcurrentHashMap<>();

    protected final int maxKeys;
    protected final long ttlNanos;

    private final ScheduledExecutorService cleaner;

    protected AbstractRateLimiter(int maxKeys, long ttlMillis) {
        this.maxKeys = maxKeys;
        this.ttlNanos = ttlMillis * 1_000_000;

        this.cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rate-limiter-cleanup");
            t.setDaemon(true);
            return t;
        });
        cleaner.scheduleAtFixedRate(this::cleanupExpired, ttlMillis, ttlMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        cleaner.shutdown();
    }

    protected boolean isKeyLimitExceeded(String key) {
        return store.size() >= maxKeys && !store.containsKey(key);
    }

    protected void cleanupExpired() {
        long now = System.nanoTime();
        Iterator<Map.Entry<String, Object>> iterator = store.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, Object> entry = iterator.next();
            ExpirableState state = (ExpirableState) entry.getValue();
            if (now - state.getLastAccessNanos() > ttlNanos) {
                iterator.remove();
            }
        }
    }

    protected interface ExpirableState {
        long getLastAccessNanos();
    }
}