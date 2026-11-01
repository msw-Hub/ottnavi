package com.ottnavi.user.service;

import com.ottnavi.global.error.ResourceNotFoundException;
import com.ottnavi.global.ratelimit.DemoLoginRateLimiter;
import com.ottnavi.global.security.token.RefreshTokenStore;
import com.ottnavi.user.exception.DemoLoginFailedException;
import com.ottnavi.user.service.model.LoginUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 데모 계정 로그인(TECH 4절). 서버 설정(app.demo-login)에 둔 아이디·비밀번호 한 쌍만 통과시킨다.
 * 일반 회원용 아이디·비밀번호 로그인이 아니며 계정은 하나뿐이다.
 *
 * <pre>
 * 설정이 비어 있음(꺼짐)           → 404 RESOURCE_NOT_FOUND(경로가 없는 것처럼 보인다)
 * 같은 IP의 시도가 15분 5회를 넘음 → 429 RATE_LIMIT_EXCEEDED
 * 아이디나 비밀번호가 틀림          → 401 DEMO_LOGIN_FAILED(어느 쪽이 틀렸는지 알리지 않는다)
 * 둘 다 맞음                       → 이전 데모 데이터를 비우고 새 데모 계정 행의 LoginUser
 * </pre>
 *
 * 데모 계정은 모든 방문자가 한 행을 같이 쓰므로, 로그인할 때마다 이전 방문자의 데이터를 지우고 새로 시작한다.
 * 순서: 기존 행이 있으면 ① Redis에서 그 사용자의 Refresh Token 계열을 폐기(실패하면 503으로 끝나고 DB는 그대로)
 * → ② 행 삭제(UserDemoResetWriter, 하위 데이터는 FK CASCADE) → ③ 새 행 생성. 사용자 id는 로그인마다 새로 발급된다.
 *
 * Origin 검사(403)는 컨트롤러가 꺼짐 확인 뒤, 이 서비스의 login 앞에서 한다.
 * 입력한 아이디·비밀번호는 어떤 로그에도 남기지 않는다. 트랜잭션은 UserLoginService가 맡고 이 클래스는 트랜잭션 밖이다
 * (동시 첫 로그인의 유니크 위반 뒤 새 트랜잭션으로 다시 읽어야 하기 때문이다).
 */
@Slf4j
@Service
public class DemoLoginService {

	private final DemoLoginProperties properties;
	private final UserLoginService userLoginService;
	private final UserDemoResetWriter userDemoResetWriter; // 데모 행 삭제 트랜잭션(다른 빈이라야 @Transactional이 적용된다)
	private final RefreshTokenStore refreshTokenStore;     // 이전 데모 세션의 Refresh Token 계열 폐기용
	private final ObjectProvider<DemoLoginRateLimiter> rateLimiterProvider; // 요청 제한을 끈 설정(테스트)에서는 빈이 없다

	public DemoLoginService(DemoLoginProperties properties, UserLoginService userLoginService,
			UserDemoResetWriter userDemoResetWriter, RefreshTokenStore refreshTokenStore,
			ObjectProvider<DemoLoginRateLimiter> rateLimiterProvider) {
		this.properties = properties;
		this.userLoginService = userLoginService;
		this.userDemoResetWriter = userDemoResetWriter;
		this.refreshTokenStore = refreshTokenStore;
		this.rateLimiterProvider = rateLimiterProvider;
	}

	/** 데모 로그인이 꺼져 있으면 404다. 컨트롤러가 Origin 검사보다 먼저 부른다(꺼진 상태에서는 경로가 있는지도 드러내지 않는다) */
	public void verifyEnabled() {
		if (!properties.isEnabled()) {
			throw new ResourceNotFoundException("요청한 경로를 찾을 수 없습니다.");
		}
	}

	/**
	 * 아이디·비밀번호를 확인하고 데모 계정 사용자를 돌려준다. 토큰 발급은 호출하는 쪽(컨트롤러)이 트랜잭션 밖에서 한다.
	 *
	 * @param loginId  입력한 아이디(null이면 빈 문자열로 본다)
	 * @param password 입력한 비밀번호(null이면 빈 문자열로 본다)
	 * @param clientIp 컨트롤러가 request.getRemoteAddr()로 꺼내 넘긴 방문자 IP
	 */
	public LoginUser login(String loginId, String password, String clientIp) {
		// 1. 꺼져 있으면 404(컨트롤러가 먼저 확인했어도 서비스만 불리는 경우를 막는다)
		verifyEnabled();

		// 2. 같은 IP의 시도 한도를 쓴다(맞든 틀리든 한 번으로 센다)
		DemoLoginRateLimiter rateLimiter = rateLimiterProvider.getIfAvailable();
		if (rateLimiter != null) {
			rateLimiter.check(clientIp);
		}

		// 3. 아이디와 비밀번호를 둘 다 비교한 뒤 결과를 합친다(한쪽이 틀려도 나머지를 비교해 응답 시간으로 구분되지 않게 한다)
		boolean loginIdMatches = matches(loginId, properties.loginId());
		boolean passwordMatches = matches(password, properties.password());
		if (!(loginIdMatches & passwordMatches)) {
			log.warn("데모 로그인 실패: 아이디 또는 비밀번호 불일치"); // 입력값은 남기지 않는다
			throw new DemoLoginFailedException("아이디 또는 비밀번호가 올바르지 않습니다.");
		}

		// 4. 이전 방문자의 데모 데이터를 비운다. 세션을 먼저 끊고(Redis 실패 시 503으로 끝나 DB는 그대로) 그다음 행을 지운다
		Optional<LoginUser> previous = userLoginService.findDemo();
		if (previous.isPresent()) {
			refreshTokenStore.revokeAllOf(previous.get().userId());
			userDemoResetWriter.deleteDemoUser(previous.get().userId());
		}

		// 5. 데모 계정 행을 새로 만든다. 동시 로그인으로 유니크 위반이 나면 먼저 만든 행을 다시 읽는다
		LoginUser loginUser;
		try {
			loginUser = userLoginService.findOrCreateDemo();
		} catch (DataIntegrityViolationException e) {
			Optional<LoginUser> created = userLoginService.findDemo();
			if (created.isEmpty()) {
				throw e;
			}
			loginUser = created.get();
		}
		log.info("데모 로그인 성공: userId={}", loginUser.userId());
		return loginUser;
	}

	/**
	 * 입력값과 설정값을 SHA-256 해시끼리 상수 시간으로 비교한다.
	 * 해시로 길이를 32바이트로 맞춰 MessageDigest.isEqual이 길이 차이로 일찍 끝나지 않게 한다.
	 */
	private static boolean matches(String input, String expected) {
		String safeInput = input;
		if (safeInput == null) {
			safeInput = "";
		}
		return MessageDigest.isEqual(sha256(safeInput), sha256(expected));
	}

	private static byte[] sha256(String value) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException e) {
			// 모든 자바 런타임이 SHA-256을 제공해야 하므로(Java SE 명세) 여기 오면 런타임 자체의 문제다
			throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
		}
	}
}
