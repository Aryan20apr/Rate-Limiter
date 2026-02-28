public class RateLimiterDemo {

    public static void main(String[] args) throws Exception {

        RateLimiter fixed = new FixedWindowRateLimiter(5, 5000, 64, 10000, 60000);
        RateLimiter slidingLog = new SlidingWindowLogRateLimiter(5, 5000, 64, 10000, 60000, 100);
        RateLimiter slidingCounter = new SlidingWindowCounterRateLimiter(5, 5000, 64, 10000, 60000);
        RateLimiter token = new TokenBucketRateLimiter(5, 1, 64, 10000, 60000);
        RateLimiter leaky = new LeakyBucketRateLimiter(5, 1, 64, 10000, 60000);

        run("Fixed Window V2", fixed);
        run("Sliding Log V2", slidingLog);
        run("Sliding Counter V2", slidingCounter);
        run("Token Bucket V2", token);
        run("Leaky Bucket V2", leaky);
    }

    private static void run(String name, RateLimiter limiter) throws Exception {

        System.out.println("\n===== " + name + " =====");

        for (int i = 1; i <= 10; i++) {
            RateLimitResult r = limiter.allow("user:42");

            System.out.printf("Req %d -> allowed=%s remaining=%d retry=%dms%n",
                    i,
                    r.allowed(),
                    r.remainingTokens(),
                    r.retryAfterMillis());

            Thread.sleep(200);
        }

        Thread.sleep(6000);

        System.out.println("After reset:");
        System.out.println(limiter.allow("user:42"));
    }
}