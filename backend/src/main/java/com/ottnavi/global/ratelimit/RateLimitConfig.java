package com.ottnavi.global.ratelimit;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 공개 API 요청 제한 빈을 만든다. app.rate-limit.enabled가 false면 필터도 Redis 연결도 만들지 않는다(기본 true).
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
@ConditionalOnProperty(prefix = "app.rate-limit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitConfig {

	/** Redis 분산 버킷 창구. 연결은 처음 쓸 때 만들고, 앱 종료 때 close()가 불린다 */
	@Bean
	RedisRateLimitBucketProvider rateLimitBucketProvider(RedisConnectionFactory connectionFactory) {
		return new RedisRateLimitBucketProvider(connectionFactory);
	}

	/** 공개 API IP별 일반 한도 검사기 */
	@Bean
	PublicRateLimiter publicRateLimiter(RedisRateLimitBucketProvider bucketProvider, RateLimitProperties props,
			MeterRegistry meterRegistry) {
		return new PublicRateLimiter(bucketProvider, props.general(), meterRegistry);
	}

	/** 단건 수집 IP별 한도 검사기. Task 050의 검색 서비스가 check(clientIp)를 부른다 */
	@Bean
	SingleFetchRateLimiter singleFetchRateLimiter(RedisRateLimitBucketProvider bucketProvider, RateLimitProperties props,
			MeterRegistry meterRegistry) {
		return new SingleFetchRateLimiter(bucketProvider, props.singleFetch(), meterRegistry);
	}

	/** 데모 로그인 IP별 시도 한도 검사기. DemoLoginService가 아이디·비밀번호를 비교하기 전에 check(clientIp)를 부른다 */
	@Bean
	DemoLoginRateLimiter demoLoginRateLimiter(RedisRateLimitBucketProvider bucketProvider, RateLimitProperties props,
			MeterRegistry meterRegistry) {
		return new DemoLoginRateLimiter(bucketProvider, props.demoLogin(), meterRegistry);
	}

	/** 요청 제한 서블릿 필터. 순서는 클래스의 @Order(HIGHEST_PRECEDENCE + 25)를 따른다 */
	@Bean
	PublicRateLimitFilter publicRateLimitFilter(PublicRateLimiter rateLimiter,
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
		return new PublicRateLimitFilter(rateLimiter, handlerExceptionResolver);
	}
}
