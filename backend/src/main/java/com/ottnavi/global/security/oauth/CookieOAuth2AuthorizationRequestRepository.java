package com.ottnavi.global.security.oauth;

import com.ottnavi.global.security.jwt.JwtProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.jackson.SecurityJacksonModules;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * OAuth2 인가 요청을 세션 대신 서명한 쿠키에 담아 두는 저장소(TECH 4절).
 * API가 STATELESS라 세션을 쓰지 않고, 서버가 재시작·중지돼도 진행 중인 로그인이 깨지지 않게 한다.
 * 쿠키 값은 `base64url(JSON).base64url(HMAC-SHA256)`이다. JSON 직렬화는 Spring Security가 허용한 타입만
 * 복원하는 Jackson 3 모듈(SecurityJacksonModules)을 쓰고, Java 직렬화는 쓰지 않는다.
 * 서명이 맞지 않거나 파싱이 실패하면 없는 것으로 본다. 쿠키 값은 로그에 남기지 않는다.
 */
@Slf4j
@Component
public class CookieOAuth2AuthorizationRequestRepository
		implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

	public static final String COOKIE_NAME = "oauth2_auth_request";
	private static final String COOKIE_PATH = "/api"; // 인가 시작(/api/oauth2/**)과 콜백(/api/login/**) 둘 다에 실려야 한다
	private static final Duration MAX_AGE = Duration.ofSeconds(180);
	private static final String HMAC_ALGORITHM = "HmacSHA256";
	private static final String KEY_DERIVATION_LABEL = "ottnavi-oauth2-auth-request-cookie"; // JWT 서명 키와 용도를 나누려는 라벨

	private final JsonMapper mapper;
	private final SecretKeySpec hmacKey;
	private final boolean secure;

	public CookieOAuth2AuthorizationRequestRepository(JwtProperties properties,
			@Value("${app.auth.cookie-secure:true}") boolean secure) {
		this.mapper = JsonMapper.builder()
				.addModules(SecurityJacksonModules.getModules(getClass().getClassLoader()))
				.build();
		this.hmacKey = deriveKey(properties.secret());
		this.secure = secure;
	}

	@Override
	public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
		String value = readCookie(request);
		if (value == null) {
			return null;
		}
		return verifyAndDeserialize(value);
	}

	@Override
	public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
			HttpServletRequest request, HttpServletResponse response) {
		// 1. null이면 저장된 요청을 지운다
		if (authorizationRequest == null) {
			response.addHeader(HttpHeaders.SET_COOKIE, build("", Duration.ZERO).toString());
			return;
		}
		// 2. JSON으로 직렬화하고 서명해 쿠키에 담는다
		String json = mapper.writeValueAsString(authorizationRequest);
		String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
		String value = payload + "." + sign(payload);
		response.addHeader(HttpHeaders.SET_COOKIE, build(value, MAX_AGE).toString());
	}

	@Override
	public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
			HttpServletResponse response) {
		// 읽고 삭제한다. 콜백은 한 번만 처리돼야 하므로 읽은 뒤 쿠키를 지운다
		OAuth2AuthorizationRequest loaded = loadAuthorizationRequest(request);
		if (readCookie(request) != null) {
			response.addHeader(HttpHeaders.SET_COOKIE, build("", Duration.ZERO).toString());
		}
		return loaded;
	}

	/** 서명을 검증하고 맞으면 인가 요청을 복원한다. 틀리거나 파싱에 실패하면 null이다 */
	private OAuth2AuthorizationRequest verifyAndDeserialize(String value) {
		// 1. payload와 서명을 나눈다
		int dot = value.indexOf('.');
		if (dot <= 0 || dot == value.length() - 1) {
			return null;
		}
		String payload = value.substring(0, dot);
		String signature = value.substring(dot + 1);
		// 2. 상수 시간으로 서명을 비교한다
		byte[] expected = sign(payload).getBytes(StandardCharsets.UTF_8);
		if (!MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.UTF_8))) {
			return null;
		}
		// 3. 허용된 타입만 복원한다
		try {
			String json = new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
			return mapper.readValue(json, OAuth2AuthorizationRequest.class);
		} catch (RuntimeException e) {
			log.warn("OAuth2 인가 요청 쿠키를 복원하지 못했습니다: {}", e.getClass().getSimpleName());
			return null;
		}
	}

	private String readCookie(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie cookie : cookies) {
			if (COOKIE_NAME.equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}

	private ResponseCookie build(String value, Duration maxAge) {
		return ResponseCookie.from(COOKIE_NAME, value)
				.httpOnly(true)
				.secure(secure)
				.sameSite("Lax") // 구글에서 돌아오는 최상위 GET 이동에는 실린다
				.path(COOKIE_PATH)
				.maxAge(maxAge)
				.build();
	}

	private String sign(String payload) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(hmacKey);
			return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("HMAC 서명에 실패했습니다.", e);
		}
	}

	/** JWT 비밀 값에서 쿠키 서명 전용 키를 파생한다(HMAC(secret, label)) */
	private static SecretKeySpec deriveKey(String jwtSecretBase64) {
		try {
			byte[] secret = Base64.getDecoder().decode(jwtSecretBase64.strip());
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
			return new SecretKeySpec(mac.doFinal(KEY_DERIVATION_LABEL.getBytes(StandardCharsets.UTF_8)), HMAC_ALGORITHM);
		} catch (GeneralSecurityException | IllegalArgumentException e) {
			throw new IllegalStateException("쿠키 서명 키를 만들 수 없습니다. app.jwt.secret을 확인하세요.");
		}
	}
}
