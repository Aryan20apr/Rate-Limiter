package com.ratelimiter.core.dtos;

public record RateLimitResult(
    boolean allowed,
    long remainingTokens,
    long retryAfterMillis
) {}
