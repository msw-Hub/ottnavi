package com.ottnavi.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.admin.service.model.AdminUserStatsView;
import com.ottnavi.engine.model.PlanType;
import com.ottnavi.engine.model.SolverType;
import com.ottnavi.plan.domain.entity.Plan;
import com.ottnavi.plan.domain.repository.PlanRepository;
import com.ottnavi.plan.service.PlanQueryService;
import com.ottnavi.support.IntegrationTestSupport;
import com.ottnavi.user.domain.entity.User;
import com.ottnavi.user.domain.entity.UserSetting;
import com.ottnavi.user.domain.enums.OAuthProvider;
import com.ottnavi.user.domain.enums.WatchPreset;
import com.ottnavi.user.domain.repository.UserRepository;
import com.ottnavi.user.domain.repository.UserSettingRepository;
import com.ottnavi.user.service.UserStatsQueryService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 관리자 회원 현황 집계를 실제 DB(Testcontainers)로 확인한다(Docker Desktop 필요).
 * 막으려는 사고: 관리자·데모 계정이 회원 수에 섞이는 일, KST 날짜 경계가 어긋나는 일,
 * 탈퇴한 회원이나 DRAFT·ARCHIVED 플랜이 활성 수에 들어가는 일, 알림 설정 행이 없는 회원이 켠 것으로 세지는 일.
 * 다른 테스트가 남긴 회원이 숫자를 바꾸지 않도록 매 테스트 전에 users를 비운다(하위 행은 FK CASCADE).
 */
class AdminUserStatsServiceTest extends IntegrationTestSupport {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	// 경계 확인이 아닌 테스트에서 회원을 "오늘 이전"에 가입한 것으로 두는 기본 가입 시각(고정 Clock 2026-11-01보다 과거)
	private static final String DEFAULT_CREATED_AT = "2026-10-01T09:00:00+09:00";

	@Autowired
	private UserStatsQueryService userStatsQueryService;

	@Autowired
	private PlanQueryService planQueryService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private UserSettingRepository userSettingRepository;

	@Autowired
	private PlanRepository planRepository;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void 회원을_비운다() {
		jdbc.update("delete from users");
	}

	@Test
	void USER_3명과_ADMIN_1명_DEMO_1명이면_전체_회원은_3명이다() {
		// given
		createUser();
		createUser();
		createUser();
		createAdmin();
		createDemo();

		// when
		AdminUserStatsView stats = statsAt("2026-11-01T00:30:00+09:00");

		// then
		assertThat(stats.totalUserCount()).isEqualTo(3);
	}

	@Test
	void 오늘_00시_직전_가입자는_오늘과_이번_달에서_빠지고_00시_정각_가입자는_들어간다() {
		// given
		User beforeMidnight = createUser();
		setCreatedAt(beforeMidnight, "2026-10-31T23:59:59+09:00");
		User atMidnight = createUser();
		setCreatedAt(atMidnight, "2026-11-01T00:00:00+09:00");

		// when
		AdminUserStatsView stats = statsAt("2026-11-01T00:30:00+09:00");

		// then
		assertThat(stats.totalUserCount()).isEqualTo(2);
		assertThat(stats.todayNewUserCount()).isEqualTo(1);
		assertThat(stats.monthNewUserCount()).isEqualTo(1);
		assertThat(stats.measuredAt()).isEqualTo(OffsetDateTime.parse("2026-11-01T00:30:00+09:00"));
	}

	@Test
	void 이번_달_초에_가입한_회원은_이번_달에는_들어가지만_오늘에는_빠진다() {
		// given
		User monthStart = createUser();
		setCreatedAt(monthStart, "2026-11-01T00:00:00+09:00");
		User today = createUser();
		setCreatedAt(today, "2026-11-15T08:00:00+09:00");

		// when
		AdminUserStatsView stats = statsAt("2026-11-15T09:00:00+09:00");

		// then
		assertThat(stats.todayNewUserCount()).isEqualTo(1);
		assertThat(stats.monthNewUserCount()).isEqualTo(2);
	}

	@Test
	void 탈퇴해서_삭제된_회원은_모든_숫자에서_빠진다() {
		// given
		User stay = createUser();
		User withdrawn = createUser();
		userSettingRepository.save(notificationOn(withdrawn));
		userRepository.deleteById(withdrawn.getId());

		// when
		AdminUserStatsView stats = statsAt("2026-11-01T00:30:00+09:00");

		// then
		assertThat(userRepository.existsById(stay.getId())).isTrue();
		assertThat(stats.totalUserCount()).isEqualTo(1);
		assertThat(stats.todayNewUserCount()).isZero();
		assertThat(stats.notificationEnabledUserCount()).isZero();
	}

