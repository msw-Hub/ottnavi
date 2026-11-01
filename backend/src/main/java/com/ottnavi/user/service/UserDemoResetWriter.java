package com.ottnavi.user.service;

import com.ottnavi.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 데모 계정 행 삭제 한 번을 짧은 트랜잭션으로 맡는다. DemoLoginService와 다른 빈이어야 @Transactional이 적용된다
 * (같은 클래스 안 호출은 프록시를 거치지 않는다). Redis 폐기는 이 트랜잭션 전에 DemoLoginService가 한다.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class UserDemoResetWriter {

	private final UserRepository userRepository;

	/**
	 * 데모 사용자 행을 지워 이전 방문자의 데이터를 비운다. 설정·구독·요금 예외·찜·플랜(하위 표 포함)·알림 행은
	 * FK ON DELETE CASCADE로, calc_run.plan_id는 SET NULL로 정리된다(V5~V7, UserWithdrawWriter와 같은 방식).
	 * 동시 로그인으로 이미 지워졌으면 아무것도 하지 않는다.
	 */
	public void deleteDemoUser(Long userId) {
		// 1. 사용자 행을 잠근다. 이미 지워졌으면(다른 로그인이 먼저 초기화함) 할 일이 없다
		if (userRepository.findByIdForUpdate(userId).isEmpty()) {
			return;
		}

		// 2. 벌크로 지운다(JPA cascade 대신 DB cascade가 하위 행을 지운다)
		userRepository.deleteByUserId(userId);
		log.info("데모 계정 데이터를 초기화했다: userId={}", userId);
	}
}
