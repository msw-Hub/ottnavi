package com.ottnavi.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Vercel을 거친 /api 요청만 받도록 오리진 비밀 헤더(x-origin-secret)를 검사한다(TECH T-8 ①).
 * Cloudtype 주소로 직접 들어와 X-Forwarded-For를 위조하는 요청을 막는 것이 목적이다(TECH 4절).
 * Security 체인 밖의 서블릿 필터라 Task 053에서 SecurityConfig를 교체해도 영향을 받지 않는다.
 * /actuator/**(Cloudtype 헬스체크), /v3/api-docs, swagger-ui는 /api 밖이라 검사하지 않는다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20) // ForwardedHeaderFilter(MIN) → RequestIpLoggingFilter(+10) → 이 필터 → Security(-100)
public class OriginSecretFilter extends OncePerRequestFilter {

	public static final String HEADER_NAME = "x-origin-secret";

	private final boolean enabled;               // local 프로필에서만 끈다
	private final byte[] secret;                 // 기대하는 헤더 값(UTF-8)
	private final HandlerExceptionResolver handlerExceptionResolver;

	// Lombok 생성자는 @Qualifier·@Value를 옮기지 않으므로 직접 작성한다
	public OriginSecretFilter(
			@Value("${app.origin-secret.enabled}") boolean enabled,
			@Value("${app.origin-secret.value:}") String secret,
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
		if (enabled && !StringUtils.hasText(secret)) {
			// 값 없이 켜진 채로 뜨면 모든 /api 요청이 거부되므로 기동 단계에서 바로 실패시킨다
			throw new IllegalStateException("app.origin-secret.enabled가 true인데 app.origin-secret.value(ORIGIN_SECRET)가 비어 있습니다.");
		}
		this.enabled = enabled;
		this.secret = secret.getBytes(StandardCharsets.UTF_8);
		this.handlerExceptionResolver = handlerExceptionResolver;
	}

	/** 꺼져 있거나 /api 밖의 요청은 검사하지 않는다 */
	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !enabled || !ApiRequestPath.isApi(request);
	}

	/** 헤더가 없거나 다르면 403 FORBIDDEN ProblemDetail로 거부한다 */
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (matches(request.getHeader(HEADER_NAME))) {
			filterChain.doFilter(request, response);
			return;
		}
		// MVC 밖이라 @RestControllerAdvice가 직접 받지 못하므로 보안 핸들러와 같은 방식으로 위임한다(WARN 로그는 핸들러가 남긴다)
		handlerExceptionResolver.resolveException(request, response, null,
				new AccessDeniedException("오리진 비밀 헤더가 없거나 일치하지 않습니다."));
	}

	/** 타이밍 공격을 막기 위해 상수 시간으로 비교한다 */
	private boolean matches(String headerValue) {
		if (headerValue == null) {
			return false;
		}
		return MessageDigest.isEqual(secret, headerValue.getBytes(StandardCharsets.UTF_8));
	}
}
