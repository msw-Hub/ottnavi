package com.ottnavi.user.service;

import com.ottnavi.user.domain.enums.UserRole;
import com.ottnavi.user.domain.repository.UserRepository;
import com.ottnavi.user.domain.repository.UserSettingRepository;
import com.ottnavi.user.service.model.UserCountStats;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 모니터링용 회원 집계. 집계 대상은 USER 권한 회원만이다(관리자·데모 계정 제외) */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserStatsQueryService {

	private final UserRepository userRepository;
	private final UserSettingRepository userSettingRepository;

	/**
	 * 전체·신규·알림 켠 회원 수를 센다.
	 *
	 * @param todayStart 오늘 00:00(KST)에 해당하는 시각
	 * @param monthStart 이번 달 1일 00:00(KST)에 해당하는 시각
	 */
	public UserCountStats countUsers(Instant todayStart, Instant monthStart) {
		// 1. 전체와 신규 회원 수(기준 시각 이후, 기준 시각 포함)
		long total = userRepository.countByRole(UserRole.USER);
		long today = userRepository.countByRoleAndCreatedAtGreaterThanEqual(UserRole.USER, todayStart);
		long month = userRepository.countByRoleAndCreatedAtGreaterThanEqual(UserRole.USER, monthStart);

		// 2. 알림을 켠 회원 수(설정 행이 없으면 꺼짐으로 보고 세지 않는다)
		long notificationEnabled = userSettingRepository.countNotificationEnabledByRole(UserRole.USER);

		return new UserCountStats(total, today, month, notificationEnabled);
	}

	/** USER가 아닌 사용자(관리자·데모)의 ID 목록. 다른 도메인이 자기 집계에서 이들을 빼는 용도다 */
	public List<Long> findNonMemberUserIds() {
		return userRepository.findIdsByRoleNot(UserRole.USER);
	}
}
