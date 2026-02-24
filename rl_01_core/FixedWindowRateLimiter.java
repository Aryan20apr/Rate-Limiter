import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class FixedWindowRateLimiter implements RateLimiter {

    private static class WindowState {

        volatile long windowStartNanos;
        final AtomicLong counter = new AtomicLong();
    }

    private final long windowSizeNanos;
    private final long maxRequests;

    private final ConcurrentHashMap<String, WindowState> store = new ConcurrentHashMap<>();

    public FixedWindowRateLimiter(long maxRequests, long windowSizeMillis) {
        this.maxRequests = maxRequests;
        this.windowSizeNanos = windowSizeMillis * 1_000_000;
    }

    @Override
    public RateLimitResult allow(String key) {

        long now = System.nanoTime();
        WindowState state = store.computeIfAbsent(key, k -> {
            WindowState ws = new WindowState();
            ws.windowStartNanos = now;
            return ws;
        });

        synchronized (state) {
            long elapsed = now - state.windowStartNanos;

            if (elapsed < 0) {
                elapsed = 0; // monotonic safeguard
            }

            if (elapsed >= windowSizeNanos) {
                state.windowStartNanos = now;
                state.counter.set(0);
            }

            long current = state.counter.incrementAndGet();

            if (current <= maxRequests) {
                return new RateLimitResult(true, maxRequests - current, 0);
            }

            long retryAfterNanos = windowSizeNanos - elapsed;
            long retryMillis = retryAfterNanos / 1_000_000;

            return new RateLimitResult(
                    false,
                    0,
                    retryMillis);

        }
    }

}
