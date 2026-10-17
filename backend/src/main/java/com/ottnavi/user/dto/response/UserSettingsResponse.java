package com.ottnavi.user.dto.response;

import com.ottnavi.user.domain.enums.WatchPreset;
import com.ottnavi.user.service.model.SubscriptionState;
import com.ottnavi.user.service.model.UserSettingsState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

/**
 * 내 설정 응답(계약 UserSettings). 온보딩 전이면 subscriptions는 빈 배열, 나머지 값은 null이다.
 *
 * @param isOnboardingDone    온보딩(첫 설정 저장)을 마쳤는지
 * @param subscriptions       서비스별 이용 상태
 * @param monthlyBudget       월 예산(원). 입력 전이면 null
 * @param monthlyWatchMinutes 월 시청 시간(분). 입력 전이면 null
 * @param watchPreset         시청 시간을 고른 방식. 입력 전이면 null
 * @param isLowestPriceMode   "가장 저렴한 요금제로 계산할래요" 체크 여부. 입력 전이면 false
 */
public record UserSettingsResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "true") boolean isOnboardingDone,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<UserSubscriptionResponse> subscriptions,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "20000") Integer monthlyBudget,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "900") Integer monthlyWatchMinutes,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "NORMAL") WatchPreset watchPreset,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "false") boolean isLowestPriceMode) {

	/** 서비스 계층 값을 응답으로 바꾼다 */
	public static UserSettingsResponse from(UserSettingsState state) {
		List<UserSubscriptionResponse> subscriptions = new ArrayList<>();
		for (SubscriptionState subscription : state.subscriptions()) {
			subscriptions.add(UserSubscriptionResponse.from(subscription));
		}
		return new UserSettingsResponse(state.isOnboardingDone(), subscriptions,
				state.monthlyBudget(), state.monthlyWatchMinutes(), state.watchPreset(), state.isLowestPriceMode());
	}
}
