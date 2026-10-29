package com.ottnavi.user.domain.entity;

import com.ottnavi.global.common.BaseTimeEntity;
import com.ottnavi.user.domain.enums.OAuthProvider;
import com.ottnavi.user.domain.enums.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 로그인 사용자(ERD users). 테이블 이름은 예약어(user)를 피하려고 복수형이다.
 * 권한은 생성 때 USER로 고정한다. 관리자 승격은 DB에서 직접 바꾼다(가입 경로로는 만들 수 없다).
 * 예외로 데모 계정(DEMO)은 데모 로그인이 createDemo로 만든다.
 */
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

	private static final int NAME_MAX_LENGTH = 100;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 255)
	private String email; // 이메일(유니크)

	@Column(nullable = false, length = NAME_MAX_LENGTH)
	private String name; // 표시 이름. 비어 오면 이메일 앞부분

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private UserRole role; // 권한

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OAuthProvider oauthProvider; // 소셜 로그인 제공자

	@Column(nullable = false, length = 100)
	private String oauthSubject; // 소셜 계정의 고유 번호(sub)

	/** 소셜 로그인으로 가입한 일반 사용자를 만든다(role은 항상 USER) */
	public static User create(String email, String name, OAuthProvider oauthProvider, String oauthSubject) {
		User user = new User();
		user.email = email;
		user.role = UserRole.USER;
		user.oauthProvider = oauthProvider;
		user.oauthSubject = oauthSubject;
		user.name = normalizeName(name, email);
		return user;
	}

	/** 데모 계정 행을 만든다(role·로그인 방식 모두 DEMO). 첫 데모 로그인 때 서버가 한 번만 만든다 */
	public static User createDemo(String email, String name, String oauthSubject) {
		User user = new User();
		user.email = email;
		user.role = UserRole.DEMO;
		user.oauthProvider = OAuthProvider.DEMO;
		user.oauthSubject = oauthSubject;
		user.name = normalizeName(name, email);
		return user;
	}

	/** 로그인 때 소셜 계정의 이름·이메일이 바뀌었으면 반영한다. 이름이 비면 이메일 앞부분을 쓴다(더티 체킹으로 저장) */
	public void updateProfile(String email, String name) {
		this.email = email;
		this.name = normalizeName(name, email);
	}

	/** 이름이 비면 이메일 앞부분으로 채우고 100자로 자른다 */
	private static String normalizeName(String name, String email) {
		// 1. 이름이 비어 있으면 이메일의 @ 앞부분을 쓴다
		String result = name;
		if (result == null || result.isBlank()) {
			int at = email.indexOf('@');
			if (at > 0) {
				result = email.substring(0, at);
			} else {
				result = email;
			}
		}
		result = result.strip();
		// 2. 컬럼 길이를 넘으면 자른다(서로게이트 쌍 중간에서 끊기면 한 글자를 더 뺀다)
		if (result.length() > NAME_MAX_LENGTH) {
			int end = NAME_MAX_LENGTH;
			if (Character.isHighSurrogate(result.charAt(end - 1))) {
				end--;
			}
			result = result.substring(0, end);
		}
		return result;
	}
}
