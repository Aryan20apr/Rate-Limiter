public record RateLimitResult(
    boolean allowed,
    long remainingTokens,
    long retryAfterMillis
) {}