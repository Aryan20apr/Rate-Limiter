import java.util.concurrent.TimeUnit;

public class RateLimiterDemo {

    public static void main(String[] args) throws Exception {

        String key = "user:123";

        RateLimiter fixedWindow = new FixedWindowRateLimiter(5, 5000); // 5 req per 5 sec

        RateLimiter slidingLog = new SlidingWindowLogRateLimiter(5, 5000);

        RateLimiter slidingCounter = new SlidingWindowCounterRateLimiter(5, 5000);

        RateLimiter tokenBucket = new TokenBucketRateLimiter(5, 1); // capacity 5, refill 1/sec

        RateLimiter leakyBucket = new LeakyBucketRateLimiter(5, 1); // capacity 5, leak 1/sec

        System.out.println("\n================ FIXED WINDOW =================");
        runBurstTest(fixedWindow, key);

        System.out.println("\n================ SLIDING LOG =================");
        runBurstTest(slidingLog, key);

        System.out.println("\n================ SLIDING COUNTER =================");
        runBurstTest(slidingCounter, key);

        System.out.println("\n================ TOKEN BUCKET =================");
        runBurstTest(tokenBucket, key);

        System.out.println("\n================ LEAKY BUCKET =================");
        runBurstTest(leakyBucket, key);

        System.out.println("\n================ STEADY TRAFFIC (Token Bucket) =================");
        runSteadyTrafficTest(tokenBucket, key);

        System.out.println("\n=== Concurrency Test (Token Bucket) ===");

        Runnable task = () -> {
            for (int i = 0; i < 5; i++) {
                RateLimitResult result = tokenBucket.allow("concurrentKey");
                System.out.println(Thread.currentThread().getName() +
                        " -> allowed=" + result.allowed());
            }
        };

        for (int i = 0; i < 10; i++) {
            new Thread(task).start();
        }
    }

    private static void runBurstTest(RateLimiter limiter, String key) throws Exception {

        System.out.println("Sending 10 immediate requests...");

        for (int i = 1; i <= 10; i++) {
            RateLimitResult result = limiter.allow(key);

            System.out.printf(
                    "Request %d -> allowed=%s, remaining=%d, retryAfter=%dms%n",
                    i,
                    result.allowed(),
                    result.remainingTokens(),
                    result.retryAfterMillis());

            TimeUnit.MILLISECONDS.sleep(100);
        }

        System.out.println("Sleeping 6 seconds...\n");
        TimeUnit.SECONDS.sleep(6);

        for (int i = 1; i <= 5; i++) {
            RateLimitResult result = limiter.allow(key);

            System.out.printf(
                    "Post-sleep Request %d -> allowed=%s, remaining=%d%n",
                    i,
                    result.allowed(),
                    result.remainingTokens());
        }
    }

    private static void runSteadyTrafficTest(RateLimiter limiter, String key) throws Exception {

        for (int i = 1; i <= 15; i++) {

            RateLimitResult result = limiter.allow(key);

            System.out.printf(
                    "Steady Request %d -> allowed=%s, remaining=%d, retryAfter=%dms%n",
                    i,
                    result.allowed(),
                    result.remainingTokens(),
                    result.retryAfterMillis());

            TimeUnit.MILLISECONDS.sleep(1000);
        }
    }
}