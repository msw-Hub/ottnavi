package com.ottnavi.user.service;

import com.ottnavi.user.domain.entity.User;
import com.ottnavi.user.domain.enums.OAuthProvider;
import com.ottnavi.user.domain.enums.UserRole;
import com.ottnavi.user.domain.repository.UserRepository;
import com.ottnavi.user.service.model.GoogleProfile;
import com.ottnavi.user.service.model.LoginUser;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 구글 로그인 후 사용자 행을 만들거나 갱신하고, 재발급 때 권한을 다시 읽는다 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserLoginService {

	private final UserRepository userRepository;

	/**
	 * 구글 프로필로 로그인 처리한다. 첫 로그인이면 USER로 가입하고, 아니면 이메일·이름을 갱신한다.
	 * 새 이메일이 다른 사용자의 이메일과 같으면(유니크 충돌) 이메일은 그대로 두고 이름만 갱신한 뒤 로그인을 허용한다.
	 */
	@Transactional
	public LoginUser login(GoogleProfile profile) {
		// 1. 구글 계정(sub)으로 기존 가입자를 찾는다
		Optional<User> found = userRepository.findByOauthProviderAndOauthSubject(OAuthProvider.GOOGLE, profile.sub());
		if (found.isEmpty()) {
			// 2. 첫 로그인이면 USER로 가입시킨다. 이메일이 이미 쓰이고 있으면 가입시키지 않는다
			if (userRepository.existsByEmail(profile.email())) {
				throw new IllegalStateException("이미 다른 계정이 사용 중인 이메일이라 가입할 수 없습니다.");
			}
			User created = userRepository.save(
					User.create(profile.email(), profile.name(), OAuthProvider.GOOGLE, profile.sub()));
			return new LoginUser(created.getId(), created.getRole());
		}
		// 3. 재로그인이면 프로필을 갱신한다(더티 체킹으로 저장)
		User user = found.get();
		String email = profile.email();
		if (!email.equals(user.getEmail()) && userRepository.existsByEmail(email)) {
			log.warn("이메일이 다른 사용자와 겹쳐 이메일은 갱신하지 않았습니다: userId={}", user.getId());
			email = user.getEmail();
		}
		user.updateProfile(email, profile.name());
		return new LoginUser(user.getId(), user.getRole());
	}

	/** 사용자 권한을 DB에서 다시 읽는다(재발급 때 AT에 최신 권한을 싣기 위함). 사용자가 없으면 비어 있다 */
	public Optional<UserRole> findRole(Long userId) {
		Optional<User> user = userRepository.findById(userId);
		if (user.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(user.get().getRole());
	}
}
