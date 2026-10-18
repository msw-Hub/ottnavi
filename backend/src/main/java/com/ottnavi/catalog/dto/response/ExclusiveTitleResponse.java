package com.ottnavi.catalog.dto.response;

import com.ottnavi.catalog.service.model.ExclusiveTitleItem;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 독점 목록의 작품 하나(계약 ExclusiveTitle).
 *
 * @param mediaType              작품 구분(movie/tv)
 * @param tmdbId                 TMDB ID
 * @param title                  한국어 제목
 * @param releaseYear            개봉(첫 방영) 연도. 모르면 null
 * @param posterPath             TMDB 포스터 상대 경로. 없으면 null
 * @param exclusiveSeasonNumbers 드라마에서 이 서비스 독점으로 확인된 시즌 번호(오름차순). 영화는 null
 * @param comparedServiceCount   비교한 다른 서비스 수(구조적 공백 제외)
 * @param unknownElsewhereCount  비교한 다른 서비스 중 제공 여부가 모름인 곳의 수
 */
public record ExclusiveTitleResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"movie", "tv"}, example = "tv") String mediaType,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1396") long tmdbId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "브레이킹 배드") String title,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "2008") Integer releaseYear,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String posterPath,
		@ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true),
				schema = @Schema(nullable = false)) List<Integer> exclusiveSeasonNumbers, // 목록 자체만 null 가능, 원소는 null 아님
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "6") int comparedServiceCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1") int unknownElsewhereCount) {

	/** 독점 목록 항목을 응답으로 바꾼다 */
	public static ExclusiveTitleResponse from(ExclusiveTitleItem item) {
		return new ExclusiveTitleResponse(
				item.mediaType().toApiValue(),
				item.tmdbId(),
				item.title(),
				item.releaseYear(),
				item.posterPath(),
				item.exclusiveSeasonNumbers(),
				item.comparedServiceCount(),
				item.unknownElsewhereCount());
	}
}
