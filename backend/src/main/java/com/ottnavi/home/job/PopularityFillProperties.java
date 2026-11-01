package com.ottnavi.home.job;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 인기 순위 상위 작품의 상세 채우기 설정(app.popularity.fill.*). 값은 application.yml에 있다.
 *
 * @param enabled   false면 채우기를 하지 않는다(기본 true)
 * @param topN      순위 16개(기간 2 x 범위 8) 각각에서 보는 상위 작품 수(기본 20, 홈이 보여 주는 최대 개수)
 * @param maxTitles 한 번에 채우는 최대 작품 수(기본 20). TMDB 호출량(작품당 수 회 ~ 시즌 수 x 2회)을 묶는 안전장치다
 */
@ConfigurationProperties(prefix = "app.popularity.fill")
public record PopularityFillProperties(boolean enabled, int topN, int maxTitles) {

	public PopularityFillProperties {
		if (topN < 1 || topN > PopularityProperties.TMDB_PAGE_SIZE) {
			throw new IllegalArgumentException("topN은 1 이상 " + PopularityProperties.TMDB_PAGE_SIZE + " 이하여야 한다");
		}
		if (maxTitles < 1) {
			throw new IllegalArgumentException("maxTitles는 1 이상이어야 한다");
		}
	}
}
