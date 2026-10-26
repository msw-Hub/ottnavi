package com.ottnavi.home.service;

import com.ottnavi.catalog.domain.enums.MediaType;
import com.ottnavi.catalog.domain.rule.TitleDetailRules;
import com.ottnavi.home.service.model.PopularityItem;
import com.ottnavi.infra.tmdb.dto.TmdbMovieSummary;
import com.ottnavi.infra.tmdb.dto.TmdbPage;
import com.ottnavi.infra.tmdb.dto.TmdbTvSummary;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** TMDB discover 응답 한 쪽을 인기 작품 목록(PopularityItem)으로 바꾼다. 응답 DTO를 이 밖으로 넘기지 않는다 */
final class PopularityItemMapper {

	private static final int MAX_TITLE_LENGTH = 300; // popularity_snapshot.title 컬럼 길이

	private PopularityItemMapper() {
	}

	/** 영화 응답을 위에서부터 topN개까지 바꾼다. 제목·인기도가 없는 항목과 중복 ID는 건너뛴다 */
	static List<PopularityItem> fromMovies(TmdbPage<TmdbMovieSummary> page, int topN) {
		List<PopularityItem> items = new ArrayList<>();
		if (page == null || page.results() == null) {
			return items;
		}
		Set<Long> seenIds = new HashSet<>();
		for (TmdbMovieSummary movie : page.results()) {
			if (items.size() >= topN) {
				break;
			}
			if (!seenIds.add(movie.id())) {
				continue;
			}
			PopularityItem item = toItem(MediaType.MOVIE, movie.id(), movie.title(), movie.originalTitle(),
					movie.posterPath(), movie.releaseDate(), movie.popularity());
			if (item != null) {
				items.add(item);
			}
		}
		return items;
	}

	/** 드라마 응답을 위에서부터 topN개까지 바꾼다. 드라마의 날짜 필드는 first_air_date다 */
	static List<PopularityItem> fromTvs(TmdbPage<TmdbTvSummary> page, int topN) {
		List<PopularityItem> items = new ArrayList<>();
		if (page == null || page.results() == null) {
			return items;
		}
		Set<Long> seenIds = new HashSet<>();
		for (TmdbTvSummary tv : page.results()) {
			if (items.size() >= topN) {
				break;
			}
			if (!seenIds.add(tv.id())) {
				continue;
			}
			PopularityItem item = toItem(MediaType.TV, tv.id(), tv.name(), tv.originalName(), tv.posterPath(),
					tv.firstAirDate(), tv.popularity());
			if (item != null) {
				items.add(item);
			}
		}
		return items;
	}

	/** 한국어 제목이 없으면 원제를 쓴다. 제목이나 인기도가 없으면 저장할 수 없어 null(건너뜀) */
	private static PopularityItem toItem(MediaType mediaType, long id, String title, String originalTitle,
			String posterPath, String date, Double popularity) {
		// 1. 한국어 제목이 없으면 원제를 고르고, 둘 다 없으면 건너뛴다
		String chosen = title;
		if (chosen == null || chosen.isBlank()) {
			chosen = originalTitle;
		}
		if (chosen == null || chosen.isBlank()) {
			return null;
		}
		// 2. 인기도가 없으면 순위 재료가 안 되어 건너뛴다
		if (popularity == null) {
			return null;
		}
		// 3. 컬럼 길이·소수 자릿수에 맞춰 만든다
		if (chosen.length() > MAX_TITLE_LENGTH) {
			chosen = chosen.substring(0, MAX_TITLE_LENGTH);
		}
		return new PopularityItem(mediaType, Math.toIntExact(id), chosen, posterPath, TitleDetailRules.parseDate(date),
				BigDecimal.valueOf(popularity).setScale(3, RoundingMode.HALF_UP));
	}
}
