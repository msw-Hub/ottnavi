package com.ottnavi.llmdraft.dto.response;

import com.ottnavi.llmdraft.service.model.CandidateItemView;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 번역 후보 작품 한 건(계약 OverviewCandidate).
 *
 * @param titleId       작품 ID
 * @param mediaType     작품 구분(movie/tv)
 * @param title         작품 제목
 * @param overview      영어 줄거리 전문
 * @param wishlistCount 이 작품을 찜한 사용자 수
 * @param hasDraft      이 작품에 번역 초안이 있는지
 */
public record OverviewCandidateResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1024") Long titleId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"movie", "tv"}, example = "movie") String mediaType,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "기생충") String title,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "All unemployed, Ki-taek's family takes peculiar interest in the wealthy Park family.") String overview,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "12") long wishlistCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "false") boolean hasDraft) {

	/** 서비스 계층 값을 응답으로 바꾼다 */
	public static OverviewCandidateResponse from(CandidateItemView item) {
		return new OverviewCandidateResponse(item.candidate().titleId(), item.candidate().mediaType().toApiValue(),
				item.candidate().title(), item.candidate().overview(), item.candidate().wishlistCount(), item.hasDraft());
	}
}
