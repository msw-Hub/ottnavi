package com.ottnavi.global.security.token;

import com.ottnavi.global.security.jwt.JwtProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Refresh Token 쿠키를 만들고 지우고 읽는다. HttpOnly, SameSite=Lax, Path=/api/auth(TECH 4절).
 * 값(토큰 원문)은 어디에도 로그로 남기지 않는다.
 */
@Component
public class RefreshTokenCookies {

	public static final String COOKIE_NAME = "rt";
	private static final String COOKIE_PATH = "/api/auth";

	private final boolean secure;
	private final long maxAgeSeconds;

	public RefreshTokenCookies(@Value("${app.auth.cookie-secure:true}") boolean secure, JwtProperties properties) {
		this.secure = secure;
		this.maxAgeSeconds = properties.refreshTtl().toSeconds();
	}

	/** 새 Refresh Token을 심는 Set-Cookie를 응답에 추가한다 */
	public void add(HttpServletResponse response, String rawToken) {
		response.addHeader(HttpHeaders.SET_COOKIE, build(rawToken, maxAgeSeconds).toString());
	}

	/** 쿠키를 지우는 Set-Cookie(같은 Path, Max-Age=0)를 응답에 추가한다 */
	public void clear(HttpServletResponse response) {
		response.addHeader(HttpHeaders.SET_COOKIE, build("", 0).toString());
	}

	/** 요청의 Refresh Token 쿠키 값을 읽는다. 없으면 null */
	public String read(HttpServletRequest request) {
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

	private ResponseCookie build(String value, long maxAge) {
		return ResponseCookie.from(COOKIE_NAME, value)
				.httpOnly(true)
				.secure(secure)
				.sameSite("Lax")
				.path(COOKIE_PATH)
				.maxAge(maxAge)
				.build();
	}
}
