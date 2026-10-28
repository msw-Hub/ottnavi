package com.ottnavi.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ottnavi.global.security.jwt.JwtTokenProvider;
import com.ottnavi.notification.domain.entity.NotificationOutbox;
import com.ottnavi.notification.domain.enums.NotificationType;
import com.ottnavi.notification.domain.repository.NotificationOutboxRepository;
import com.ottnavi.support.IntegrationTestSupport;
import com.ottnavi.support.TestTokens;
import com.ottnavi.user.domain.entity.User;
import com.ottnavi.user.domain.entity.UserSetting;
import com.ottnavi.user.domain.enums.OAuthProvider;
import com.ottnavi.user.domain.enums.UserRole;
import com.ottnavi.user.domain.enums.WatchPreset;
import com.ottnavi.user.domain.repository.UserRepository;
import com.ottnavi.user.domain.repository.UserSettingRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 알림 설정·이력·관리자 요약 API를 실제 보안 체인과 DB로 확인한다(Docker Desktop 필요).
 * 막으려는 사고: 온보딩 전 저장이 500이 되는 일, 타인 알림이 내 이력에 보이는 일,
 * 메일 본문 재료나 실패 사유가 응답에 새는 일, 일반 사용자가 관리자 요약을 보는 일.
 */
@AutoConfigureMockMvc
class NotificationApiTest extends IntegrationTestSupport {

	private static final String SETTING_PATH = "/api/me/notification-setting";
	private static final String HISTORY_PATH = "/api/me/notifications";
	private static final String SUMMARY_PATH = "/api/admin/notifications/summary";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private UserSettingRepository userSettingRepository;

	@Autowired
	private NotificationOutboxRepository notificationOutboxRepository;

	@Test
	void 온보딩_전에는_꺼짐과_계정_이메일로_200이다() throws Exception {
		// given
		User user = createUser();

		// when & then
		mockMvc.perform(get(SETTING_PATH).header(HttpHeaders.AUTHORIZATION, bearerOf(user)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isNotificationEnabled").value(false))
				.andExpect(jsonPath("$.data.email").value(user.getEmail()));
	}

	@Test
	void 온보딩_전_저장은_409_ONBOARDING_REQUIRED다() throws Exception {
		// given
		User user = createUser();

		// when & then
		mockMvc.perform(put(SETTING_PATH).header(HttpHeaders.AUTHORIZATION, bearerOf(user))
						.contentType(MediaType.APPLICATION_JSON).content("{\"isNotificationEnabled\": true}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("ONBOARDING_REQUIRED"));
	}

	@Test
	void 온보딩_뒤에는_켜고_끌_수_있고_조회에_반영된다() throws Exception {
		// given
		User user = createUser();
		userSettingRepository.save(UserSetting.create(user.getId(), 20000, 900, WatchPreset.NORMAL, false));

		// when
		mockMvc.perform(put(SETTING_PATH).header(HttpHeaders.AUTHORIZATION, bearerOf(user))
						.contentType(MediaType.APPLICATION_JSON).content("{\"isNotificationEnabled\": true}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isNotificationEnabled").value(true));

		// then
		mockMvc.perform(get(SETTING_PATH).header(HttpHeaders.AUTHORIZATION, bearerOf(user)))
				.andExpect(jsonPath("$.data.isNotificationEnabled").value(true));
	}

	@Test
	void 이력은_내_알림만_최신순으로_보이고_내부_값은_없다() throws Exception {
		// given
		User me = createUser();
		User other = createUser();
		NotificationOutbox older = outboxOf(me, NotificationType.SUBSCRIBE_GUIDE);
		older.markExhausted("SMTP 오류 상세");
		notificationOutboxRepository.save(older);
		NotificationOutbox newer = outboxOf(me, NotificationType.MONTHLY);
		newer.markSent(Instant.now());
		notificationOutboxRepository.save(newer);
		notificationOutboxRepository.save(outboxOf(other, NotificationType.PLAN_CHANGE));

		// when
		MvcResult result = mockMvc.perform(get(HISTORY_PATH).header(HttpHeaders.AUTHORIZATION, bearerOf(me)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(2))
				.andExpect(jsonPath("$.data.content[0].notificationId").value(newer.getId()))
				.andExpect(jsonPath("$.data.content[0].status").value("SENT"))
				.andExpect(jsonPath("$.data.content[1].status").value("FAILED"))
				.andExpect(jsonPath("$.data.content[1].sentAt").value((Object) null))
				.andReturn();

		// then: 본문 재료·실패 사유는 응답에 없다
		String body = result.getResponse().getContentAsString();
		assertThat(body).doesNotContain("payload").doesNotContain("lastError");
		assertThat(body).doesNotContain("SMTP 오류 상세").doesNotContain("payload-value");
	}

	@Test
	void 관리자_요약은_일반_사용자에게_403이고_관리자에게는_새_알림이_반영된다() throws Exception {
		// given
		TestTokens tokens = new TestTokens(jwtTokenProvider);
		User user = createUser();
		String before = readSummaryBody(tokens.adminBearer());
		long sentBefore = ((Number) JsonPath.read(before, "$.data.sentCount")).longValue();
		long pendingBefore = ((Number) JsonPath.read(before, "$.data.pendingCount")).longValue();
		NotificationOutbox sent = outboxOf(user, NotificationType.MONTHLY);
		sent.markSent(Instant.now());
		notificationOutboxRepository.save(sent);
		notificationOutboxRepository.save(outboxOf(user, NotificationType.PLAN_CHANGE));

		// when & then
		mockMvc.perform(get(SUMMARY_PATH).header(HttpHeaders.AUTHORIZATION, bearerOf(user)))
				.andExpect(status().isForbidden());
		String after = readSummaryBody(tokens.adminBearer());
		assertThat(((Number) JsonPath.read(after, "$.data.windowDays")).intValue()).isEqualTo(7);
		assertThat(((Number) JsonPath.read(after, "$.data.sentCount")).longValue()).isEqualTo(sentBefore + 1);
		assertThat(((Number) JsonPath.read(after, "$.data.pendingCount")).longValue()).isEqualTo(pendingBefore + 1);
		assertThat((Object) JsonPath.read(after, "$.data.latestSentAt")).isNotNull();
	}

	private String readSummaryBody(String adminBearer) throws Exception {
		MvcResult result = mockMvc.perform(get(SUMMARY_PATH).header(HttpHeaders.AUTHORIZATION, adminBearer))
				.andExpect(status().isOk()).andReturn();
		return result.getResponse().getContentAsString();
	}

	/** 대기 상태 알림. 본문 재료에 눈에 띄는 값을 넣어 응답 누출을 확인한다 */
	private NotificationOutbox outboxOf(User user, NotificationType type) {
		return NotificationOutbox.create(user.getId(), type, type.name() + ":" + UUID.randomUUID(),
				"{\"secret\":\"payload-value\"}", Instant.now().plus(Duration.ofDays(1)));
	}

	private String bearerOf(User user) {
		return new TestTokens(jwtTokenProvider).bearer(user.getId(), UserRole.USER);
	}

	private User createUser() {
		String sub = "g" + UUID.randomUUID().toString().replace("-", "");
		return userRepository.save(User.create(sub + "@example.com", "테스터", OAuthProvider.GOOGLE, sub));
	}
}
