package com.ottnavi.user.service;

import com.ottnavi.global.security.token.RefreshTokenStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 회원 탈퇴. DB 삭제를 먼저 커밋하고 그 뒤에 Redis의 Refresh Token 계열을 지운다(외부 호출은 트랜잭션 밖, backend.md).
 * 사용자 단위로 저장한 Redis 캐시는 아직 없다(캐시는 작품·홈·계산 결과처럼 모두에게 같은 값뿐이다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserWithdrawService {

	private final UserWithdrawWriter userWithdrawWriter;
	private final RefreshTokenStore refreshTokenStore;

	/** 사용자 데이터를 지우고 Refresh Token 계열을 폐기한다. 이미 지워진 사용자면 401이다 */
	public void withdraw(Long userId) {
		// 1. DB 삭제(짧은 트랜잭션, 여기서 커밋까지 끝난다)
		userWithdrawWriter.deleteUser(userId);

		// 2. Redis의 Refresh Token 계열을 지운다. 탈퇴는 이미 끝났으므로 실패해도 204로 응답한다.
		//    남은 계열은 재발급 때 사용자가 없어 401이 되고 그때 다시 지워지며 TTL(14일)로도 사라진다
		try {
			refreshTokenStore.revokeAllOf(userId);
		} catch (RuntimeException e) {
			log.error("탈퇴 뒤 Refresh Token 계열 삭제에 실패했습니다: userId={}", userId, e);
		}
	}
}
