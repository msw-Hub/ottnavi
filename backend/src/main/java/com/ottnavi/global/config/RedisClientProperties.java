package com.ottnavi.global.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Redis 연결 보강 설정(app.redis.*). 설정이 없어도 아래 기본값으로 동작한다.
 * spring.data.redis.*(호스트·타임아웃 등)와 겹치지 않는 항목만 둔다.
 *
 * @param warmUp 기동 직후 연결 예열 설정
 */
@ConfigurationProperties(prefix = "app.redis")
public record RedisClientProperties(@DefaultValue WarmUp warmUp) {

	/**
	 * 기동 직후 연결 예열.
	 *
	 * @param enabled    켜기 여부
	 * @param retryDelay 예열이 하나라도 실패했을 때 실패한 쪽만 한 번 더 시도하기까지 기다리는 시간
	 */
	public record WarmUp(
			@DefaultValue("true") boolean enabled,
			@DefaultValue("30s") Duration retryDelay) {

		public WarmUp {
			if (retryDelay.isZero() || retryDelay.isNegative()) {
				throw new IllegalArgumentException("app.redis.warm-up.retry-delay는 0보다 커야 합니다.");
			}
		}
	}
}
