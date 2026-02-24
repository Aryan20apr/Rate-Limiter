import java.util.concurrent.ConcurrentHashMap;

public class LeakyBucketRateLimiter implements RateLimiter {

    private static class BucketState {
        double water;
        long lastLeakTime;
    }

    private final double capacity;
    private final double leakPerNano;

    private final ConcurrentHashMap<String, BucketState> store = new ConcurrentHashMap<>();

    public LeakyBucketRateLimiter(double capacity, double leakPerSecond) {
        this.capacity = capacity;
        this.leakPerNano = leakPerSecond / 1_000_000_000.0;
    }

    @Override
    public RateLimitResult allow(String key) {

        long now = System.nanoTime();

        BucketState state = store.computeIfAbsent(key, k -> {
            BucketState bs = new BucketState();
            bs.lastLeakTime = now;
            return bs;
        });

        synchronized (state) {

            long delta = now - state.lastLeakTime;
            if (delta < 0) delta = 0;

            double leaked = delta * leakPerNano;
            state.water = Math.max(0, state.water - leaked);
            state.lastLeakTime = now;

            if (state.water < capacity) {
                state.water += 1;
                return new RateLimitResult(
                        true,
                        (long) (capacity - state.water),
                        0
                );
            }

            double nanosToDrain = (state.water - capacity + 1) / leakPerNano;
            long retryMillis = (long) nanosToDrain / 1_000_000;

            return new RateLimitResult(
                    false,
                    0,
                    retryMillis
            );
        }
    }
}