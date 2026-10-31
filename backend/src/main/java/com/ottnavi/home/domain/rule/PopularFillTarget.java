package com.ottnavi.home.domain.rule;

import com.ottnavi.catalog.domain.enums.MediaType;

/**
 * 인기 순위에서 뽑은 "상세를 채울 후보" 작품 하나.
 * 위치가 domain/rule인 이유: 순수 규칙(PopularityFillTargets)의 입력·출력이라 service 쪽 타입을 가져오면 의존 방향이 거꾸로 된다.
 */
public record PopularFillTarget(
		MediaType mediaType,
		int tmdbId,
		String fallbackTitle) { // 스냅샷에 기록된 제목. TMDB 응답에 제목이 없을 때 title 행의 제목으로 쓴다
}
