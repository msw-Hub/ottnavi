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
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.util.UrlPathHelper;

/**
 * 수집 실행·월간 플랜 배치 같은 관리자 배치 경로를 정적 배치 토큰(Authorization: Bearer)으로 보호한다.
 * GitHub Actions cron이 관리자 JWT 없이 호출할 수 있게 하는 임시 장치다.
 * Task 054에서 Security 체인 안으로 옮기고 ADMIN JWT와 OR로 합친다.
 * 토큰 값은 로그에 남기지 않는다.
 * 검사 대상은 /api/admin/collect, /api/admin/plans/monthly와 그 하위 경로뿐이다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30) // OriginSecretFilter(+20) → 이 필터 → Security(-100)
public class AdminBatchTokenFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";
	private static final String[] PROTECTED_PATHS = {"/api/admin/collect", "/api/admin/plans/monthly"};

	private final byte[] token;                  // 기대하는 토큰 값(UTF-8)
	private final HandlerExceptionResolver handlerExceptionResolver;

	// Lombok 생성자는 @Qualifier·@Value를 옮기지 않으므로 직접 작성한다
	public AdminBatchTokenFilter(
			@Value("${app.admin-batch-token}") String token,
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
		if (!StringUtils.hasText(token)) {
			// 값 없이 뜨면 배치 경로가 모두 막히거나(또는 빈 토큰이 통과할 수 있어) 기동 단계에서 바로 실패시킨다
			throw new IllegalStateException("app.admin-batch-token(ADMIN_BATCH_TOKEN)이 비어 있습니다.");
		}
		this.token = token.getBytes(StandardCharsets.UTF_8);
		this.handlerExceptionResolver = handlerExceptionResolver;
	}

	/** 보호 대상 경로(정확히 일치하거나 / 아래 하위 경로)가 아니면 검사하지 않는다 */
	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		// 1. MVC와 같은 방식으로 디코딩·세미콜론·중복 슬래시를 정리한 경로를 얻는다
		String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
		// 2. 보호 경로와 정확히 같거나 그 아래 경로면 검사 대상이다(/api/admin/collection 같은 접두사만 같은 경로는 제외)
		for (String protectedPath : PROTECTED_PATHS) {
			if (path.equals(protectedPath) || path.startsWith(protectedPath + "/")) {
				return false;
			}
		}
		return true;
	}

	/** 토큰이 없거나 다르면 401 UNAUTHORIZED ProblemDetail로 거부한다 */
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		// 1. 토큰이 일치하면 다음 필터로 넘긴다
		if (matches(request.getHeader("Authorization"))) {
			filterChain.doFilter(request, response);
			return;
		}
		// 2. 불일치는 보안 핸들러와 같은 방식으로 위임한다(WARN 로그는 핸들러가 남기고 토큰 값은 남기지 않는다)
		handlerExceptionResolver.resolveException(request, response, null,
				new InsufficientAuthenticationException("관리자 배치 토큰이 없거나 일치하지 않습니다."));
	}

	/** Bearer 접두사(대소문자 무시)를 떼고 상수 시간으로 비교한다 */
	private boolean matches(String authorization) {
		if (authorization == null) {
			return false;
		}
		if (!authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
			return false;
		}
		String presented = authorization.substring(BEARER_PREFIX.length());
		return MessageDigest.isEqual(token, presented.getBytes(StandardCharsets.UTF_8));
	}
}
