package com.ottnavi.user.service;

import com.ottnavi.global.error.UnauthorizedException;
import com.ottnavi.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴의 DB 삭제 한 번을 짧은 트랜잭션으로 맡는다. UserWithdrawService와 다른 빈이어야 @Transactional이 적용된다
 * (같은 클래스 안 호출은 프록시를 거치지 않는다). Redis 삭제는 이 트랜잭션이 커밋된 뒤 UserWithdrawService가 한다.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class UserWithdrawWriter {

	private final UserRepository userRepository;

	/**
	 * users 행 하나를 지운다. 설정·구독·요금·찜·플랜·알림 행은 FK ON DELETE CASCADE로, calc_run.plan_id는 SET NULL로 정리된다
	 * (V5~V7, TECH T-1). 이미 지워진 사용자면 401이다.
	 */
	public void deleteUser(Long userId) {
		// 1. 사용자 행을 잠근다. 같은 사용자의 찜 추가·설정 저장은 이 잠금 뒤에 줄을 서므로, 지워진 뒤에는 행을 못 찾아 401로 끝난다
		if (userRepository.findByIdForUpdate(userId).isEmpty()) {
			throw new UnauthorizedException("사용자를 찾을 수 없습니다.");
		}

		// 2. 벌크로 지운다(JPA cascade 대신 DB cascade가 하위 행을 지운다)
		userRepository.deleteByUserId(userId);
		log.info("회원 탈퇴: userId={}", userId);
	}
}
