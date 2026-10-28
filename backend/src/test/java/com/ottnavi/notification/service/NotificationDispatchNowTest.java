package com.ottnavi.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.ottnavi.notification.domain.enums.NotificationType;
import com.ottnavi.notification.service.model.ClaimedNotification;
import com.ottnavi.notification.service.model.NotificationProductItem;
import com.ottnavi.notification.service.model.NotificationTitleItem;
import com.ottnavi.notification.service.model.SubscribeGuidePayload;
import com.ottnavi.support.IntegrationTestSupport;
import com.ottnavi.user.domain.entity.User;
import com.ottnavi.user.domain.enums.OAuthProvider;
import com.ottnavi.user.domain.enums.WatchPreset;
import com.ottnavi.user.domain.repository.UserRepository;
import com.ottnavi.user.service.UserSettingService;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 가입 안내의 즉시 발송(dispatchNow)을 실제 PostgreSQL과 가짜 SMTP 서버(GreenMail)로 확인한다(Task 073).
 * 막으려는 사고: 즉시 발송이 메일을 보내고 SENT로 기록하지 못하는 일, 스케줄러가 이미 점유한 행을 즉시 발송이 또 보내
 * 같은 메일이 두 통 나가는 일, 새 payload 필드가 템플릿에 나오지 않는 일.
 */
class NotificationDispatchNowTest extends IntegrationTestSupport {

	private static final String EMAIL = "task073-now@example.com";

	@RegisterExtension
	GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

	@Autowired
	private NotificationCreateService createService;
	@Autowired
	private NotificationClaimService claimService;
	@Autowired
	private NotificationDispatcher dispatcher;
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
		User user = userRepository.save(User.create(EMAIL, "즉시발송테스트", OAuthProvider.GOOGLE, "task073-now-sub"));
		userId = user.getId();
		userSettingService.updateSettings(userId, 20_000, 600, WatchPreset.NORMAL, false, new ArrayList<>());
		jdbc.update("UPDATE user_setting SET notification_enabled = TRUE WHERE user_id = ?", userId);
	}

	@AfterEach
	void tearDown() {
		cleanUp();
	}

	private void cleanUp() {
		jdbc.update("DELETE FROM notification_outbox WHERE user_id IN (SELECT id FROM users WHERE email = '" + EMAIL + "')");
		jdbc.update("DELETE FROM user_setting WHERE user_id IN (SELECT id FROM users WHERE email = '" + EMAIL + "')");
		jdbc.update("DELETE FROM users WHERE email = '" + EMAIL + "'");
	}

	private Long createRow(String dedupeKey) {
		NotificationProductItem product = new NotificationProductItem("넷플릭스 스탠다드", List.of("넷플릭스"), 13_500);
		NotificationProductItem cancel = new NotificationProductItem("디즈니+ 스탠다드", List.of("디즈니+"), 9_900);
		NotificationTitleItem title = new NotificationTitleItem("카지노", 2, "넷플릭스");
		SubscribeGuidePayload payload = new SubscribeGuidePayload(1, "즉시발송테스트", "2026-11-03", "2026-11",
				"11월에는 넷플릭스 스탠다드에 가입해 작품 1개를 볼 수 있어요.", List.of(product), List.of(cancel), List.of(title), 13_500);
		return createService.createInNewTransaction(userId, NotificationType.SUBSCRIBE_GUIDE, dedupeKey, payload).get();
	}

	private Map<String, Object> row(Long id) {
		return jdbc.queryForMap("SELECT status, attempt_count, sent_at FROM notification_outbox WHERE id = ?", id);
	}

	@Test
	void 즉시_발송하면_메일_1통이_가고_SENT로_기록되며_가입_안내_재료가_본문에_나온다() throws Exception {
		// given
		Long id = createRow("t073:now-ok");

		// when
		dispatcher.dispatchNow(id);

		// then
		MimeMessage[] received = greenMail.getReceivedMessages();
		assertThat(received).hasSize(1);
		assertThat(received[0].getSubject()).isEqualTo("[OTT내비] 저장한 구독 플랜 안내");
		String body = (String) received[0].getContent();
		assertThat(body).contains("확인된 범위").contains("넷플릭스 스탠다드").contains("13,500").contains("카지노")
				.contains("시즌 2").contains("해지 예약").contains("디즈니+ 스탠다드").contains("2026-11-03");
		Map<String, Object> saved = row(id);
		assertThat(saved.get("status")).isEqualTo("SENT");
		assertThat(saved.get("attempt_count")).isEqualTo(1);
		assertThat(saved.get("sent_at")).isNotNull();
	}

	@Test
	void 스케줄러가_먼저_점유한_행은_즉시_발송이_보내지_않고_스케줄러_쪽에서_한_통만_나간다() {
		// given: 스케줄러가 먼저 이 행을 점유했다(발송 전)
		Long id = createRow("t073:now-race");
		List<ClaimedNotification> claimed = claimService.claimDue(Instant.now());
		assertThat(claimed).hasSize(1);

		// when: 같은 행을 즉시 발송하려 한다
		dispatcher.dispatchNow(id);

		// then: 아무것도 보내지 않고 점유 상태도 그대로다
		assertThat(greenMail.getReceivedMessages()).isEmpty();
		assertThat(row(id).get("attempt_count")).isEqualTo(1);
		assertThat(row(id).get("status")).isEqualTo("PENDING");

		// when: 먼저 점유한 쪽(스케줄러)이 발송을 이어 간다
		dispatcher.dispatchAll(claimed);

		// then: 메일은 모두 1통이다
		assertThat(greenMail.getReceivedMessages()).hasSize(1);
		assertThat(row(id).get("status")).isEqualTo("SENT");
	}

	@Test
	void 이미_발송이_끝난_행을_다시_즉시_발송해도_보내지_않는다() {
		// given
		Long id = createRow("t073:now-twice");
		dispatcher.dispatchNow(id);

		// when
		dispatcher.dispatchNow(id);

		// then
		assertThat(greenMail.getReceivedMessages()).hasSize(1);
	}
}
