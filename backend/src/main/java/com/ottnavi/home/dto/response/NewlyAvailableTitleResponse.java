package com.ottnavi.home.dto.response;

import com.ottnavi.catalog.domain.enums.ArrivalType;
import com.ottnavi.catalog.dto.response.TitleSearchItemResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * 새로 볼 수 있게 된 작품 한 줄(계약 NewlyAvailableTitle).
 *
 * @param title          작품 카드
 * @param ottServiceId   새로 볼 수 있게 된 서비스
 * @param arrivalType    이유(ARRIVAL 입고 / RELEASE 공개)
 * @param availableSince 기준 날짜(KST). ARRIVAL이면 있음으로 바뀐 것을 확인한 날, RELEASE면 공개일
 */
public record NewlyAvailableTitleResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) TitleSearchItemResponse title,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "4") long ottServiceId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) ArrivalType arrivalType,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-15") LocalDate availableSince) {
}
