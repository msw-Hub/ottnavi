package com.ottnavi.global.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 홈·탐색 응답 캐시 설정(app.home.cache.*, Task 107). 값을 바꾸면 Redis 캐시 유효 시간과 브라우저 캐시 시간이 바뀐다.
 * 설정이 없어도 아래 기본값으로 동작한다.
 *
 * @param popularityTtl    인기 순위 캐시 유효 시간(기본 60분)
 * @param newArrivalsTtl   새로 볼 수 있게 된 작품 캐시 유효 시간(기본 30분)
 * @param wishlistedTtl    찜 많은 작품 캐시 유효 시간(기본 10분). 찜이 바뀔 때마다 비우지 않고 이 시간에 맡긴다
 * @param genresTtl        장르 목록 캐시 유효 시간(기본 24시간)
 * @param genreTitlesTtl   장르별 작품 캐시 유효 시간(기본 30분)
 * @param browserSeconds   브라우저가 응답을 재사용해도 되는 시간(초, 기본 60). Cache-Control: public, max-age 값이다
 */
@ConfigurationProperties(prefix = "app.home.cache")
public record HomeCacheProperties(
		@DefaultValue("60m") Duration popularityTtl,
		@DefaultValue("30m") Duration newArrivalsTtl,
		@DefaultValue("10m") Duration wishlistedTtl,
		@DefaultValue("24h") Duration genresTtl,
		@DefaultValue("30m") Duration genreTitlesTtl,
		@DefaultValue("60") int browserSeconds) {

	public HomeCacheProperties {
		if (browserSeconds < 0) {
			throw new IllegalArgumentException("브라우저 캐시 시간은 0 이상이어야 한다");
		}
	}
}
