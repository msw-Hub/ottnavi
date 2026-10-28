package com.ottnavi.notification.service.model;

/**
 * 알림 설정 조회 결과.
 *
 * @param notificationEnabled 알림 수신 여부(온보딩 전이면 false)
 * @param email               수신 이메일(로그인한 계정의 이메일)
 */
public record NotificationSettingView(boolean notificationEnabled, String email) {
}
