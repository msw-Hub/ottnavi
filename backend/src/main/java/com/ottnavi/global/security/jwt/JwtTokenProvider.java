package com.ottnavi.global.security.jwt;

import com.ottnavi.global.security.AuthUser;
import com.ottnavi.user.domain.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * Access Token(JWT)을 발급하고 검증한다. 서명은 HS256으로 고정하고, 검증도 HS256만 허용해
 * alg 헤더를 바꾼 토큰(none·HS512 등)을 거부한다. 토큰 값은 로그에 남기지 않는다.
 * 클레임: iss, sub(users.id 문자열), role, iat, exp.
 */
@Slf4j
public class JwtTokenProvider {

	private static final int MIN_SECRET_BYTES = 32;     // HS256 최소 키 길이(256비트)
	private static final long CLOCK_SKEW_SECONDS = 30;  // 서버 간 시각 오차 허용
	private static final String ROLE_CLAIM = "role";

	private final SecretKey key;
	private final String issuer;
	private final Duration accessTtl;
	private final JwtParser parser;

	public JwtTokenProvider(JwtProperties properties) {
		// 1. 비밀 값이 없거나 Base64가 아니거나 32바이트 미만이면 기동 단계에서 실패시킨다(값은 메시지에 넣지 않는다)
		if (!StringUtils.hasText(properties.secret())) {
			throw new IllegalStateException("app.jwt.secret(JWT_SECRET)이 비어 있습니다.");
		}
		byte[] secretBytes;
		try {
			secretBytes = Decoders.BASE64.decode(properties.secret().strip());
		} catch (RuntimeException e) {
			throw new IllegalStateException("app.jwt.secret은 Base64 문자열이어야 합니다.");
		}
		if (secretBytes.length < MIN_SECRET_BYTES) {
			throw new IllegalStateException("app.jwt.secret은 디코딩 후 32바이트(256비트) 이상이어야 합니다.");
		}
		// 2. 키와 파서를 한 번만 만든다. 파서는 HS256만 허용하고 발급자·만료를 검사한다
		this.key = Keys.hmacShaKeyFor(secretBytes);
		this.issuer = properties.issuer();
		this.accessTtl = properties.accessTtl();
		this.parser = Jwts.parser()
				.verifyWith(key)
				.sig().clear().add(Jwts.SIG.HS256).and()
				.requireIssuer(issuer)
				.clockSkewSeconds(CLOCK_SKEW_SECONDS)
				.build();
	}

	/** 사용자 id와 권한으로 Access Token을 발급한다 */
	public String issueAccessToken(Long userId, UserRole role) {
		Date issuedAt = new Date();
		Date expiresAt = new Date(issuedAt.getTime() + accessTtl.toMillis());
		return Jwts.builder()
				.issuer(issuer)
				.subject(String.valueOf(userId))
				.claim(ROLE_CLAIM, role.name())
				.issuedAt(issuedAt)
				.expiration(expiresAt)
				.signWith(key, Jwts.SIG.HS256)
				.compact();
	}

	/** 토큰을 검증해 사용자 정보를 돌려준다. 서명·만료·발급자·형식이 맞지 않으면 null이다 */
	public AuthUser parse(String token) {
		// 1. 서명·만료·발급자 검증(실패 사유는 토큰 값 없이 예외 종류만 남긴다)
		Claims claims;
		try {
			claims = parser.parseSignedClaims(token).getPayload();
		} catch (RuntimeException e) {
			log.debug("Access Token 검증 실패: {}", e.getClass().getSimpleName());
			return null;
		}
		// 2. sub(숫자)와 role(USER·ADMIN)을 꺼낸다. 모양이 다르면 무효로 본다
		try {
			Long userId = Long.valueOf(claims.getSubject());
			UserRole role = UserRole.valueOf(claims.get(ROLE_CLAIM, String.class));
			return new AuthUser(userId, role);
		} catch (RuntimeException e) {
			log.debug("Access Token 클레임 형식 오류: {}", e.getClass().getSimpleName());
			return null;
		}
	}
}
