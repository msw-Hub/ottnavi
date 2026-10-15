package com.ottnavi.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/**
 * GitHub Actions cron이 관리자 JWT 없이 호출하는 배치 경로에서 정적 배치 토큰(Authorization: Bearer)을 확인하고,
 * 맞으면 ROLE_BATCH 인증을 설정한다. 틀리거나 없으면 아무것도 하지 않고 다음 필터로 넘긴다(그러면 JWT 필터와
 * 인가 규칙이 판정하므로 ADMIN JWT와 OR 관계가 된다). 토큰 값은 로그에 남기지 않는다.
 * 대상은 /api/admin/collect, /api/admin/plans/monthly와 그 하위 경로뿐이다.
 * Spring Boot가 서블릿 필터로 자동 등록하지 않도록 빈이 아니라 SecurityConfig에서 new로 만든다.
 */
public class BatchTokenAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";
	private static final String[] PROTECTED_PATHS = {"/api/admin/collect", "/api/admin/plans/monthly"};

	private final byte[] token; // 기대하는 토큰 값(UTF-8)

	public BatchTokenAuthenticationFilter(String token) {
		if (!StringUtils.hasText(token)) {
			// 빈 토큰이 통과할 수 있어 기동 단계에서 바로 실패시킨다
			throw new IllegalStateException("app.admin-batch-token(ADMIN_BATCH_TOKEN)이 비어 있습니다.");
		}
		this.token = token.getBytes(StandardCharsets.UTF_8);
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

	/** 토큰이 맞으면 ROLE_BATCH 인증을 설정하고, 아니면 아무것도 하지 않고 넘긴다 */
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		// 1. 토큰이 일치할 때만 배치 인증을 채운다
		if (matches(request.getHeader("Authorization"))) {
			List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_BATCH"));
			SecurityContext context = SecurityContextHolder.createEmptyContext();
			context.setAuthentication(new UsernamePasswordAuthenticationToken("batch", null, authorities));
			SecurityContextHolder.setContext(context);
		}
		// 2. 일치 여부와 무관하게 다음 필터로 넘긴다(거부는 JWT 필터 뒤 인가 규칙이 맡는다)
		filterChain.doFilter(request, response);
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
