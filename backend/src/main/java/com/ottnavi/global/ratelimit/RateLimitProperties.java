package com.ottnavi.global.ratelimit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 공개 API 요청 제한 설정(app.rate-limit.*). 값은 application.yml에 둔다 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
		boolean enabled,        // 요청 제한 사용 여부. 테스트에서만 끈다
		Limit general,          // 공개 API 전체에 IP별로 거는 일반 한도
		Limit singleFetch) {    // 단건 수집(DB에 없어 TMDB를 부르는 검색)에만 거는 더 낮은 한도

	/** 토큰 통 설정. 요청 한 번에 토큰 한 개를 쓰고, 통은 refillPeriod마다 refillTokens개씩 고르게 다시 찬다 */
	public record Limit(
			int capacity,           // 통 크기: 순간에 몰아서 보낼 수 있는 최대 요청 수
			int refillTokens,       // refillPeriod 동안 채워지는 토큰 수
			Duration refillPeriod) { // 채우는 주기
	}
}