	@Test
	void 활성_플랜_보유자는_ACTIVE만_세고_DRAFT_ARCHIVED_DEMO의_ACTIVE는_뺀다() {
		// given: ACTIVE 보유자 1명, DRAFT만 가진 회원, ARCHIVED만 가진 회원, ACTIVE를 가진 DEMO
		User active = createUser();
		savePlan(active, "ACTIVE");
		User draftOnly = createUser();
		savePlan(draftOnly, "DRAFT");
		User archivedOnly = createUser();
		savePlan(archivedOnly, "ARCHIVED");
		User demo = createDemo();
		savePlan(demo, "ACTIVE");

		// when
		AdminUserStatsView stats = statsAt("2026-11-01T00:30:00+09:00");

		// then
		assertThat(stats.activePlanUserCount()).isEqualTo(1);
	}

	@Test
	void 제외_ID_목록이_비어도_ACTIVE_플랜_보유자를_센다() {
		// given
		User active = createUser();
		savePlan(active, "ACTIVE");

		// when
		long count = planQueryService.countActivePlanUsers(new ArrayList<Long>());

		// then
		assertThat(count).isEqualTo(1);
	}

	@Test
	void 알림을_켠_회원만_세고_꺼짐_설정_없음_DEMO의_켜짐은_뺀다() {
		// given
		User on = createUser();
		userSettingRepository.save(notificationOn(on));
		User off = createUser();
		userSettingRepository.save(UserSetting.create(off.getId(), 20000, 900, WatchPreset.NORMAL, false));
		createUser();
		User demo = createDemo();
		userSettingRepository.save(notificationOn(demo));

		// when
		AdminUserStatsView stats = statsAt("2026-11-01T00:30:00+09:00");

		// then
		assertThat(stats.notificationEnabledUserCount()).isEqualTo(1);
	}

	/** 고정 시계(KST)로 서비스를 만들어 집계한다 */
	private AdminUserStatsView statsAt(String kstTime) {
		Clock clock = Clock.fixed(OffsetDateTime.parse(kstTime).toInstant(), KST);
		AdminUserStatsService service = new AdminUserStatsService(userStatsQueryService, planQueryService, clock);
		return service.getUserStats();
	}

	/**
	 * 회원을 만든다. 가입 시각(created_at)은 실제 현재 시각이 아니라 고정된 과거 값으로 맞춘다.
	 * 실제 시각을 그대로 두면 테스트를 도는 날짜가 고정 Clock의 날짜(2026-11-01)와 같아지는 순간부터
	 * "오늘 신규" 숫자가 달라져 테스트가 깨진다(CI에서 실제로 11월 1일 00시 이후에 실패했다).
	 * 경계를 확인하는 테스트는 setCreatedAt으로 다시 덮어쓴다.
	 */
	private User createUser() {
		String sub = "g" + UUID.randomUUID().toString().replace("-", "");
		User user = userRepository.save(User.create(sub + "@example.com", "테스터", OAuthProvider.GOOGLE, sub));
		setCreatedAt(user, DEFAULT_CREATED_AT);
		return user;
	}

	/** 관리자는 가입 경로로 만들 수 없어 DB에서 권한을 바꾼다 */
	private User createAdmin() {
		User user = createUser();
		jdbc.update("update users set role = 'ADMIN' where id = ?", user.getId());
		return user;
	}

	private User createDemo() {
		String sub = "d" + UUID.randomUUID().toString().replace("-", "");
		User user = userRepository.save(User.createDemo(sub + "@example.com", "데모", sub));
		setCreatedAt(user, DEFAULT_CREATED_AT);
		return user;
	}

	private UserSetting notificationOn(User user) {
		UserSetting setting = UserSetting.create(user.getId(), 20000, 900, WatchPreset.NORMAL, false);
		setting.changeNotificationEnabled(true);
		return setting;
	}

	/** 가입 시각을 KST 기준 값으로 바꾼다(경계 확인용) */
	private void setCreatedAt(User user, String kstTime) {
		Instant instant = OffsetDateTime.parse(kstTime).toInstant();
		jdbc.update("update users set created_at = ? where id = ?", java.sql.Timestamp.from(instant), user.getId());
	}

	/** 지정한 상태의 플랜을 하나 만든다(ARCHIVED는 ACTIVE를 거쳐 보관한다) */
	private void savePlan(User user, String status) {
		Plan plan = Plan.createDraft(user.getId(), null, PlanType.RECOMMENDED, LocalDate.of(2026, 11, 1), 30_000, 600,
				SolverType.EXACT, 1, 1, 1, 1_000, 2_000, Instant.now());
		plan = planRepository.saveAndFlush(plan);
		if (status.equals("DRAFT")) {
			return;
		}
		plan.activate(Instant.now(), null);
		plan = planRepository.saveAndFlush(plan);
		if (status.equals("ARCHIVED")) {
			plan.archive();
			planRepository.saveAndFlush(plan);
		}
	}
}
