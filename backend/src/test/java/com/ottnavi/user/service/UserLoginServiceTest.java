package com.ottnavi.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.support.IntegrationTestSupport;
import com.ottnavi.user.domain.entity.User;
import com.ottnavi.user.domain.enums.OAuthProvider;
import com.ottnavi.user.domain.enums.UserRole;
import com.ottnavi.user.domain.repository.UserRepository;
import com.ottnavi.user.service.model.GoogleProfile;
import com.ottnavi.user.service.model.LoginUser;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 구글 로그인 후 사용자 가입·갱신 규칙을 실제 DB에서 확인한다(Docker Desktop 필요).
 * 막으려는 사고: 같은 구글 계정이 중복 가입되는 일, 이메일 유니크 충돌로 기존 사용자가 로그인하지 못하는 일.
 */
class UserLoginServiceTest extends IntegrationTestSupport {

	@Autowired
	private UserLoginService userLoginService;

	@Autowired
	private UserRepository userRepository;

	@Test
	void 첫_로그인은_USER로_가입한다() {
		// given
		String sub = uniqueSub();
		GoogleProfile profile = new GoogleProfile(sub, sub + "@example.com", "홍길동");

		// when
		LoginUser loginUser = userLoginService.login(profile);

		// then
		User saved = userRepository.findById(loginUser.userId()).orElseThrow();
		assertThat(loginUser.role()).isEqualTo(UserRole.USER);
		assertThat(saved.getEmail()).isEqualTo(sub + "@example.com");
		assertThat(saved.getName()).isEqualTo("홍길동");
		assertThat(saved.getOauthProvider()).isEqualTo(OAuthProvider.GOOGLE);
	}

	@Test
	void 재로그인은_같은_행의_이메일과_이름을_갱신한다() {
		// given
		String sub = uniqueSub();
		LoginUser first = userLoginService.login(new GoogleProfile(sub, sub + "@example.com", "옛 이름"));

		// when
		LoginUser second = userLoginService.login(new GoogleProfile(sub, sub + "-new@example.com", "새 이름"));

		// then
		assertThat(second.userId()).isEqualTo(first.userId());
		User saved = userRepository.findById(first.userId()).orElseThrow();
		assertThat(saved.getEmail()).isEqualTo(sub + "-new@example.com");
		assertThat(saved.getName()).isEqualTo("새 이름");
	}

	@Test
	void 이름이_비어_오면_이메일_앞부분을_쓴다() {
		// given
		String sub = uniqueSub();

		// when
		LoginUser loginUser = userLoginService.login(new GoogleProfile(sub, sub + "@example.com", "  "));

		// then
		assertThat(userRepository.findById(loginUser.userId()).orElseThrow().getName()).isEqualTo(sub);
	}

	@Test
	void 새_이메일이_다른_사용자와_겹치면_이메일은_두고_로그인은_성공한다() {
		// given: 두 사용자. 둘째가 첫째의 이메일로 바꾸려 한다
		String subA = uniqueSub();
		String subB = uniqueSub();
		userLoginService.login(new GoogleProfile(subA, subA + "@example.com", "A"));
		LoginUser b = userLoginService.login(new GoogleProfile(subB, subB + "@example.com", "B"));

		// when
		LoginUser again = userLoginService.login(new GoogleProfile(subB, subA + "@example.com", "B2"));

		// then: 이메일은 그대로, 이름만 갱신
		assertThat(again.userId()).isEqualTo(b.userId());
		User saved = userRepository.findById(b.userId()).orElseThrow();
		assertThat(saved.getEmail()).isEqualTo(subB + "@example.com");
		assertThat(saved.getName()).isEqualTo("B2");
	}

	@Test
	void 재발급용_권한_조회는_사용자가_없으면_비어_있다() {
		assertThat(userLoginService.findRole(Long.MAX_VALUE)).isEmpty();
	}

	private String uniqueSub() {
		return "g" + UUID.randomUUID().toString().replace("-", "");
	}
}
