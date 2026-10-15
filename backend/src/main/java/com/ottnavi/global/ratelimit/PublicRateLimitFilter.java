package com.ottnavi.global.ratelimit;

import com.ottnavi.global.error.RateLimitExceededException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.util.UrlPathHelper;

/**
 * 비로그인 공개 경로(/api/public/**)와 인증 경로(/api/auth/**, /api/oauth2/**, /api/login/**)에 IP별 요청 제한을 건다.
 * 인증 경로를 포함하는 이유는 로그인 시도·재발급을 반복해 서버를 괴롭히는 것을 막기 위해서다(backend.md 컨트롤러 규칙).
 * 로그인해야 하는 /api/**, 관리자 /api/admin/**, /api 밖(/actuator 등)은 검사하지 않는다.
 *
 * Security 체인 앞의 서블릿 필터라 Task 053의 SecurityConfig 교체에 영향을 받지 않는다.
 * 빈은 RateLimitConfig가 만든다(@Component로 두면 @WebMvcTest 슬라이스가 이 필터를 끌어와 의존 빈이 없어 실패한다).
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 25) // OriginSecretFilter(+20) → 이 필터 → AdminBatchTokenFilter(+30) → Security(-100)
public class PublicRateLimitFilter extends OncePerRequestFilter {

	// 검사 대상 경로 접두사. 마지막 / 없이 두고 "정확히 같거나 / 아래"만 대상으로 본다(/api/publicity 같은 경로 제외)
	private static final String[] TARGET_PATHS = {"/api/public", "/api/auth", "/api/oauth2", "/api/login"};

	private final PublicRateLimiter rateLimiter;
	private final HandlerExceptionResolver handlerExceptionResolver;

	public PublicRateLimitFilter(PublicRateLimiter rateLimiter, HandlerExceptionResolver handlerExceptionResolver) {
		this.rateLimiter = rateLimiter;
		this.handlerExceptionResolver = handlerExceptionResolver;
	}

	/** 대상 경로가 아니면 검사하지 않는다 */
	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		// 경로 판정은 global.security.ApiRequestPath와 같은 방식(MVC와 같은 디코딩·정규화)을 쓴다.
		// 그 클래스는 security 패키지 전용(package-private)이라 가져다 쓰지 않고, 같은 한 줄을 여기에 둔다
		// (AdminBatchTokenFilter도 같은 방식). 원본 URI로 비교하면 /%61pi/public/... 으로 우회할 수 있다(CWE-647)
		String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
		for (String targetPath : TARGET_PATHS) {
			if (path.equals(targetPath) || path.startsWith(targetPath + "/")) {
				return false;
			}
		}
		return true;
	}

	/** 한도를 넘으면 429 RATE_LIMIT_EXCEEDED ProblemDetail로 거부한다 */
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		// 1. 방문자 IP로 토큰 한 개를 쓴다. 키는 remoteAddr뿐이다(x-real-ip는 Vercel IP라 쓰지 않고,
		//    X-Forwarded-For는 ForwardedHeaderFilter가 remoteAddr에 이미 반영했다. TECH 4절)
		try {
			rateLimiter.check(request.getRemoteAddr());
		} catch (RateLimitExceededException e) {
			// 2. 한도 초과는 MVC 밖이라 @RestControllerAdvice가 직접 받지 못하므로 다른 보안 필터와 같은 방식으로 위임한다.
			//    GlobalExceptionHandler가 429 ProblemDetail과 Retry-After 헤더를 만든다
			handlerExceptionResolver.resolveException(request, response, null, e);
			return;
		}
		// 3. 한도 안이면 다음 필터로 넘긴다
		filterChain.doFilter(request, response);
	}
}
