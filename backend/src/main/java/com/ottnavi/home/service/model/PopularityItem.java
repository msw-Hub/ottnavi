package com.ottnavi.home.service.model;

import com.ottnavi.catalog.domain.enums.MediaType;
import java.math.BigDecimal;
import java.time.LocalDate;

/** TMDB discover 응답에서 뽑은 인기 작품 한 건. 응답 DTO를 서비스 밖으로 넘기지 않으려고 필요한 값만 담는다 */
public record PopularityItem(
		MediaType mediaType, // 영화 / 드라마
		int tmdbId,
		String title, // 한국어 제목(없으면 원제)
		String posterPath, // 없으면 null
		LocalDate releaseDate, // 개봉일 / 첫 방영일, 모르면 null
		BigDecimal popularity) { // TMDB 인기도(그날 값)
}
