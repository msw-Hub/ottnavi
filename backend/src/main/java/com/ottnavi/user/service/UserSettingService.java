package com.ottnavi.user.service;

import com.ottnavi.global.error.FieldValidationException;
import com.ottnavi.global.error.UnauthorizedException;
import com.ottnavi.provider.domain.entity.OttService;
import com.ottnavi.provider.service.OttServiceQueryService;
import com.ottnavi.user.domain.entity.User;
import com.ottnavi.user.domain.entity.UserSetting;
import com.ottnavi.user.domain.entity.UserSubscription;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 내 설정(월 예산·월 시청 시간·서비스별 이용 상태) 조회와 전체 교체 저장 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserSettingService {

	private static final int DEFAULT_BILLING_DAY = 1; // 결제일을 비우면 1일(PRD FR-08)

	private final UserRepository userRepository;
	private final UserSettingRepository userSettingRepository;
	private final UserSubscriptionRepository userSubscriptionRepository;
	private final OttServiceQueryService ottServiceQueryService;

	/**
	 * 내 설정을 조회한다.
	 * 설정 행이 없으면(온보딩 전) isOnboardingDone=false에 빈 구독 목록과 null 값을 돌려주고,
	 * 있으면 서비스 전부를 ID 오름차순으로 채운다(저장 행이 없는 서비스는 NOT_SUBSCRIBED).
	 */
	public UserSettingsState getSettings(Long userId) {
		// 1. 설정 행이 없으면 온보딩 전 상태로 응답
		Optional<UserSetting> found = userSettingRepository.findById(userId);
		if (found.isEmpty()) {
			return new UserSettingsState(false, new ArrayList<>(), null, null, null, false);
		}
		UserSetting setting = found.get();

		// 2. 저장된 이용 상태 행을 서비스 ID 기준으로 모은다
		Map<Long, SubscriptionState> saved = new HashMap<>();
		for (UserSubscription row : userSubscriptionRepository.findAllByUserId(userId)) {
			SubscriptionState state = new SubscriptionState(row.getOttService().getId(),
					toApiStatus(row.getStatus()), row.getPlanName(), row.getBillingDay());
			saved.put(state.ottServiceId(), state);
		}

		// 3. 서비스 전부를 채워 응답
		return new UserSettingsState(true, fillAllServices(loadServicesById(), saved),
				setting.getMonthlyBudget(), setting.getMonthlyWatchMinutes(), setting.getWatchPreset(),
				setting.isLowestPriceMode());
	}

	/**
	 * 내 설정을 전체 교체로 저장하고 저장된 결과를 돌려준다. 처음 저장하면 온보딩이 끝난 것으로 본다.
	 * 같은 사용자의 동시 PUT은 users 행 잠금으로 직렬화한다(없으면 두 요청이 모두 지운 뒤 같은 유니크 키를 넣어 한쪽이 500이 된다).
	 */
	@Transactional
	public UserSettingsState updateSettings(Long userId, int monthlyBudget, int monthlyWatchMinutes,
			WatchPreset watchPreset, boolean lowestPriceMode, List<SubscriptionState> requested) {
		// 1. 서비스 7개를 읽고 요청 항목을 검증·정규화한다(잠금 전에 끝내 잠금 시간을 줄인다)
		Map<Long, OttService> servicesById = loadServicesById();
		Map<Long, SubscriptionState> normalized = validateAndNormalize(requested, servicesById);

		// 2. 사용자 행을 잠가 같은 사용자의 PUT을 한 줄로 세운다
		Optional<User> lockedUser = userRepository.findByIdForUpdate(userId);
		if (lockedUser.isEmpty()) {
			throw new UnauthorizedException("사용자를 찾을 수 없습니다.");
		}
		User user = lockedUser.get();

		// 3. 기존 이용 상태를 벌크로 지운다(delete 뒤 save는 INSERT가 먼저 나가 유니크 위반이 나므로 쓰지 않는다)
		userSubscriptionRepository.deleteAllByUserId(userId);

		// 4. 설정 행을 만들거나 바꾼다(바꿀 때는 더티 체킹으로 저장된다)
		Optional<UserSetting> existing = userSettingRepository.findById(userId);
		if (existing.isPresent()) {
			existing.get().updateCondition(monthlyBudget, monthlyWatchMinutes, watchPreset, lowestPriceMode);
		} else {
			userSettingRepository.save(
					UserSetting.create(userId, monthlyBudget, monthlyWatchMinutes, watchPreset, lowestPriceMode));
		}

		// 5. 미구독이 아닌 서비스만 행으로 넣는다(미구독은 행을 두지 않는다)
		for (SubscriptionState state : normalized.values()) {
			if (state.status() == ServiceSubscriptionStatus.NOT_SUBSCRIBED) {
				continue;
			}
			OttService ottService = servicesById.get(state.ottServiceId());
			userSubscriptionRepository.save(UserSubscription.create(user, ottService,
					toStoredStatus(state.status()), state.planName(), state.billingDay()));
		}
		log.info("내 설정 저장: userId={}, 구독 행={}", userId, normalized.size());

		// 6. 저장된 모습 그대로 서비스 전부를 채워 응답
		return new UserSettingsState(true, fillAllServices(servicesById, normalized),
				monthlyBudget, monthlyWatchMinutes, watchPreset, lowestPriceMode);
	}

	/**
	 * 알림 수신 여부를 바꾼다. 설정 행이 없으면(온보딩 전) 409 ONBOARDING_REQUIRED다.
	 * 내 설정 저장과 같은 행을 바꾸므로 users 행을 잠가 동시 저장과 줄을 세운다
	 * (Hibernate는 바뀐 열만이 아니라 행 전체를 UPDATE하므로, 잠그지 않으면 한쪽 변경이 덮일 수 있다).
	 */
	@Transactional
	public void updateNotificationEnabled(Long userId, boolean notificationEnabled) {
		// 1. 사용자 행을 잠가 같은 사용자의 설정 쓰기를 한 줄로 세운다
		if (userRepository.findByIdForUpdate(userId).isEmpty()) {
			throw new UnauthorizedException("사용자를 찾을 수 없습니다.");
		}

		// 2. 설정 행이 없으면 온보딩 전이므로 저장할 곳이 없다
		Optional<UserSetting> found = userSettingRepository.findById(userId);
		if (found.isEmpty()) {
			throw new OnboardingRequiredException("내 설정을 먼저 저장해 주세요.");
		}

		// 3. 값을 바꾼다(영속 상태라 커밋 때 더티 체킹으로 UPDATE된다)
		found.get().changeNotificationEnabled(notificationEnabled);
		log.info("알림 설정 저장: userId={}, enabled={}", userId, notificationEnabled);
	}

	/**
	 * 요청 항목을 검증하고 서비스 ID를 키로 한 맵으로 바꾼다.
	 * 없는 서비스·중복 서비스·상태와 맞지 않는 요금제 이름·결제일은 400 VALIDATION_FAILED다.
	 * SUBSCRIBED인데 결제일이 null이면 1일로 채우고, 요금제 이름은 앞뒤 공백을 뗀다.
	 */
	private Map<Long, SubscriptionState> validateAndNormalize(List<SubscriptionState> requested,
			Map<Long, OttService> servicesById) {
		Map<Long, SubscriptionState> result = new LinkedHashMap<>();
		for (int i = 0; i < requested.size(); i++) {
			SubscriptionState item = requested.get(i);
			String prefix = "subscriptions[" + i + "].";

			// 1. 서비스 ID가 7개 서비스에 있고 이전 항목과 겹치지 않는지
			if (!servicesById.containsKey(item.ottServiceId())) {
				throw new FieldValidationException(prefix + "ottServiceId", "존재하지 않는 서비스입니다.");
			}
			if (result.containsKey(item.ottServiceId())) {
				throw new FieldValidationException(prefix + "ottServiceId", "같은 서비스를 중복해서 보낼 수 없습니다.");
			}

			// 2. 상태에 따라 요금제 이름·결제일 규칙을 적용
			String planName = item.planName();
			Integer billingDay = item.billingDay();
			if (item.status() == ServiceSubscriptionStatus.SUBSCRIBED) {
				if (planName == null || planName.isBlank()) {
					throw new FieldValidationException(prefix + "planName", "구독 중이면 요금제 이름이 필요합니다.");
				}
				planName = planName.strip();
				if (billingDay == null) {
					billingDay = DEFAULT_BILLING_DAY;
				}
			} else if (planName != null || billingDay != null) {
				// 요금제 이름이 있으면 그 칸을, 결제일만 있으면 결제일 칸을 오류 위치로 알린다
				String field = planName != null ? "planName" : "billingDay";
				throw new FieldValidationException(prefix + field, "구독 중이 아니면 요금제 이름과 결제일은 비워야 합니다.");
			}
			result.put(item.ottServiceId(), new SubscriptionState(item.ottServiceId(), item.status(), planName, billingDay));
		}
		return result;
	}

	/** 서비스 전부를 ID 오름차순 맵으로 읽는다. 순서가 정해져 있어야 응답 순서가 일정하다 */
	private Map<Long, OttService> loadServicesById() {
		Map<Long, OttService> result = new TreeMap<>();
		for (OttService ottService : ottServiceQueryService.listOttServices()) {
			result.put(ottService.getId(), ottService);
		}
		return result;
	}

	/** 서비스 전부에 대해 저장된 상태를 넣고, 없는 서비스는 NOT_SUBSCRIBED로 채운다(서비스 ID 오름차순) */
	private List<SubscriptionState> fillAllServices(Map<Long, OttService> servicesById,
			Map<Long, SubscriptionState> saved) {
		List<SubscriptionState> result = new ArrayList<>();
		for (Long ottServiceId : servicesById.keySet()) {
			SubscriptionState state = saved.get(ottServiceId);
			if (state == null) {
				state = new SubscriptionState(ottServiceId, ServiceSubscriptionStatus.NOT_SUBSCRIBED, null, null);
			}
			result.add(state);
		}
		return result;
	}

	/** DB 상태를 API 상태로 바꾼다(이름이 같은 값끼리 대응) */
	private ServiceSubscriptionStatus toApiStatus(SubscriptionStatus status) {
		return ServiceSubscriptionStatus.valueOf(status.name());
	}

	/** API 상태를 DB 상태로 바꾼다. NOT_SUBSCRIBED는 저장하지 않으므로 호출하면 안 된다 */
	private SubscriptionStatus toStoredStatus(ServiceSubscriptionStatus status) {
		return SubscriptionStatus.valueOf(status.name());
	}
}
