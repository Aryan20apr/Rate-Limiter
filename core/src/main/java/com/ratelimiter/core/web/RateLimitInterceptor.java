package com.ratelimiter.core.web;

import org.springframework.web.servlet.HandlerInterceptor;

import com.ratelimiter.core.dtos.RateLimitResult;
import com.ratelimiter.core.service.RateLimitManager;

import jakarta.servlet.http.*;

public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitManager manager;

    public RateLimitInterceptor(RateLimitManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        RateLimitResult result = manager.evaluate(request);

        if (!result.allowed()) {
            response.setStatus(429);
            response.setHeader("Retry-After",
                    String.valueOf(result.retryAfterMillis() / 1000));
            return false;
        }

        return true;
    }
}
