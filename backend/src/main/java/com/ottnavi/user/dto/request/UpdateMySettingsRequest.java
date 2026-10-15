package com.ottnavi.user.dto.request;

import com.ottnavi.user.domain.enums.WatchPreset;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 내 설정 저장 요청(계약 UpdateMySettingsRequest). 전체 교체이며 "주 N회 × 회당 M시간" 환산 입력값은 받지 않는다(R11-18 A안).
 *
 * @param subscriptions       서비스별 이용 상태. 1~7개, 서비스마다 한 번만. 빠진 서비스는 미구독
 * @param monthlyBudget       월 예산(원). 0 이상
 * @param monthlyWatchMinutes 월 시청 시간(분). 120~44640
 * @param watchPreset         시청 시간을 고른 방식
 */
public record UpdateMySettingsRequest(
		@NotNull(message = "서비스별 이용 상태는 필수입니다.")
		@Size(min = 1, max = 7, message = "서비스별 이용 상태는 1~7개여야 합니다.")
		@Valid
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
		List<@NotNull(message = "이용 상태 항목은 비울 수 없습니다.") UserSubscriptionRequest> subscriptions,
		@NotNull(message = "월 예산은 필수입니다.")
		@Min(value = 0, message = "월 예산은 0 이상이어야 합니다.")
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "20000") Integer monthlyBudget,
		@NotNull(message = "월 시청 시간은 필수입니다.")
		@Min(value = 120, message = "월 시청 시간은 120분 이상이어야 합니다.")
		@Max(value = 44640, message = "월 시청 시간은 44640분 이하여야 합니다.")
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "900") Integer monthlyWatchMinutes,
		@NotNull(message = "시청 시간 방식은 필수입니다.")
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "NORMAL") WatchPreset watchPreset) {
}
