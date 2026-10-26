package com.ottnavi.catalog.dto.response;

import com.ottnavi.catalog.service.model.ExclusiveTitleItem;
import com.ottnavi.catalog.service.model.ExclusiveTitlePageView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 서비스별 독점 목록 한 페이지(계약 ExclusiveTitlePage = PageMeta + 서비스 ID·판정 여부·데이터 기준일).
 *
 * @param content       이 페이지의 작품들(인기도 높은 순, 없으면 빈 배열)
 * @param page          현재 페이지(0부터)
 * @param size          페이지 크기
 * @param totalElements 이 서비스 독점으로 확인된 작품 전체 수
 * @param totalPages    전체 페이지 수
 * @param ottServiceId  서비스 ID
 * @param isJudged      독점 목록을 판정했는지. 현재는 모든 서비스가 true다.
 *                      데이터 충분도가 낮은 서비스(INSUFFICIENT)는 일부 구간(쿠팡플레이 영화)을 판정에서 빼고, 그 서비스의 data_quality로 화면이 안내한다
 * @param dataAsOf      제공 상태의 데이터 기준일(KST)
 */
public record ExclusiveTitlePageResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ExclusiveTitleResponse> content,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "0") int page,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "20") int size,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "135") long totalElements,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "7") int totalPages,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1") long ottServiceId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "true") boolean isJudged,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-18") LocalDate dataAsOf) {

	/** 조회 결과를 응답으로 바꾼다 */
	public static ExclusiveTitlePageResponse from(ExclusiveTitlePageView view) {
		List<ExclusiveTitleResponse> content = new ArrayList<>();
		for (ExclusiveTitleItem item : view.content()) {
			content.add(ExclusiveTitleResponse.from(item));
		}
		return new ExclusiveTitlePageResponse(content, view.page(), view.size(), view.totalElements(),
				view.totalPages(), view.ottServiceId(), view.judged(), view.dataAsOf());
	}
}
