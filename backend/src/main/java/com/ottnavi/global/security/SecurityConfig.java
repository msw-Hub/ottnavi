package com.ottnavi.global.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 임시 보안 설정: 전 경로 허용.
 * TODO Task 053에서 실제 규칙(/api/public/**·인증 경로 허용, 그 외 /api/** 인증, /api/admin/** ADMIN)으로 교체한다.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ProblemDetailAuthenticationEntryPoint authenticationEntryPoint; // 401 ProblemDetail 응답
    private final ProblemDetailAccessDeniedHandler accessDeniedHandler;           // 403 ProblemDetail 응답

    /** 세션 없이 모든 요청을 허용하는 임시 필터 체인을 등록한다 */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // TECH T-8 ②(/api/** 기본 no-store)는 Security 기본 캐시 헤더로 충족한다:
                // Cache-Control: no-cache, no-store, max-age=0, must-revalidate. 컨트롤러가 Cache-Control을 직접 넣으면 Security는 덮어쓰지 않으므로
                // 공개 조회 CDN 캐시가 필요해지면 해당 응답에서만 연다. Task 053에서 이 설정을 교체할 때 끄지 않는다(OttServiceApiTest가 검사)
                .headers(headers -> headers.cacheControl(Customizer.withDefaults()))
                // 지금은 permitAll이라 호출되지 않지만, Task 053에서 인증 규칙을 넣으면 바로 같은 오류 형식이 적용된다
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}