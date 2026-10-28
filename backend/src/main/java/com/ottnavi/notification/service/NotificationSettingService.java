package com.ottnavi.notification.service;

import com.ottnavi.global.error.UnauthorizedException;
import com.ottnavi.notification.service.model.NotificationSettingView;
import com.ottnavi.user.service.UserQueryService;
import com.ottnavi.user.service.UserSettingService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 알림 설정(수신 여부·수신 이메일) 조회와 저장.
 * 수신 여부는 user_setting 행에, 이메일은 users 행에 있어 user 서비스를 거쳐 읽고 쓴다(다른 도메인 리포지토리는 직접 쓰지 않는다).
 * 호출마다 user 서비스가 각자 트랜잭션을 열고, 이 서비스는 트랜잭션을 만들지 않는다.
 */
@Service
@RequiredArgsConstructor
public class NotificationSettingService {

	private final UserQueryService userQueryService;
	private final UserSettingService userSettingService;

	/** 알림 설정을 조회한다. 온보딩 전이어도 수신 여부 false로 응답한다 */
	public NotificationSettingView getSetting(Long userId) {
		// 1. 수신 여부(설정 행이 없으면 false)와 계정 이메일을 읽는다
		boolean enabled = userQueryService.isNotificationEnabled(userId);
		return new NotificationSettingView(enabled, readEmail(userId));
	}

	/** 알림 수신 여부를 저장하고 저장된 설정을 돌려준다. 온보딩 전이면 409 ONBOARDING_REQUIRED다 */
	public NotificationSettingView updateSetting(Long userId, boolean enabled) {
		// 1. 수신 여부를 저장한다(설정 행이 없으면 여기서 409)
		userSettingService.updateNotificationEnabled(userId, enabled);

		// 2. 저장된 모습으로 응답한다
		return new NotificationSettingView(enabled, readEmail(userId));
	}

	/** 계정 이메일을 읽는다. 사용자가 없으면(탈퇴 직후 등) 인증 실패로 본다 */
	private String readEmail(Long userId) {
		Optional<String> email = userQueryService.findEmail(userId);
		if (email.isEmpty()) {
			throw new UnauthorizedException("사용자를 찾을 수 없습니다.");
		}
		return email.get();
	}
}
