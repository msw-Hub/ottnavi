package com.ottnavi.home.job;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 인기 순위 수집 설정(app.popularity.*, Task 107). 값은 application.yml에 있다.
 *
 * @param topN 서비스·매체(영화/드라마)마다 저장하는 상위 작품 수(기본 20, TMDB discover 한 쪽이 20개라 20을 넘길 수 없다)
 * @param retentionDays 스냅샷을 보관하는 일수(기본 14). 이보다 오래된 날짜는 수집 Job이 지운다
 * @param recentYears 첫 공개일이 오늘(KST)에서 이 햇수 이내인 작품만 순위에 넣는다(기본 2). 영화 개봉일·드라마 첫 방영일 기준
 */
@ConfigurationProperties(prefix = "app.popularity")
public record PopularityProperties(int topN, int retentionDays, int recentYears) {

	public static final int TMDB_PAGE_SIZE = 20; // discover 한 쪽에 오는 결과 수

	public PopularityProperties {
		if (topN < 1 || topN > TMDB_PAGE_SIZE) {
			throw new IllegalArgumentException("topN은 1 이상 " + TMDB_PAGE_SIZE + " 이하여야 한다");
		}
		if (retentionDays < 1) {
			throw new IllegalArgumentException("retentionDays는 1 이상이어야 한다");
		}
		if (recentYears < 1) {
			throw new IllegalArgumentException("recentYears는 1 이상이어야 한다");
		}
	}
}
