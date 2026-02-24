import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class SlidingWindowLogRateLimiter implements RateLimiter {

    private final long maxRequests;
    private final long windowSizeNanos;

    private final ConcurrentHashMap<String, Deque<Long>> store = new ConcurrentHashMap<>();

    public SlidingWindowLogRateLimiter(long maxRequests, long windowSizeMillis) {
        this.maxRequests = maxRequests;
        this.windowSizeNanos = windowSizeMillis * 1_000_000;
    }

    @Override
    public RateLimitResult allow(String key) {

        long now = System.nanoTime();

        Deque<Long> deque = store.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());

        synchronized (deque) {

            while (!deque.isEmpty()) {
                long timestamp = deque.peekFirst();
                if (now - timestamp > windowSizeNanos) {
                    deque.pollFirst();
                } else {
                    break;
                }
            }

            if (deque.size() < maxRequests) {
                deque.addLast(now);
                return new RateLimitResult(
                        true,
                        maxRequests - deque.size(),
                        0
                );
            }

            long oldest = deque.peekFirst();
            long retryAfterNanos = windowSizeNanos - (now - oldest);
            long retryMillis = retryAfterNanos / 1_000_000;

            return new RateLimitResult(
                    false,
                    0,
                    retryMillis
            );
        }
    }
}