package com.ottnavi.home.dto.response;

import com.ottnavi.catalog.dto.response.TitleSearchItemResponse;
import com.ottnavi.catalog.service.model.NewlyAvailableItemView;
import com.ottnavi.catalog.service.model.NewlyAvailableResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 새로 볼 수 있게 된 작품 목록(계약 NewlyAvailableTitleList).
 *
 * @param dataAsOf 제공 상태의 데이터 기준일(KST)
 * @param items    기준 날짜가 최근인 순(없으면 빈 배열)
 */
public record NewlyAvailableTitleListResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-18") LocalDate dataAsOf,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<NewlyAvailableTitleResponse> items) {

	/** 조회 결과를 응답으로 바꾼다 */
	public static NewlyAvailableTitleListResponse from(NewlyAvailableResult result) {
		List<NewlyAvailableTitleResponse> items = new ArrayList<>();
		for (NewlyAvailableItemView item : result.items()) {
			items.add(new NewlyAvailableTitleResponse(TitleSearchItemResponse.from(item.title()), item.ottServiceId(),
					item.arrivalType(), item.availableSince()));
		}
		return new NewlyAvailableTitleListResponse(result.dataAsOf(), items);
	}
}
