package com.ottnavi.home.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 홈 공통 목록 설정(app.home.*, Task 107). 값은 application.yml에 있고 여기를 바꾸면 규칙이 바뀐다.
 *
 * @param minWishlistUsers "찜 많은 작품"에 넣는 최소 찜 사용자 수(기본 3). 이보다 적은 작품은 뺀다(개인 식별 방지)
 * @param arrivalDays "새로 볼 수 있게 된 작품"에서 입고(없음·모름 → 있음)로 보는 최근 일수(기본 14)
 * @param releaseDays 첫 적재 작품을 공개로 보는 공개일의 최근 일수(기본 30)
 */
@ConfigurationProperties(prefix = "app.home")
public record HomeProperties(int minWishlistUsers, int arrivalDays, int releaseDays) {

	public HomeProperties {
		if (minWishlistUsers < 1 || arrivalDays < 1 || releaseDays < 1) {
			throw new IllegalArgumentException("홈 설정 값은 모두 1 이상이어야 한다");
		}
	}
}
