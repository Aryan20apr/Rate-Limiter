package com.ratelimiter.core.config;

import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.ratelimiter.core.service.RateLimitManager;
import com.ratelimiter.core.web.RateLimitInterceptor;

// @Configuration -- Not enabled for now as filter will work
public class WebConfig implements WebMvcConfigurer {

    private final RateLimitManager manager;

    public WebConfig(RateLimitManager manager) {
        this.manager = manager;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitInterceptor(manager));
    }
}