package com.ottnavi.global.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ottnavi.global.error.RateLimitExceededException;
import com.ottnavi.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 실제 Redis(Testcontainers)에서 Bucket4j 분산 버킷이 한도·키·만료를 지키는지 검증한다.
 * 한도는 통 크기 3, 한 시간에 1개 충전이라 같은 IP의 네 번째 요청부터 거부된다.
 * 방문자 IP는 X-Forwarded-For 첫 값(ForwardedHeaderFilter가 remoteAddr에 반영)으로 바꿔 가며 쓴다.
 * Docker Desktop이 켜져 있어야 한다.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {
		"app.rate-limit.enabled=true",
		"app.rate-limit.general.capacity=3",
		"app.rate-limit.general.refill-tokens=1",
		"app.rate-limit.general.refill-period=1h",
		"app.rate-limit.single-fetch.capacity=2",
		"app.rate-limit.single-fetch.refill-tokens=1",
		"app.rate-limit.single-fetch.refill-period=1h"})
class PublicRateLimitRedisIntegrationTest extends IntegrationTestSupport {

	private static final String URL = "/api/public/ott-services";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Autowired
	private SingleFetchRateLimiter singleFetchRateLimiter;

	@Test
	void 단건_수집_버킷은_rl_fetch_키로_저장되고_통을_넘으면_거부한다() {
		// given: 통 크기(2)만큼 쓴다
		singleFetchRateLimiter.check("10.2.0.1");
		singleFetchRateLimiter.check("10.2.0.1");

		// when & then: 세 번째는 거부되고, 일반 버킷과 다른 키가 TTL과 함께 저장된다
		assertThatThrownBy(() -> singleFetchRateLimiter.check("10.2.0.1"))
				.isInstanceOf(RateLimitExceededException.class);
		Long ttlSeconds = redisTemplate.getExpire("rl:fetch:10.2.0.1");
		assertThat(ttlSeconds).isNotNull();
		assertThat(ttlSeconds).isGreaterThan(0L);
	}

	@Test
	void 한도를_넘으면_429와_Retry_After를_주고_다른_IP는_영향이_없다() throws Exception {
		// given: IP 10.1.0.1이 통 크기(3)만큼 요청한다
		for (int i = 0; i < 3; i++) {
			mockMvc.perform(get(URL).header("X-Forwarded-For", "10.1.0.1"))
					.andExpect(status().isOk());
		}

		// when & then: 네 번째는 거부된다
		mockMvc.perform(get(URL).header("X-Forwarded-For", "10.1.0.1"))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"))
				.andExpect(header().exists("Retry-After"));

		// 다른 IP는 같은 시각에도 통과한다
		mockMvc.perform(get(URL).header("X-Forwarded-For", "10.1.0.2"))
				.andExpect(status().isOk());
	}

	@Test
	void 버킷은_rl_public_IP_키로_저장되고_만료_시간이_붙는다() throws Exception {
		// given & when
		mockMvc.perform(get(URL).header("X-Forwarded-For", "10.1.0.3"))
				.andExpect(status().isOk());

		// then: Bucket4j가 버킷이 가득 차는 시점 기준으로 TTL을 건다(영구 키가 남지 않는다)
		Long ttlSeconds = redisTemplate.getExpire("rl:public:10.1.0.3");
		assertThat(ttlSeconds).isNotNull();
		assertThat(ttlSeconds).isGreaterThan(0L);
	}

	@Test
	void 인증_경로도_같은_IP_버킷을_쓴다() throws Exception {
		// given: 공개 경로로 통을 다 쓴다
		for (int i = 0; i < 3; i++) {
			mockMvc.perform(get(URL).header("X-Forwarded-For", "10.1.0.4"));
		}

		// when & then: 인증 경로(존재하지 않는 하위 경로여도 필터가 먼저 거부한다)도 429다
		mockMvc.perform(get("/api/auth/refresh").header("X-Forwarded-For", "10.1.0.4"))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().exists("Retry-After"));
	}
}
