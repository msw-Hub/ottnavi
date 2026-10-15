package com.ottnavi.user.domain.repository;

import com.ottnavi.user.domain.entity.User;
import com.ottnavi.user.domain.enums.OAuthProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	/** 소셜 계정의 고유 번호로 사용자를 찾는다(로그인 때 기존 가입자인지 판정) */
	Optional<User> findByOauthProviderAndOauthSubject(OAuthProvider oauthProvider, String oauthSubject);

	/** 이메일을 쓰는 사용자가 있는지 확인한다(이메일 유니크 충돌을 미리 피하려는 용도) */
	boolean existsByEmail(String email);
}
