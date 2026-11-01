package com.ottnavi.infra.analytics;

import feign.RequestInterceptor;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;

/**
 * GA Feign 클라이언트 전용 설정.
 * @Configuration을 붙이지 않는다: 붙이면 컴포넌트 스캔에 잡혀 모든 Feign 클라이언트에 적용된다(TmdbFeignConfig와 같은 이유).
 * 타임아웃·로그 수준은 application.yml(spring.cloud.openfeign.client.config.ga)에 있다.
 */
public class GaFeignConfig {

	/** 호출마다 토큰 제공자에게 유효한 토큰을 받아 Bearer로 붙인다. 토큰을 못 받으면 AnalyticsCredentialsException이 그대로 올라간다 */
	@Bean
	RequestInterceptor gaAuthInterceptor(GaAccessTokenProvider tokenProvider) {
		return template -> template.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.getAccessToken());
	}

	/** 재시도하지 않는다. 방문자 카드 하나 때문에 느린 응답을 반복해 기다리거나 GA 일일 토큰 한도를 쓰지 않는다 */
	@Bean
	Retryer gaRetryer() {
		return Retryer.NEVER_RETRY;
	}

	/** 오류 응답을 고정 문구 예외로 바꾼다 */
	@Bean
	ErrorDecoder gaErrorDecoder() {
		return new GaErrorDecoder();
	}
}
