package com.ottnavi.user.service.model;

import com.ottnavi.user.domain.enums.WatchPreset;
import java.util.List;

/**
 * 사용자 설정 조회 결과. 온보딩 전이면 subscriptions는 빈 목록이고 나머지는 null이다.
 *
 * @param isOnboardingDone    첫 설정 저장을 마쳤는지(user_setting 행 존재)
 * @param subscriptions       서비스별 이용 상태(온보딩 뒤에는 서비스 전부, ID 오름차순)
 * @param monthlyBudget       월 예산(원)
 * @param monthlyWatchMinutes 월 시청 시간(분)
 * @param watchPreset         시청 시간을 고른 방식
 */
public record UserSettingsState(
		boolean isOnboardingDone,
		List<SubscriptionState> subscriptions,
		Integer monthlyBudget,
		Integer monthlyWatchMinutes,
		WatchPreset watchPreset) {
}
