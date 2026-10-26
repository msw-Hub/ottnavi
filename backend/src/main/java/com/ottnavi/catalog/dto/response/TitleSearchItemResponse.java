package com.ottnavi.catalog.dto.response;

import com.ottnavi.catalog.service.model.ServiceStatus;
import com.ottnavi.catalog.service.model.TitleSearchItemView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

/**
 * 검색 결과의 작품 하나(계약 TitleSearchItem).
 *
 * @param mediaType      작품 구분(movie/tv)
 * @param tmdbId         TMDB ID
 * @param title          한국어 제목
 * @param releaseYear    개봉(첫 방영) 연도. 모르면 null
 * @param posterPath     TMDB 포스터 상대 경로. 없으면 null
 * @param availabilities 서비스별 제공 상태(7개)
 */
public record TitleSearchItemResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"movie", "tv"}, example = "movie") String mediaType,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "496243") long tmdbId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "기생충") String title,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "2019") Integer releaseYear,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String posterPath,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ServiceAvailabilityResponse> availabilities) {

	/** 검색 결과 항목을 응답으로 바꾼다 */
	public static TitleSearchItemResponse from(TitleSearchItemView item) {
		List<ServiceAvailabilityResponse> availabilities = new ArrayList<>();
		for (ServiceStatus status : item.availabilities()) {
			availabilities.add(ServiceAvailabilityResponse.from(status));
		}
		return new TitleSearchItemResponse(
				item.mediaType().toApiValue(),
				item.tmdbId(),
				item.title(),
				item.releaseYear(),
				item.posterPath(),
				availabilities);
	}
}
