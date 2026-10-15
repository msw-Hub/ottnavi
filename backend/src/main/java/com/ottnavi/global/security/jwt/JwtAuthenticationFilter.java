package com.ottnavi.global.security.jwt;

import com.ottnavi.global.security.AuthUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorization: Bearer Access Token이 유효하면 인증을 설정한다. 무효하거나 없으면 아무것도 하지 않고 통과시킨다(익명).
 * 거부는 인가 규칙과 AuthenticationEntryPoint가 맡는다.
 * Spring Boot가 서블릿 필터로 자동 등록하지 않도록 빈이 아니라 SecurityConfig에서 new로 만든다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtTokenProvider tokenProvider;

	public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
		this.tokenProvider = tokenProvider;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		// 1. 앞선 필터(배치 토큰)가 이미 인증을 채웠으면 건너뛴다
		if (SecurityContextHolder.getContext().getAuthentication() == null) {
			// 2. Bearer 토큰이 유효할 때만 인증을 설정한다
			String authorization = request.getHeader("Authorization");
			if (authorization != null
					&& authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
				AuthUser authUser = tokenProvider.parse(authorization.substring(BEARER_PREFIX.length()).strip());
				if (authUser != null) {
					List<GrantedAuthority> authorities = List.of(
							new SimpleGrantedAuthority("ROLE_" + authUser.role().name()));
					SecurityContext context = SecurityContextHolder.createEmptyContext();
					context.setAuthentication(new UsernamePasswordAuthenticationToken(authUser, null, authorities));
					SecurityContextHolder.setContext(context);
				}
			}
		}
		// 3. 다음 필터로 넘긴다
		filterChain.doFilter(request, response);
	}
}
