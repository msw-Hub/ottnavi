package com.ottnavi.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.notification.domain.enums.NotificationType;
import com.ottnavi.notification.service.model.NotificationProductItem;
import com.ottnavi.notification.service.model.NotificationTitleItem;
import com.ottnavi.notification.service.model.SubscribeGuidePayload;
import com.ottnavi.support.IntegrationTestSupport;
import com.ottnavi.user.domain.entity.User;
import com.ottnavi.user.domain.enums.OAuthProvider;
import com.ottnavi.user.domain.enums.WatchPreset;
import com.ottnavi.user.domain.repository.UserRepository;
import com.ottnavi.user.service.UserSettingService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 알림 생성 관문(Task 073)을 실제 PostgreSQL로 확인한다.
 * 막으려는 사고: 같은 키로 알림이 두 번 만들어지는 일, 두 번째 시도가 예외를 던져 호출한 트랜잭션을 망가뜨리는 일,
 * 알림을 끈(또는 설정이 없는) 사용자에게 행이 만들어지는 일, payload가 JSON으로 저장되지 않는 일.
 */
class NotificationCreateServiceTest extends IntegrationTestSupport {

	private static final String EMAIL_PREFIX = "task073-create-";

	@Autowired
	private NotificationCreateService createService;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private UserSettingService userSettingService;
	@Autowired
	private JdbcTemplate jdbc;

	private Long userId;

	@BeforeEach
	void setUp() {
		cleanUp();
		userId = saveUser("a", true);
	}

	@AfterEach
	void tearDown() {
		cleanUp();
	}

	private void cleanUp() {
		jdbc.update("DELETE FROM notification_outbox WHERE user_id IN (SELECT id FROM users WHERE email LIKE '"
				+ EMAIL_PREFIX + "%')");
		jdbc.update("DELETE FROM user_setting WHERE user_id IN (SELECT id FROM users WHERE email LIKE '"
				+ EMAIL_PREFIX + "%')");
		jdbc.update("DELETE FROM users WHERE email LIKE '" + EMAIL_PREFIX + "%'");
	}

	/** 사용자를 만든다. withSetting이 false면 설정 행이 없는(온보딩 전) 사용자다 */
	private Long saveUser(String suffix, boolean withSetting) {
		String subject = "task073-create-sub-" + suffix;
		User user = userRepository.save(User.create(EMAIL_PREFIX + suffix + "@example.com", "생성테스트",
				OAuthProvider.GOOGLE, subject));
		if (withSetting) {
			userSettingService.updateSettings(user.getId(), 20_000, 600, WatchPreset.NORMAL, false, new ArrayList<>());
		}
		return user.getId();
	}

	private void setNotification(Long ownerId, boolean enabled) {
		jdbc.update("UPDATE user_setting SET notification_enabled = ? WHERE user_id = ?", enabled, ownerId);
	}

	private SubscribeGuidePayload payload() {
		NotificationProductItem product = new NotificationProductItem("넷플릭스 스탠다드", List.of("넷플릭스"), 13_500);
		NotificationTitleItem title = new NotificationTitleItem("카지노", null, "넷플릭스");
		return new SubscribeGuidePayload(1, "생성테스트", "2026-11-03", "2026-11", "11월에는 넷플릭스 스탠다드에 가입해 작품 1개를 볼 수 있어요.",
				List.of(product), List.of(), List.of(title), 13_500);
	}

	private int countRows(String dedupeKey) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE dedupe_key = ?", Integer.class,
				dedupeKey);
	}

	@Test
	void 알림을_켠_사용자에게는_PENDING_행을_만들고_payload는_JSON으로_저장한다() {
		// given
		setNotification(userId, true);

		// when
		Optional<Long> id = createService.create(userId, NotificationType.SUBSCRIBE_GUIDE, "t073:ok", payload());

		// then
		assertThat(id).isPresent();
		Map<String, Object> row = jdbc.queryForMap("SELECT type, status, attempt_count, payload ->> 'userName' AS name, "
				+ "payload -> 'joinProducts' -> 0 ->> 'productName' AS product, "
				+ "next_attempt_at <= now() AS due FROM notification_outbox WHERE id = ?", id.get());
		assertThat(row.get("type")).isEqualTo("SUBSCRIBE_GUIDE");
		assertThat(row.get("status")).isEqualTo("PENDING");
		assertThat(row.get("attempt_count")).isEqualTo(0);
		assertThat(row.get("name")).isEqualTo("생성테스트");
		assertThat(row.get("product")).isEqualTo("넷플릭스 스탠다드");
		assertThat(row.get("due")).isEqualTo(true);
	}

	@Test
	void 같은_키로_두_번_만들면_행은_1개이고_두_번째는_예외_없이_빈_값이다() {
		// given
		setNotification(userId, true);
		Optional<Long> first = createService.create(userId, NotificationType.MONTHLY, "t073:dup", payload());

		// when
		Optional<Long> second = createService.create(userId, NotificationType.MONTHLY, "t073:dup", payload());

		// then
		assertThat(first).isPresent();
		assertThat(second).isEmpty();
		assertThat(countRows("t073:dup")).isEqualTo(1);
	}

	@Test
	void 새_트랜잭션_경로도_같은_키는_한_번만_만든다() {
		// given
		setNotification(userId, true);

		// when
		Optional<Long> first = createService.createInNewTransaction(userId, NotificationType.SUBSCRIBE_GUIDE, "t073:new",
				payload());
		Optional<Long> second = createService.createInNewTransaction(userId, NotificationType.SUBSCRIBE_GUIDE,
				"t073:new", payload());

		// then
		assertThat(first).isPresent();
		assertThat(second).isEmpty();
		assertThat(countRows("t073:new")).isEqualTo(1);
	}

	@Test
	void 알림을_끈_사용자에게는_세_유형_모두_행을_만들지_않는다() {
		// given
		setNotification(userId, false);

		// when
		Optional<Long> guide = createService.create(userId, NotificationType.SUBSCRIBE_GUIDE, "t073:off-1", payload());
		Optional<Long> monthly = createService.create(userId, NotificationType.MONTHLY, "t073:off-2", payload());
		Optional<Long> change = createService.create(userId, NotificationType.PLAN_CHANGE, "t073:off-3", payload());

		// then
		assertThat(guide).isEmpty();
		assertThat(monthly).isEmpty();
		assertThat(change).isEmpty();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE user_id = ?", Integer.class,
				userId)).isZero();
	}

	@Test
	void 데모_계정은_알림을_켜_두어도_행을_만들지_않는다() {
		// given: 데모 계정에 설정 행을 만들고 DB에서 직접 알림을 켠다(API는 403으로 막지만 값이 켜져 있어도 막히는지 본다)
		User demo = userRepository.save(User.createDemo(EMAIL_PREFIX + "demo@example.com", "데모", "task073-create-demo"));
		userSettingService.updateSettings(demo.getId(), 20_000, 600, WatchPreset.NORMAL, false, new ArrayList<>());
		setNotification(demo.getId(), true);

		// when
		Optional<Long> id = createService.create(demo.getId(), NotificationType.MONTHLY, "t109:demo", payload());

		// then
		assertThat(id).isEmpty();
		assertThat(countRows("t109:demo")).isZero();
	}

	@Test
	void 설정_행이_없는_사용자에게는_행을_만들지_않는다() {
		// given
		Long noSettingUser = saveUser("b", false);

		// when
		Optional<Long> id = createService.create(noSettingUser, NotificationType.MONTHLY, "t073:nosetting", payload());

		// then
		assertThat(id).isEmpty();
		assertThat(countRows("t073:nosetting")).isZero();
	}
}
