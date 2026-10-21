package com.ottnavi.notification.domain.enums;

/** 알림 발송 상태(ERD notification_outbox.status, V7 CHECK 값과 같다). "처리 중" 상태는 두지 않고 next_attempt_at 임대로 표현한다(TECH 5절) */
public enum NotificationStatus {
	PENDING,   // 발송 대기(재시도 대기 포함)
	SENT,      // 발송 완료
	FAILED     // 최대 시도 횟수를 넘어 포기
}
