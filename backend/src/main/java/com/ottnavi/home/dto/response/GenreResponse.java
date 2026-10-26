package com.ottnavi.home.dto.response;

import com.ottnavi.catalog.service.model.GenreInfo;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 탐색용 장르 하나(계약 Genre).
 *
 * @param genreId TMDB 장르 ID
 * @param name    한국어 장르 이름
 */
public record GenreResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "16") int genreId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "애니메이션") String name) {

	/** 장르 정보를 응답으로 바꾼다 */
	public static GenreResponse from(GenreInfo genre) {
		return new GenreResponse(genre.id(), genre.name());
	}
}
