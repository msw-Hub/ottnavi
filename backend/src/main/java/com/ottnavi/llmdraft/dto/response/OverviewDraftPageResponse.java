package com.ottnavi.llmdraft.dto.response;

import com.ottnavi.llmdraft.service.model.DraftPageView;
import com.ottnavi.llmdraft.service.model.DraftRecord;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

/**
 * 번역 초안 한 페이지(계약 OverviewDraftPage = PageMeta + 일괄 진행 상황).
 *
 * @param content       이 페이지의 초안들(없으면 빈 배열). Redis에서 사라진 초안은 조용히 빠진다
 * @param page          현재 페이지(0부터)
 * @param size          페이지 크기(표본이면 요청한 표본 수)
 * @param totalElements 조건에 맞는 전체 건수(표본이면 뽑힌 건수)
 * @param totalPages    전체 페이지 수
 * @param bulkRun         최근 일괄 번역의 진행 상황. 일괄을 돌린 적이 없으면 null
 */
public record OverviewDraftPageResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<OverviewDraftResponse> content,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "0") int page,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "20") int size,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "100") long totalElements,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "5") int totalPages,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) OverviewLatestBulkRunResponse bulkRun) {

	/** 서비스 계층 값을 응답으로 바꾼다 */
	public static OverviewDraftPageResponse from(DraftPageView view) {
		List<OverviewDraftResponse> content = new ArrayList<>();
		for (DraftRecord draft : view.content()) {
			content.add(OverviewDraftResponse.from(draft));
		}
		OverviewLatestBulkRunResponse bulkRun = null;
		if (view.bulkRun() != null) {
			bulkRun = OverviewLatestBulkRunResponse.from(BulkDraftRunResponse.from(view.bulkRun()));
		}
		return new OverviewDraftPageResponse(content, view.page(), view.size(), view.totalElements(),
				view.totalPages(), bulkRun);
	}
}
