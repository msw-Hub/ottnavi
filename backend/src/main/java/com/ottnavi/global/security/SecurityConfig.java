package com.ottnavi.global.security;

import com.ottnavi.global.security.jwt.JwtAuthenticationFilter;
import com.ottnavi.global.security.jwt.JwtProperties;
import com.ottnavi.global.security.jwt.JwtTokenProvider;
import com.ottnavi.global.security.oauth.CookieOAuth2AuthorizationRequestRepository;
import com.ottnavi.global.security.oauth.NoOpOAuth2AuthorizedClientRepository;
import com.ottnavi.global.security.oauth.OAuth2LoginFailureHandler;
import com.ottnavi.global.security.oauth.OAuth2LoginSuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;

/**
 * 보안 설정: 세션 없는 JWT 인증과 경로별 인가 규칙(TECH 4절, PRD R11-21).
 * 인증 필터는 배치 토큰 → JWT 순이다. 둘 다 빈으로 두지 않고 여기서 new로 체인에만 넣는다
 * (빈으로 두면 Spring Boot가 서블릿 필터로 한 번 더 등록해 두 번 돈다).
 * 구글 로그인(oauth2Login)은 경로를 /api 아래로 옮기고, 인가 요청은 서명 쿠키에 담으며, 세션을 만들지 않는다(TECH 4절).
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

	private final ProblemDetailAuthenticationEntryPoint authenticationEntryPoint; // 401 ProblemDetail 응답
	private final ProblemDetailAccessDeniedHandler accessDeniedHandler;           // 403 ProblemDetail 응답

	/** JWT 발급·검증기. 비밀 값이 약하면 여기서 기동이 실패한다 */
	@Bean
	public JwtTokenProvider jwtTokenProvider(JwtProperties properties) {
		return new JwtTokenProvider(properties);
	}

	/** 세션 없이 JWT로 인증하고, 경로 3분류에 맞춰 인가하는 필터 체인을 등록한다 */
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtTokenProvider jwtTokenProvider,
			@Value("${app.admin-batch-token}") String batchToken,
			CookieOAuth2AuthorizationRequestRepository authorizationRequestRepository,
			OAuth2LoginSuccessHandler loginSuccessHandler, OAuth2LoginFailureHandler loginFailureHandler)
			throws Exception {
		JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtTokenProvider);
		BatchTokenAuthenticationFilter batchTokenFilter = new BatchTokenAuthenticationFilter(batchToken);

		http
				.csrf(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.requestCache(cache -> cache.requestCache(new NullRequestCache()))
				// TECH T-8 ②(/api/** 기본 no-store)는 Security 기본 캐시 헤더로 충족한다:
				// Cache-Control: no-cache, no-store, max-age=0, must-revalidate. 컨트롤러가 Cache-Control을 직접 넣으면 Security는 덮어쓰지 않으므로
				// 공개 조회 CDN 캐시가 필요해지면 해당 응답에서만 연다. 이 설정을 끄지 않는다(OttServiceApiTest가 검사)
				.headers(headers -> headers.cacheControl(Customizer.withDefaults()))
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(authenticationEntryPoint)
						.accessDeniedHandler(accessDeniedHandler))
				// 구글 로그인: 경로를 /api 아래로 옮기고(TECH 4절) 인가 요청은 서명 쿠키에 둔다.
				// loginPage를 지정해 기본 로그인 페이지(/login) 생성을 막고, 구글 토큰은 저장하지 않는다(세션·메모리 미사용)
				.oauth2Login(oauth2 -> oauth2
						.loginPage("/api/oauth2/authorization/google")
						.authorizedClientRepository(new NoOpOAuth2AuthorizedClientRepository())
						.authorizationEndpoint(endpoint -> endpoint
								.baseUri("/api/oauth2/authorization")
								.authorizationRequestRepository(authorizationRequestRepository))
						.redirectionEndpoint(endpoint -> endpoint.baseUri("/api/login/oauth2/code/*"))
						.successHandler(loginSuccessHandler)
						.failureHandler(loginFailureHandler))
				// 인증 필터 순서: 배치 토큰 → JWT(배치 인증이 채워졌으면 JWT는 건너뛴다)
				.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
				.addFilterBefore(batchTokenFilter, JwtAuthenticationFilter.class)
				// 위에서부터 먼저 맞는 규칙이 적용된다
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/api/public/**", "/api/auth/**", "/api/oauth2/**", "/api/login/**").permitAll()
						// 데모 계정(DEMO): 탈퇴·알림 설정 변경은 막는다(공용 계정이라 한 사람이 지우거나 메일을 켜면 안 된다)
						.requestMatchers(HttpMethod.DELETE, "/api/me").hasAnyRole("USER", "ADMIN")
						.requestMatchers(HttpMethod.PUT, "/api/me/notification-setting").hasAnyRole("USER", "ADMIN")
						// 알림 메일 예시는 데모 계정 전용이다. 아래 /api/** authenticated() 보다 앞에 둬야 USER·ADMIN이 403이 된다
						.requestMatchers(HttpMethod.GET, "/api/me/plans/current/mail-preview").hasRole("DEMO")
						// 시스템 지표는 서버 내부 구성이라 ADMIN만 본다. 아래 GET /api/admin/** (ADMIN·DEMO)보다 앞에 둬야 데모 계정이 403이 된다
						.requestMatchers(HttpMethod.GET, "/api/admin/system-metrics").hasRole("ADMIN")
						// 데모 계정은 관리자 화면을 조회(GET)만 한다. 배치 토큰의 GET은 기존대로 배치 경로에서만 통과한다.
						// HEAD·OPTIONS는 GET 매처에 걸리지 않아 아래 기존 ADMIN 규칙으로 간다
						.requestMatchers(HttpMethod.GET, "/api/admin/collect/**", "/api/admin/plans/monthly/**",
								"/api/admin/retention/**", "/api/admin/popularity/**")
								.hasAnyRole("ADMIN", "BATCH", "DEMO")
						.requestMatchers(HttpMethod.GET, "/api/admin/**").hasAnyRole("ADMIN", "DEMO")
						// 이 아래는 쓰기·실행(POST·PATCH·DELETE 등)이라 데모 계정은 403이다
						.requestMatchers("/api/admin/collect/**", "/api/admin/plans/monthly/**",
								"/api/admin/retention/**", "/api/admin/popularity/**")
								.hasAnyRole("ADMIN", "BATCH")
						.requestMatchers("/api/admin/**").hasRole("ADMIN")
						.requestMatchers("/api/**").authenticated()
						.requestMatchers("/actuator/health/**", "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml",
								"/swagger-ui/**", "/swagger-ui.html", "/error").permitAll()
						.anyRequest().denyAll());
		return http.build();
	}
}
