package com.ottnavi.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ottnavi.global.error.FieldValidationException;
import com.ottnavi.provider.domain.entity.OttService;
import com.ottnavi.provider.service.OttServiceQueryService;
import com.ottnavi.user.domain.entity.User;
import com.ottnavi.user.domain.entity.UserSetting;
import com.ottnavi.user.domain.entity.UserSubscription;
import com.ottnavi.user.domain.enums.OAuthProvider;
import com.ottnavi.user.domain.enums.ServiceSubscriptionStatus;
import com.ottnavi.user.domain.enums.SubscriptionStatus;
import com.ottnavi.user.domain.enums.WatchPreset;
import com.ottnavi.user.domain.repository.UserRepository;
import com.ottnavi.user.domain.repository.UserSettingRepository;
import com.ottnavi.user.domain.repository.UserSubscriptionRepository;
import com.ottnavi.user.exception.OnboardingRequiredException;
import com.ottnavi.user.service.model.SubscriptionState;
import com.ottnavi.user.service.model.UserSettingsState;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 설정 저장 검증 규칙을 DB 없이 확인한다.
 * 막으려는 사고: 없는 서비스·중복 서비스가 저장되는 일, 구독 중이 아닌데 요금제 이름이 남는 일(DB CHECK 위반 500),
 * 결제일을 비운 구독 중 서비스가 1일로 채워지지 않는 일.
 */
class UserSettingServiceTest {

	private static final Long USER_ID = 7L;

	private UserRepository userRepository;
	private UserSettingRepository userSettingRepository;
	private UserSubscriptionRepository userSubscriptionRepository;
	private UserSettingService service;

	@BeforeEach
	void setUp() {
		userRepository = mock(UserRepository.class);
		userSettingRepository = mock(UserSettingRepository.class);
		userSubscriptionRepository = mock(UserSubscriptionRepository.class);
		OttServiceQueryService ottServiceQueryService = mock(OttServiceQueryService.class);

		// 서비스 3개(id 1~3)만 있는 상황으로 충분하다
		List<OttService> services = new ArrayList<>();
		for (long id = 1; id <= 3; id++) {
			OttService ottService = mock(OttService.class);
			when(ottService.getId()).thenReturn(id);
			services.add(ottService);
		}
		when(ottServiceQueryService.listOttServices()).thenReturn(services);

		User user = User.create("a@example.com", "테스터", OAuthProvider.GOOGLE, "sub-a");
		when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
		when(userSettingRepository.findById(USER_ID)).thenReturn(Optional.empty());

		service = new UserSettingService(userRepository, userSettingRepository, userSubscriptionRepository,
				ottServiceQueryService);
	}

	@Test
	void 구독_중인데_결제일이_없으면_1일로_저장한다() {
		// given
		List<SubscriptionState> requested = new ArrayList<>();
		requested.add(new SubscriptionState(1L, ServiceSubscriptionStatus.SUBSCRIBED, " 스탠다드 ", null));

		// when
		UserSettingsState result = service.updateSettings(USER_ID, 20000, 900, WatchPreset.NORMAL, false, requested);

		// then
		ArgumentCaptor<UserSubscription> captor = ArgumentCaptor.forClass(UserSubscription.class);
		verify(userSubscriptionRepository).save(captor.capture());
		assertThat(captor.getValue().getStatus()).isEqualTo(SubscriptionStatus.SUBSCRIBED);
		assertThat(captor.getValue().getBillingDay()).isEqualTo(1);
		assertThat(captor.getValue().getPlanName()).isEqualTo("스탠다드");
		assertThat(result.isOnboardingDone()).isTrue();
	}

	@Test
	void 미구독은_행으로_저장하지_않고_응답에는_서비스_전부를_채운다() {
		// given
		List<SubscriptionState> requested = new ArrayList<>();
		requested.add(new SubscriptionState(2L, ServiceSubscriptionStatus.NOT_SUBSCRIBED, null, null));
		requested.add(new SubscriptionState(3L, ServiceSubscriptionStatus.FREE, null, null));

		// when
		UserSettingsState result = service.updateSettings(USER_ID, 0, 120, WatchPreset.CUSTOM, false, requested);

		// then
		ArgumentCaptor<UserSubscription> captor = ArgumentCaptor.forClass(UserSubscription.class);
		verify(userSubscriptionRepository).save(captor.capture());
		assertThat(captor.getValue().getOttService().getId()).isEqualTo(3L);
		assertThat(result.subscriptions()).hasSize(3);
		assertThat(result.subscriptions().get(0).ottServiceId()).isEqualTo(1L);
		assertThat(result.subscriptions().get(0).status()).isEqualTo(ServiceSubscriptionStatus.NOT_SUBSCRIBED);
		assertThat(result.subscriptions().get(2).status()).isEqualTo(ServiceSubscriptionStatus.FREE);
	}

