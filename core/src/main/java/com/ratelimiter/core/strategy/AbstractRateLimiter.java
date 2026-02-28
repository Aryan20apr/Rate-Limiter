package com.ratelimiter.core.strategy;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractRateLimiter implements RateLimiter {

    protected final ConcurrentHashMap<String, Object> store = new ConcurrentHashMap<>();

    protected final Object[] stripes;
    protected final int maxKeys;
    protected final long ttlNanos;

    protected AbstractRateLimiter(int stripeCount, int maxKeys, long ttlMillis) {
        this.stripes = new Object[stripeCount];
        for (int i = 0; i < stripeCount; i++) {
            stripes[i] = new Object();
        }
        this.maxKeys = maxKeys;
        this.ttlNanos = ttlMillis * 1_000_000;
    }

    protected Object stripe(String key) {
        return stripes[Math.abs(key.hashCode()) % stripes.length];
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