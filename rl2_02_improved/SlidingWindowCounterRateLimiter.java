import java.util.concurrent.atomic.LongAdder;

public class SlidingWindowCounterRateLimiter extends AbstractRateLimiter {

    private static class CounterState implements ExpirableState {
        volatile long windowStart;
        final LongAdder currentCount = new LongAdder();
        volatile long previousCount;
        volatile long lastAccess;

        @Override
        public long getLastAccessNanos() {
            return lastAccess;
        }
    }

    private final long maxRequests;
    private final long windowNanos;

    public SlidingWindowCounterRateLimiter(long maxRequests,
                                             long windowMillis,
                                             int stripeCount,
                                             int maxKeys,
                                             long ttlMillis) {
        super(stripeCount, maxKeys, ttlMillis);
        this.maxRequests = maxRequests;
        this.windowNanos = windowMillis * 1_000_000;
    }

    @Override
    public RateLimitResult allow(String key) {

        if (isKeyLimitExceeded(key)) {
            return new RateLimitResult(false, 0, 0);
        }

        long now = System.nanoTime();

        CounterState state = (CounterState) store.computeIfAbsent(key, k -> {
            CounterState cs = new CounterState();
            cs.windowStart = now;
            cs.lastAccess = now;
            return cs;
        });

        Object lock = stripe(key);

        synchronized (lock) {

            state.lastAccess = now;

            long elapsed = now - state.windowStart;

            if (elapsed >= windowNanos) {
                state.previousCount = state.currentCount.sum();
                state.currentCount.reset();
                state.windowStart = now;
                elapsed = 0;
            }

            double weight = (double) elapsed / windowNanos;
            long estimated = (long) (state.previousCount * (1 - weight))
                    + state.currentCount.sum();

            if (estimated >= maxRequests) {
                long retryMillis = (windowNanos - elapsed) / 1_000_000;
                return new RateLimitResult(false, 0, retryMillis);
            }

            state.currentCount.increment();

            return new RateLimitResult(true,
                    maxRequests - estimated - 1,
                    0);
        }
    }
}