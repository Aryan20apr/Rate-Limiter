import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class SlidingWindowCounterRateLimiter implements RateLimiter {

    private static class CounterState {
        volatile long windowStartNanos;
        final AtomicLong currentCount = new AtomicLong(0);
        final AtomicLong previousCount = new AtomicLong(0);
    }

    private final long maxRequests;
    private final long windowSizeNanos;

    private final ConcurrentHashMap<String, CounterState> store = new ConcurrentHashMap<>();

    public SlidingWindowCounterRateLimiter(long maxRequests, long windowSizeMillis) {
        this.maxRequests = maxRequests;
        this.windowSizeNanos = windowSizeMillis * 1_000_000;
    }

    @Override
    public RateLimitResult allow(String key) {

        long now = System.nanoTime();

        CounterState state = store.computeIfAbsent(key, k -> {
            CounterState cs = new CounterState();
            cs.windowStartNanos = now;
            return cs;
        });

        synchronized (state) {

            long elapsed = now - state.windowStartNanos;
            if (elapsed < 0) elapsed = 0;

            if (elapsed >= windowSizeNanos) {
                state.previousCount.set(state.currentCount.get());
                state.currentCount.set(0);
                state.windowStartNanos = now;
                elapsed = 0;
            }

            double weight = (double) elapsed / windowSizeNanos;
            long estimatedCount = (long) (state.previousCount.get() * (1 - weight))
                    + state.currentCount.get();

            if (estimatedCount >= maxRequests) {

                long retryAfterNanos = windowSizeNanos - elapsed;
                return new RateLimitResult(false, 0, retryAfterNanos / 1_000_000);
            }

            state.currentCount.incrementAndGet();

            return new RateLimitResult(
                    true,
                    maxRequests - estimatedCount - 1,
                    0
            );
        }
    }
}