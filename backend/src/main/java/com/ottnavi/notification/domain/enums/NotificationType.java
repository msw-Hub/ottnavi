package com.ottnavi.notification.domain.enums;

/** 알림 유형(ERD notification_outbox.type, V7 CHECK 값과 같다) */
public enum NotificationType {
	SUBSCRIBE_GUIDE,   // 플랜 저장 직후 보내는 구독 안내
	MONTHLY,           // 월초 재계산 결과 안내
	PLAN_CHANGE        // 이번 달 확정 플랜의 작품이 빠지는 등 변경 알림
}
