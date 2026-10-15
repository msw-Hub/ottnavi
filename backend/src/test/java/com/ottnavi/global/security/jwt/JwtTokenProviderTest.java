package com.ottnavi.global.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ottnavi.global.security.AuthUser;
import com.ottnavi.user.domain.enums.UserRole;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

/** Access Token 발급·검증과 알고리즘·키 길이 방어를 확인한다(Docker·스프링 컨텍스트 불필요) */
class JwtTokenProviderTest {

	private static final Duration TTL = Duration.ofMinutes(30);

	@Test
	void 발급한_토큰을_검증하면_사용자_id와_권한이_돌아온다() {
		// given
		JwtTokenProvider provider = provider(secret(32, 'a'), TTL, "ottnavi");

		// when
		String token = provider.issueAccessToken(42L, UserRole.ADMIN);
		AuthUser authUser = provider.parse(token);

		// then
		assertThat(authUser).isEqualTo(new AuthUser(42L, UserRole.ADMIN));
	}

	@Test
	void 만료된_토큰은_거부한다() {
		// given: 수명이 -1분이라 허용 오차(30초)를 넘겨 이미 만료된 토큰이 나온다
		JwtTokenProvider provider = provider(secret(32, 'a'), Duration.ofMinutes(-1), "ottnavi");
		String token = provider.issueAccessToken(1L, UserRole.USER);

		// when & then
		assertThat(provider.parse(token)).isNull();
	}

	@Test
	void 다른_키로_서명한_토큰은_거부한다() {
		// given
		JwtTokenProvider issuer = provider(secret(32, 'b'), TTL, "ottnavi");
		JwtTokenProvider verifier = provider(secret(32, 'a'), TTL, "ottnavi");

		// when & then
		assertThat(verifier.parse(issuer.issueAccessToken(1L, UserRole.USER))).isNull();
	}

	@Test
	void 발급자가_다른_토큰은_거부한다() {
		// given
		JwtTokenProvider other = provider(secret(32, 'a'), TTL, "other-service");
		JwtTokenProvider provider = provider(secret(32, 'a'), TTL, "ottnavi");

		// when & then
		assertThat(provider.parse(other.issueAccessToken(1L, UserRole.USER))).isNull();
	}

	@Test
	void 서명이_없는_alg_none_토큰은_거부한다() {
		// given: signWith 없이 만들면 서명 없는 토큰이 된다
		JwtTokenProvider provider = provider(secret(32, 'a'), TTL, "ottnavi");
		String unsecured = Jwts.builder().issuer("ottnavi").subject("1").claim("role", "ADMIN")
				.expiration(new Date(System.currentTimeMillis() + 60_000)).compact();

		// when & then
		assertThat(provider.parse(unsecured)).isNull();
	}

	@Test
	void 같은_키라도_HS512로_서명한_토큰은_거부한다() {
		// given: 64바이트 키라 HS512 서명이 가능하다. 허용 알고리즘을 HS256으로 제한했는지를 본다
		String secret = secret(64, 'c');
		JwtTokenProvider provider = provider(secret, TTL, "ottnavi");
		SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
		String hs512Token = Jwts.builder().issuer("ottnavi").subject("1").claim("role", "ADMIN")
				.expiration(new Date(System.currentTimeMillis() + 60_000))
				.signWith(key, Jwts.SIG.HS512).compact();

		// when & then
		assertThat(provider.parse(hs512Token)).isNull();
	}

	@Test
	void 형식이_깨진_토큰과_알_수_없는_권한은_거부한다() {
		// given
		JwtTokenProvider provider = provider(secret(32, 'a'), TTL, "ottnavi");
		SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret(32, 'a')));
		String unknownRole = Jwts.builder().issuer("ottnavi").subject("1").claim("role", "SUPER")
				.expiration(new Date(System.currentTimeMillis() + 60_000))
				.signWith(key, Jwts.SIG.HS256).compact();

		// when & then
		assertThat(provider.parse("not-a-jwt")).isNull();
		assertThat(provider.parse(unknownRole)).isNull();
	}

	@Test
	void 비밀_값이_비었거나_Base64가_아니거나_32바이트_미만이면_생성에_실패한다() {
		// given & when & then
		assertThatThrownBy(() -> provider("  ", TTL, "ottnavi")).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> provider("!!!not-base64!!!", TTL, "ottnavi")).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> provider(secret(31, 'a'), TTL, "ottnavi")).isInstanceOf(IllegalStateException.class);
	}

	private JwtTokenProvider provider(String secret, Duration accessTtl, String issuer) {
		return new JwtTokenProvider(new JwtProperties(secret, accessTtl, issuer));
	}

	/** 같은 문자를 반복한 바이트 배열을 Base64로 만든 시험용 비밀 값 */
	private String secret(int bytes, char fill) {
		return Base64.getEncoder().encodeToString(String.valueOf(fill).repeat(bytes).getBytes());
	}
}
