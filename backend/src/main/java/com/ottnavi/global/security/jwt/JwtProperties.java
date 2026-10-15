package com.ottnavi.global.security.jwt;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** JWT·Refresh Token 설정(app.jwt.*). 비밀 값은 환경 변수로만 주입하고 로그에 남기지 않는다 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
		String secret,                                          // HS256 서명 키(Base64). 디코딩 후 32바이트 이상
		@DefaultValue("30m") Duration accessTtl,                // Access Token 수명(TECH T-7)
		@DefaultValue("ottnavi") String issuer,                 // iss 클레임
		@DefaultValue("14d") Duration refreshTtl,               // Refresh Token 수명. 교체할 때마다 다시 이만큼(TECH T-7)
		@DefaultValue("10s") Duration refreshReuseGrace) {      // 직전 토큰을 재사용으로 보지 않는 유예(동시 요청·응답 유실 대비)

	/** 설정 바인딩에 쓰는 생성자를 명시한다(생성자가 둘이라 지정하지 않으면 바인딩이 실패한다) */
	@ConstructorBinding
	public JwtProperties {
	}

	/** 토큰 수명 설정 없이 쓰는 호출용 생성자(Refresh 설정은 기본값) */
	public JwtProperties(String secret, Duration accessTtl, String issuer) {
		this(secret, accessTtl, issuer, Duration.ofDays(14), Duration.ofSeconds(10));
	}

	/** 비밀 값이 로그에 찍히지 않게 toString에서 뺀다 */
	@Override
	public String toString() {
		return "JwtProperties[accessTtl=" + accessTtl + ", issuer=" + issuer
				+ ", refreshTtl=" + refreshTtl + ", refreshReuseGrace=" + refreshReuseGrace + "]";
	}
}
