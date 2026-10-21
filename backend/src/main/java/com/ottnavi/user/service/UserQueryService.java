package com.ottnavi.user.service;

import com.ottnavi.global.error.UnauthorizedException;
import com.ottnavi.user.domain.entity.User;
import com.ottnavi.user.domain.entity.UserSetting;
import com.ottnavi.user.domain.entity.UserSubscription;
import com.ottnavi.user.domain.enums.ServiceSubscriptionStatus;
import com.ottnavi.user.domain.enums.SubscriptionStatus;
import com.ottnavi.user.domain.repository.UserRepository;
import com.ottnavi.user.domain.repository.UserSettingRepository;
import com.ottnavi.user.domain.repository.UserSubscriptionRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 다른 도메인(찜 등)이 사용자 데이터를 읽거나 잠글 때 쓰는 창구. 다른 도메인이 user 리포지토리를 직접 쓰지 않게 한다 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserQueryService {

	private final UserRepository userRepository;
	private final UserSubscriptionRepository userSubscriptionRepository;
	private final UserSettingRepository userSettingRepository;

	/** "가장 저렴한 요금제로 계산할래요" 체크 여부. 설정 행이 없으면(온보딩 전) 꺼짐(false)이다 */
	public boolean isLowestPriceMode(Long userId) {
		Optional<UserSetting> found = userSettingRepository.findById(userId);
		if (found.isEmpty()) {
			return false;
		}
		return found.get().isLowestPriceMode();
	}

	/** 사용자의 이메일을 돌려준다. 사용자가 없으면(탈퇴) 빈 값이다. 알림 발송기가 발송 직전에 수신 주소를 다시 확인하는 용도다 */
	public Optional<String> findEmail(Long userId) {
		Optional<User> found = userRepository.findById(userId);
		if (found.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(found.get().getEmail());
	}

	/**
	 * 사용자 행을 FOR UPDATE로 잠가 같은 사용자의 쓰기 요청을 한 줄로 세운다. 호출하는 쪽의 쓰기 트랜잭션 안에서만 쓴다
	 * (트랜잭션이 없으면 잠금이 바로 풀려 의미가 없으므로 MANDATORY로 막는다). 잠금은 그 트랜잭션이 끝날 때 풀린다.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void lockUser(Long userId) {
		if (userRepository.findByIdForUpdate(userId).isEmpty()) {
			throw new UnauthorizedException("사용자를 찾을 수 없습니다.");
		}
	}

	/**
	 * 지금 이용할 수 있는 서비스 ID 집합을 돌려준다. 구독 중(SUBSCRIBED)과 무료 이용(FREE)만 포함하고,
	 * 잘 모르겠음(UNKNOWN)과 미구독은 뺀다.
	 */
	public Set<Long> listUsableServiceIds(Long userId) {
		Set<Long> result = new HashSet<>();
		for (UserSubscription row : userSubscriptionRepository.findAllByUserId(userId)) {
			if (row.getStatus() == SubscriptionStatus.SUBSCRIBED || row.getStatus() == SubscriptionStatus.FREE) {
				result.add(row.getOttService().getId());
			}
		}
		return result;
	}

	/**
	 * 사용자의 서비스별 이용 상태를 서비스 ID로 찾을 수 있게 돌려준다. 행이 없는 서비스(미구독)는 담지 않으므로
	 * 호출하는 쪽이 NOT_SUBSCRIBED로 채운다.
	 */
	public Map<Long, ServiceSubscriptionStatus> mapStatusByServiceId(Long userId) {
		Map<Long, ServiceSubscriptionStatus> result = new HashMap<>();
		for (UserSubscription row : userSubscriptionRepository.findAllByUserId(userId)) {
			// DB 값(SUBSCRIBED/FREE/UNKNOWN)과 API 값은 이름이 같아 그대로 옮긴다
			result.put(row.getOttService().getId(), ServiceSubscriptionStatus.valueOf(row.getStatus().name()));
		}
		return result;
	}
}
