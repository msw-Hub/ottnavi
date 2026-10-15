package com.ottnavi.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ottnavi.global.security.jwt.JwtTokenProvider;
import com.ottnavi.provider.domain.entity.OttService;
import com.ottnavi.provider.service.OttServiceQueryService;
import com.ottnavi.support.IntegrationTestSupport;
import com.ottnavi.support.TestTokens;
import com.ottnavi.user.domain.entity.User;
import com.ottnavi.user.domain.enums.OAuthProvider;
import com.ottnavi.user.domain.enums.ServiceSubscriptionStatus;
import com.ottnavi.user.domain.enums.UserRole;
import com.ottnavi.user.domain.enums.WatchPreset;
import com.ottnavi.user.domain.repository.UserRepository;
import com.ottnavi.user.domain.repository.UserSubscriptionRepository;
import com.ottnavi.user.service.UserSettingService;
import com.ottnavi.user.service.model.SubscriptionState;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 내 설정 API를 실제 보안 체인과 DB로 확인한다(Docker Desktop 필요).
 * 막으려는 사고: 저장한 값이 조회와 다르게 보이는 일, 다른 사용자의 설정이 보이는 일,
 * 같은 사용자의 동시 저장이 유니크 위반 500으로 깨지는 일, 잘못된 입력이 저장되는 일.
 */
@AutoConfigureMockMvc
class UserSettingApiTest extends IntegrationTestSupport {

	private static final String PATH = "/api/me/settings";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private UserSubscriptionRepository userSubscriptionRepository;

	@Autowired
	private UserSettingService userSettingService;

	@Autowired
	private OttServiceQueryService ottServiceQueryService;

