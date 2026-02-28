import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

public class SlidingWindowLogRateLimiter extends AbstractRateLimiter {

    private static class LogState implements ExpirableState {
        final Deque<Long> timestamps = new ConcurrentLinkedDeque<>();
        volatile long lastAccess;

        @Override
        public long getLastAccessNanos() {
            return lastAccess;
        }
    }

    private final long maxRequests;
    private final long windowNanos;
    private final int hardCap; // safety cap per key

    public SlidingWindowLogRateLimiter(long maxRequests,
                                         long windowMillis,
                                         int stripeCount,
                                         int maxKeys,
                                         long ttlMillis,
                                         int hardCap) {
        super(stripeCount, maxKeys, ttlMillis);
        this.maxRequests = maxRequests;
        this.windowNanos = windowMillis * 1_000_000;
        this.hardCap = hardCap;
    }

    @Override
    public RateLimitResult allow(String key) {

        if (isKeyLimitExceeded(key)) {
            return new RateLimitResult(false, 0, 0);
        }

        long now = System.nanoTime();

        LogState state = (LogState) store.computeIfAbsent(key, k -> {
            LogState s = new LogState();
            s.lastAccess = now;
            return s;
        });

        Object lock = stripe(key);

        synchronized (lock) {

            state.lastAccess = now;

            Deque<Long> deque = state.timestamps;

            while (!deque.isEmpty()) {
                long oldest = deque.peekFirst();
                if (now - oldest > windowNanos) {
                    deque.pollFirst();
                } else {
                    break;
                }
            }

            if (deque.size() >= hardCap) {
                return new RateLimitResult(false, 0, 0);
            }

            if (deque.size() < maxRequests) {
                deque.addLast(now);
                return new RateLimitResult(true,
                        maxRequests - deque.size(),
                        0);
            }

            long oldest = deque.peekFirst();
            long retryMillis = (windowNanos - (now - oldest)) / 1_000_000;

            return new RateLimitResult(false, 0, retryMillis);
        }
    }
}