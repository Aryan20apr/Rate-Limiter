public interface RateLimiter {
    RateLimitResult allow(String key);
}