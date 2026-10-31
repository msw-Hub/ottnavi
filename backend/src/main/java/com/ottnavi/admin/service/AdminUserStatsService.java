package com.ottnavi.admin.service;

import com.ottnavi.admin.service.model.AdminUserStatsView;
import com.ottnavi.plan.service.PlanQueryService;
import com.ottnavi.user.service.UserStatsQueryService;
import com.ottnavi.user.service.model.UserCountStats;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 관리자 회원 현황: user·plan 서비스의 집계를 KST 기준으로 모아 한 묶음으로 만든다. 읽기 전용이라 캐시 없이 매번 센다 */
@Service
@RequiredArgsConstructor
public class AdminUserStatsService {

	private final UserStatsQueryService userStatsQueryService;
	private final PlanQueryService planQueryService;
	private final Clock clock; // KST 기준 시계(ClockConfig). 테스트에서 고정할 수 있다

	/** 회원 현황을 센다 */
	public AdminUserStatsView getUserStats() {
		// 1. 기준 시각: 지금, 오늘 00:00(KST), 이번 달 1일 00:00(KST)
		Instant now = clock.instant();
		LocalDate today = LocalDate.now(clock);
		Instant todayStart = today.atStartOfDay(clock.getZone()).toInstant();
		Instant monthStart = today.withDayOfMonth(1).atStartOfDay(clock.getZone()).toInstant();

		// 2. USER 회원의 가입·알림 집계(user 도메인)
		UserCountStats userCounts = userStatsQueryService.countUsers(todayStart, monthStart);

		// 3. 관리자·데모 계정을 뺀 ACTIVE 플랜 보유자 수(plan 도메인은 users를 조인하지 않고 ID 목록으로 제외한다)
		List<Long> excludedUserIds = userStatsQueryService.findNonMemberUserIds();
		long activePlanUserCount = planQueryService.countActivePlanUsers(excludedUserIds);

		// 4. 합쳐서 돌려준다
		OffsetDateTime measuredAt = now.atZone(clock.getZone()).toOffsetDateTime();
		return new AdminUserStatsView(userCounts.totalUserCount(), userCounts.todayNewUserCount(),
				userCounts.monthNewUserCount(), activePlanUserCount, userCounts.notificationEnabledUserCount(),
				measuredAt);
	}
}
