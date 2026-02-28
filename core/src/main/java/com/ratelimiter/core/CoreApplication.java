package com.ratelimiter.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.ratelimiter.core.config.RateLimitProperties;

@SpringBootApplication
@EnableConfigurationProperties(RateLimitProperties.class)
public class CoreApplication {

	public static void main(String[] args) {
		SpringApplication.run(CoreApplication.class, args);
	}

}
