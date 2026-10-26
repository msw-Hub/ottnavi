package com.ottnavi.user.domain.enums;

/**
 * 서비스 이용 상태 중 DB에 저장하는 값(ERD user_subscription.status).
 * 미구독(NOT_SUBSCRIBED)은 행을 두지 않으므로 이 enum에 없고, API 응답에서만 {@link ServiceSubscriptionStatus}로 채운다.
 */
public enum SubscriptionStatus {
	SUBSCRIBED, // 구독 중
	FREE,       // 가족 공유 등 무료 이용
	UNKNOWN     // 잘 모르겠음(저장하며, 계산에서는 미구독과 같이 취급)
}
