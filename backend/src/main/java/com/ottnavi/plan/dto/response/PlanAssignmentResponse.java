package com.ottnavi.plan.dto.response;

import com.ottnavi.plan.service.model.PlanDraftView;
import com.ottnavi.wishlist.domain.enums.WishlistPriority;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 그 달에 볼 작품 배정 하나(계약 PlanAssignment). 긴 시즌은 연속된 달에 걸쳐 조각으로 나뉜다.
 *
 * @param planItemId     플랜 항목 ID(같은 시즌의 조각들은 같은 값)
 * @param mediaType      작품 구분(movie/tv)
 * @param tmdbId         TMDB ID
 * @param title          한국어 제목
 * @param seasonNumber   드라마 시즌 번호. 영화는 null
 * @param priority       우선순위
 * @param ottServiceId   이 달에 이 조각을 보는 서비스
 * @param watchMin       이 달에 배정된 시청 시간(분)
 * @param segmentIndex   몇 번째 조각인지(1부터)
 * @param segmentCount   전체 조각 수
 * @param isContinuation 앞 달에서 이어 보는 조각인지
 */
public record PlanAssignmentResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "501") long planItemId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"movie", "tv"}, example = "tv") String mediaType,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "93405") long tmdbId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "오징어 게임") String title,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "1") Integer seasonNumber,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "MUST") WishlistPriority priority,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1") long ottServiceId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "300") int watchMin,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1", example = "1") int segmentIndex,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1", example = "1") int segmentCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "false") boolean isContinuation) {

	/** 서비스 계층 값을 응답으로 바꾼다 */
	public static PlanAssignmentResponse from(PlanDraftView.Assignment assignment) {
		return new PlanAssignmentResponse(assignment.planItemId(), assignment.mediaType().toApiValue(),
				assignment.tmdbId(), assignment.title(), assignment.seasonNumber(),
				WishlistPriority.valueOf(assignment.priority().name()), assignment.ottServiceId(),
				assignment.watchMin(), assignment.segmentIndex(), assignment.segmentCount(),
				assignment.isContinuation());
	}
}
