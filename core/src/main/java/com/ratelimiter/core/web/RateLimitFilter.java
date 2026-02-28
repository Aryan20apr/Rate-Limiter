package com.ratelimiter.core.web;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

import com.ratelimiter.core.dtos.RateLimitResult;
import com.ratelimiter.core.service.RateLimitManager;

public class RateLimitFilter implements Filter {

    private final RateLimitManager manager;

    public RateLimitFilter(RateLimitManager manager) {
        this.manager = manager;
    }

    @Override
    public void doFilter(ServletRequest request,
                         ServletResponse response,
                         FilterChain chain)
            throws IOException, ServletException {

        var httpReq = (HttpServletRequest) request;
        var httpRes = (HttpServletResponse) response;

        RateLimitResult result = manager.evaluate(httpReq);

        if (!result.allowed()) {
            httpRes.setStatus(429);
            httpRes.setHeader("Retry-After",
                    String.valueOf(result.retryAfterMillis() / 1000));
            httpRes.getWriter().write("Rate limit exceeded");
            return;
        }

        httpRes.setHeader("X-RateLimit-Remaining",
                String.valueOf(result.remainingTokens()));

        chain.doFilter(request, response);
    }
}
