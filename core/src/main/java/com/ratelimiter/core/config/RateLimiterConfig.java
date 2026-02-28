package com.ratelimiter.core.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ratelimiter.core.service.RateLimitManager;
import com.ratelimiter.core.web.RateLimitFilter;

@Configuration
public class RateLimiterConfig {

	@Bean
	@ConditionalOnMissingBean(MeterRegistry.class)
	public MeterRegistry meterRegistry() {
		return new SimpleMeterRegistry();
	}

	@Bean
	public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(RateLimitManager manager) {
    FilterRegistrationBean<RateLimitFilter> bean = new FilterRegistrationBean<>();
    bean.setFilter(new RateLimitFilter(manager));
    bean.addUrlPatterns("/*");
    return bean;
}
}
