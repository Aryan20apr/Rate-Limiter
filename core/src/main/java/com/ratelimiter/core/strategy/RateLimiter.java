package com.ratelimiter.core.strategy;

import com.ratelimiter.core.dtos.RateLimitResult;

public interface RateLimiter {
    RateLimitResult allow(String key);
}

