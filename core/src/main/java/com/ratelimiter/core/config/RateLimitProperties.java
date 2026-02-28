package com.ratelimiter.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.ratelimiter.core.dtos.RateLimitRule;

import java.util.List;

@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    private List<RateLimitRule> rules;

    public List<RateLimitRule> getRules() {
        return rules;
    }

    public void setRules(List<RateLimitRule> rules) {
        this.rules = rules;
    }
}
