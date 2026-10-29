package com.ottnavi.global.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * app.rate-limit.enabled에 따라 요청 제한 빈이 등록되는지 검증한다.
 * 실제 Redis에는 연결하지 않는다(연결은 처음 쓸 때 만들고, 여기서는 쓰지 않는다. Docker 불필요).
 */
class RateLimitConfigTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(RateLimitConfig.class)
			.withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
			.withBean(MeterRegistry.class, SimpleMeterRegistry::new)
			.withBean("handlerExceptionResolver", HandlerExceptionResolver.class, () -> mock(HandlerExceptionResolver.class))
			.withPropertyValues(
					"app.rate-limit.general.capacity=20",
					"app.rate-limit.general.refill-tokens=60",
					"app.rate-limit.general.refill-period=1m",
					"app.rate-limit.single-fetch.capacity=3",
					"app.rate-limit.single-fetch.refill-tokens=10",
					"app.rate-limit.single-fetch.refill-period=1m",
					"app.rate-limit.demo-login.capacity=5",
					"app.rate-limit.demo-login.refill-tokens=5",
					"app.rate-limit.demo-login.refill-period=15m");

	@Test
	void enabled가_true거나_없으면_필터와_세_검사기가_등록된다() {
		// given & when & then
		runner.run(context -> {
			assertThat(context).hasSingleBean(PublicRateLimitFilter.class);
			assertThat(context).hasSingleBean(PublicRateLimiter.class);
			assertThat(context).hasSingleBean(SingleFetchRateLimiter.class);
			assertThat(context).hasSingleBean(DemoLoginRateLimiter.class);
		});
	}

	@Test
	void enabled가_false면_빈이_하나도_등록되지_않는다() {
		// given & when & then
		runner.withPropertyValues("app.rate-limit.enabled=false").run(context -> {
			assertThat(context).doesNotHaveBean(PublicRateLimitFilter.class);
			assertThat(context).doesNotHaveBean(PublicRateLimiter.class);
			assertThat(context).doesNotHaveBean(SingleFetchRateLimiter.class);
			assertThat(context).doesNotHaveBean(DemoLoginRateLimiter.class);
			assertThat(context).doesNotHaveBean(RedisRateLimitBucketProvider.class);
		});
	}
}
