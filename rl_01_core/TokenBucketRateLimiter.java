import java.util.concurrent.ConcurrentHashMap;

public class TokenBucketRateLimiter implements RateLimiter {

    private static class BucketState {
        double tokens;
        long lastRefillNanos;
    }

    private final double capacity;
    private final double refillTokensPerNano;

    private final ConcurrentHashMap<String, BucketState> store = new ConcurrentHashMap<>();

    public TokenBucketRateLimiter(double capacity, double refillPerSecond) {
        this.capacity = capacity;
        this.refillTokensPerNano = refillPerSecond / 1_000_000_000.0;
    }

    @Override
    public RateLimitResult allow(String key) {

        long now = System.nanoTime();

        BucketState state = store.computeIfAbsent(key, k -> {
            BucketState bs = new BucketState();
            bs.tokens = capacity;
            bs.lastRefillNanos = now;
            return bs;
        });

        synchronized (state) {

            long delta = now - state.lastRefillNanos;
            if (delta < 0) delta = 0;

            double refill = delta * refillTokensPerNano;

            state.tokens = Math.min(capacity, state.tokens + refill);
            state.lastRefillNanos = now;

            if (state.tokens >= 1.0) {
                state.tokens -= 1.0;
                return new RateLimitResult(
                        true,
                        (long) state.tokens,
                        0
                );
            }

            double nanosToNextToken = (1.0 - state.tokens) / refillTokensPerNano;
            long retryMillis = (long) nanosToNextToken / 1_000_000;

            return new RateLimitResult(
                    false,
                    0,
                    retryMillis
            );
        }
    }
}