	@Test
	void 비로그인은_401이다() throws Exception {
		mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
		mockMvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content(validBody(1L)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 저장_전에는_온보딩_전_모습이다() throws Exception {
		// given
		String bearer = bearerOfNewUser();

		// when & then
		mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isOnboardingDone").value(false))
				.andExpect(jsonPath("$.data.subscriptions").isEmpty())
				.andExpect(jsonPath("$.data.monthlyBudget").doesNotExist())
				.andExpect(jsonPath("$.data.monthlyWatchMinutes").doesNotExist())
				.andExpect(jsonPath("$.data.watchPreset").doesNotExist());
	}

	@Test
	void 저장한_값이_그대로_조회되고_빠진_서비스는_NOT_SUBSCRIBED로_채운다() throws Exception {
		// given
		String bearer = bearerOfNewUser();
		long firstId = serviceIds().get(0);

		// when
		mockMvc.perform(put(PATH).header(HttpHeaders.AUTHORIZATION, bearer)
						.contentType(MediaType.APPLICATION_JSON).content(validBody(firstId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isOnboardingDone").value(true));

		// then
		mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.isOnboardingDone").value(true))
				.andExpect(jsonPath("$.data.monthlyBudget").value(20000))
				.andExpect(jsonPath("$.data.monthlyWatchMinutes").value(900))
				.andExpect(jsonPath("$.data.watchPreset").value("NORMAL"))
				.andExpect(jsonPath("$.data.subscriptions.length()").value(7))
				.andExpect(jsonPath("$.data.subscriptions[0].ottServiceId").value(firstId))
				.andExpect(jsonPath("$.data.subscriptions[0].status").value("SUBSCRIBED"))
				.andExpect(jsonPath("$.data.subscriptions[0].planName").value("스탠다드"))
				.andExpect(jsonPath("$.data.subscriptions[0].billingDay").value(1))
				.andExpect(jsonPath("$.data.subscriptions[1].status").value("NOT_SUBSCRIBED"))
				.andExpect(jsonPath("$.data.subscriptions[1].planName").doesNotExist());
	}

	@Test
	void 다시_저장하면_이전_구독이_교체된다() throws Exception {
		// given
		String bearer = bearerOfNewUser();
		List<Long> ids = serviceIds();
		mockMvc.perform(put(PATH).header(HttpHeaders.AUTHORIZATION, bearer)
						.contentType(MediaType.APPLICATION_JSON).content(validBody(ids.get(0))))
				.andExpect(status().isOk());

		// when: 이번에는 다른 서비스만 FREE로 보낸다
		String body = """
				{"subscriptions":[{"ottServiceId":%d,"status":"FREE","planName":null,"billingDay":null}],
				 "monthlyBudget":0,"monthlyWatchMinutes":120,"watchPreset":"CUSTOM"}
				""".formatted(ids.get(1));
		mockMvc.perform(put(PATH).header(HttpHeaders.AUTHORIZATION, bearer)
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk());

		// then
		mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(jsonPath("$.data.monthlyBudget").value(0))
				.andExpect(jsonPath("$.data.watchPreset").value("CUSTOM"))
				.andExpect(jsonPath("$.data.subscriptions[0].status").value("NOT_SUBSCRIBED"))
				.andExpect(jsonPath("$.data.subscriptions[1].status").value("FREE"));
	}

	@Test
	void 한_사용자의_저장은_다른_사용자에게_보이지_않는다() throws Exception {
		// given
		String bearerA = bearerOfNewUser();
		String bearerB = bearerOfNewUser();

		// when
		mockMvc.perform(put(PATH).header(HttpHeaders.AUTHORIZATION, bearerA)
						.contentType(MediaType.APPLICATION_JSON).content(validBody(serviceIds().get(0))))
				.andExpect(status().isOk());

		// then
		mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearerB))
				.andExpect(jsonPath("$.data.isOnboardingDone").value(false));
	}

	@Test
	void 필수값이_빠지면_400이다() throws Exception {
		mockMvc.perform(put(PATH).header(HttpHeaders.AUTHORIZATION, bearerOfNewUser())
						.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
	}

	@Test
	void 월_시청_시간이_120_미만이면_400이다() throws Exception {
		// given
		String body = validBody(serviceIds().get(0)).replace("\"monthlyWatchMinutes\": 900", "\"monthlyWatchMinutes\": 119");

		// when & then
		mockMvc.perform(put(PATH).header(HttpHeaders.AUTHORIZATION, bearerOfNewUser())
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("monthlyWatchMinutes"));
	}

	@Test
	void 같은_서비스를_중복해서_보내면_400이고_저장되지_않는다() throws Exception {
		// given
		String bearer = bearerOfNewUser();
		long id = serviceIds().get(0);
		String body = """
				{"subscriptions":[
				  {"ottServiceId":%d,"status":"FREE","planName":null,"billingDay":null},
				  {"ottServiceId":%d,"status":"UNKNOWN","planName":null,"billingDay":null}],
				 "monthlyBudget":0,"monthlyWatchMinutes":120,"watchPreset":"LIGHT"}
				""".formatted(id, id);

		// when & then
		mockMvc.perform(put(PATH).header(HttpHeaders.AUTHORIZATION, bearer)
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
		mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(jsonPath("$.data.isOnboardingDone").value(false));
	}

	@Test
	void 구독_중인데_요금제_이름이_없으면_400이다() throws Exception {
		// given
		String body = """
				{"subscriptions":[{"ottServiceId":%d,"status":"SUBSCRIBED","planName":null,"billingDay":15}],
				 "monthlyBudget":0,"monthlyWatchMinutes":120,"watchPreset":"LIGHT"}
				""".formatted(serviceIds().get(0));

		// when & then
		mockMvc.perform(put(PATH).header(HttpHeaders.AUTHORIZATION, bearerOfNewUser())
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("subscriptions[0].planName"));
	}

	@Test
	void 같은_사용자의_동시_저장도_유니크_위반_없이_하나의_결과로_끝난다() throws Exception {
		// given: 같은 사용자가 서비스 3개를 담은 저장을 동시에 보낸다(여러 번 반복해 경합을 만든다)
		User user = createUser();
		List<Long> ids = serviceIds();
		List<SubscriptionState> states = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			states.add(new SubscriptionState(ids.get(i), ServiceSubscriptionStatus.FREE, null, null));
		}
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			for (int round = 0; round < 5; round++) {
				CountDownLatch start = new CountDownLatch(1);
				List<Future<?>> futures = new ArrayList<>();
				for (int t = 0; t < 2; t++) {
					futures.add(pool.submit(() -> {
						start.await();
						return userSettingService.updateSettings(user.getId(), 10000, 600, WatchPreset.NORMAL, states);
					}));
				}

				// when
				start.countDown();

				// then: 두 요청 모두 예외 없이 끝난다
				for (Future<?> future : futures) {
					future.get();
				}
				assertThat(userSubscriptionRepository.findAllByUserId(user.getId())).hasSize(3);
			}
		} finally {
			pool.shutdownNow();
		}
	}

	/** 서비스 ID를 오름차순으로 돌려준다 */
	private List<Long> serviceIds() {
		List<Long> ids = new ArrayList<>();
		for (OttService ottService : ottServiceQueryService.listOttServices()) {
			ids.add(ottService.getId());
		}
		ids.sort(null);
		return ids;
	}

	/** 첫 서비스만 구독 중(결제일 비움 → 1일)인 정상 요청 본문 */
	private String validBody(long ottServiceId) {
		return """
				{
				  "subscriptions": [
				    {"ottServiceId": %d, "status": "SUBSCRIBED", "planName": "스탠다드", "billingDay": null}
				  ],
				  "monthlyBudget": 20000,
				  "monthlyWatchMinutes": 900,
				  "watchPreset": "NORMAL"
				}
				""".formatted(ottServiceId);
	}

	/** 새 사용자를 만들고 그 사용자의 Authorization 헤더 값을 돌려준다 */
	private String bearerOfNewUser() {
		return new TestTokens(jwtTokenProvider).bearer(createUser().getId(), UserRole.USER);
	}

	private User createUser() {
		String sub = "g" + UUID.randomUUID().toString().replace("-", "");
		return userRepository.save(User.create(sub + "@example.com", "테스터", OAuthProvider.GOOGLE, sub));
	}
}
