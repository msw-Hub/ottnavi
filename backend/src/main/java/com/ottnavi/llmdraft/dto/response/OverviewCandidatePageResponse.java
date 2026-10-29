package com.ottnavi.llmdraft.dto.response;

import com.ottnavi.llmdraft.service.model.CandidateItemView;
import com.ottnavi.llmdraft.service.model.CandidatePageView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

/**
 * 번역 후보 한 페이지(계약 OverviewCandidatePage = PageMeta + 요약).
 *
 * @param content       이 페이지의 후보들(없으면 빈 배열)
 * @param page          현재 페이지(0부터)
 * @param size          페이지 크기
 * @param totalElements 전체 건수(번역 대기 중인 영어 줄거리 작품 수)
 * @param totalPages    전체 페이지 수
 * @param summary       화면 상단 요약(사용량·상한·모델·키 유무). 키가 없어도 조회는 동작한다
 */
public record OverviewCandidatePageResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<OverviewCandidateResponse> content,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "0") int page,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "20") int size,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2512") long totalElements,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "126") int totalPages,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) LlmSummaryResponse summary) {

	/** 서비스 계층 값을 응답으로 바꾼다 */
	public static OverviewCandidatePageResponse from(CandidatePageView view) {
		List<OverviewCandidateResponse> content = new ArrayList<>();
		for (CandidateItemView item : view.content()) {
			content.add(OverviewCandidateResponse.from(item));
		}
		return new OverviewCandidatePageResponse(content, view.page(), view.size(), view.totalElements(),
				view.totalPages(), LlmSummaryResponse.from(view));
	}
}
