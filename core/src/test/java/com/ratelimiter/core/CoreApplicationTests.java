package com.ratelimiter.core;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(CoreApplicationTests.TestConfig.class)
class CoreApplicationTests {

	@Test
	void contextLoads() {
	}

	@Configuration
	static class TestConfig {
		@Bean
		io.micrometer.core.instrument.MeterRegistry meterRegistry() {
			return new SimpleMeterRegistry();
		}
	}
}