	@Test
	void 설정_행이_없으면_알림_설정_저장은_ONBOARDING_REQUIRED다() {
		// given: setUp에서 설정 행이 없는 상태다

		// when & then
		assertThatThrownBy(() -> service.updateNotificationEnabled(USER_ID, true))
				.isInstanceOf(OnboardingRequiredException.class);
	}

	@Test
	void 설정_행이_있으면_알림_수신_여부가_바뀐다() {
		// given
		UserSetting setting = UserSetting.create(USER_ID, 20000, 900, WatchPreset.NORMAL, false);
		when(userSettingRepository.findById(USER_ID)).thenReturn(Optional.of(setting));

		// when
		service.updateNotificationEnabled(USER_ID, true);

		// then
		assertThat(setting.isNotificationEnabled()).isTrue();
	}

	@Test
	void 첫_저장이면_설정_행을_만들고_있으면_값을_바꾼다() {
		// given
		List<SubscriptionState> requested = new ArrayList<>();
		requested.add(new SubscriptionState(1L, ServiceSubscriptionStatus.UNKNOWN, null, null));
		UserSetting existing = UserSetting.create(USER_ID, 1000, 200, WatchPreset.LIGHT, false);
		when(userSettingRepository.findById(USER_ID)).thenReturn(Optional.of(existing));

		// when
		service.updateSettings(USER_ID, 30000, 1800, WatchPreset.HEAVY, false, requested);

		// then
		assertThat(existing.getMonthlyBudget()).isEqualTo(30000);
		assertThat(existing.getMonthlyWatchMinutes()).isEqualTo(1800);
		assertThat(existing.getWatchPreset()).isEqualTo(WatchPreset.HEAVY);
		verify(userSettingRepository, never()).save(any());
	}

	@Test
	void 없는_서비스면_검증_실패이고_잠금도_잡지_않는다() {
		// given
		List<SubscriptionState> requested = new ArrayList<>();
		requested.add(new SubscriptionState(99L, ServiceSubscriptionStatus.UNKNOWN, null, null));

		// when & then
		assertThatThrownBy(() -> service.updateSettings(USER_ID, 0, 120, WatchPreset.LIGHT, false, requested))
				.isInstanceOf(FieldValidationException.class)
				.hasMessageContaining("존재하지 않는 서비스");
		verify(userRepository, never()).findByIdForUpdate(any());
	}

	@Test
	void 같은_서비스를_두_번_보내면_검증_실패다() {
		// given
		List<SubscriptionState> requested = new ArrayList<>();
		requested.add(new SubscriptionState(1L, ServiceSubscriptionStatus.FREE, null, null));
		requested.add(new SubscriptionState(1L, ServiceSubscriptionStatus.UNKNOWN, null, null));

		// when & then
		assertThatThrownBy(() -> service.updateSettings(USER_ID, 0, 120, WatchPreset.LIGHT, false, requested))
				.isInstanceOf(FieldValidationException.class)
				.hasMessageContaining("중복");
	}

	@Test
	void 구독_중인데_요금제_이름이_비면_검증_실패다() {
		// given
		List<SubscriptionState> requested = new ArrayList<>();
		requested.add(new SubscriptionState(1L, ServiceSubscriptionStatus.SUBSCRIBED, "  ", 10));

		// when & then
		assertThatThrownBy(() -> service.updateSettings(USER_ID, 0, 120, WatchPreset.LIGHT, false, requested))
				.isInstanceOf(FieldValidationException.class)
				.hasMessageContaining("요금제 이름");
	}

	@Test
	void 구독_중이_아닌데_요금제_이름이나_결제일이_있으면_검증_실패다() {
		// given
		List<SubscriptionState> withPlan = new ArrayList<>();
		withPlan.add(new SubscriptionState(1L, ServiceSubscriptionStatus.FREE, "스탠다드", null));
		List<SubscriptionState> withDay = new ArrayList<>();
		withDay.add(new SubscriptionState(1L, ServiceSubscriptionStatus.NOT_SUBSCRIBED, null, 5));

		// when & then
		assertThatThrownBy(() -> service.updateSettings(USER_ID, 0, 120, WatchPreset.LIGHT, false, withPlan))
				.isInstanceOf(FieldValidationException.class)
				.extracting(e -> ((FieldValidationException) e).getField())
				.isEqualTo("subscriptions[0].planName");
		// 결제일만 보낸 경우에는 오류 위치가 결제일 칸이다
		assertThatThrownBy(() -> service.updateSettings(USER_ID, 0, 120, WatchPreset.LIGHT, false, withDay))
				.isInstanceOf(FieldValidationException.class)
				.extracting(e -> ((FieldValidationException) e).getField())
				.isEqualTo("subscriptions[0].billingDay");
	}
}
