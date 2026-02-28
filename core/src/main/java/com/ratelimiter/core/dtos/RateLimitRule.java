package com.ratelimiter.core.dtos;

import com.ratelimiter.core.config.RateLimitScope;

import lombok.Data;

@Data
public class RateLimitRule {

    private String name;
    private RateLimitScope scope;
    private String algorithm;
    private long maxRequests;
    private long windowMillis;
    private double refillPerSecond;
    private double capacity;

   
}
