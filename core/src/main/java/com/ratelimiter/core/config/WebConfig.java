package com.ratelimiter.core.config;

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