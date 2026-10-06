package com.ottnavi.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ottnavi.global.common.CommonResponse;
import com.ottnavi.global.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 보안 필터의 401·403이 ProblemDetail + errorCode로 오는지 검증한다.
 * 운영 체인은 아직 permitAll(Task 053 전)이라, 같은 엔트리포인트·거부 핸들러를 건 테스트 전용 체인을 /test/security/** 에만 적용한다.
 */
@WebMvcTest(controllers = SecurityErrorHandlerTest.SecuredTestController.class)
@Import({SecurityConfig.class, ProblemDetailAuthenticationEntryPoint.class, ProblemDetailAccessDeniedHandler.class,
		GlobalExceptionHandler.class, SecurityErrorHandlerTest.TestSecurityChain.class,
		SecurityErrorHandlerTest.SecuredTestController.class})
class SecurityErrorHandlerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void 인증_없이_보호_경로를_호출하면_401_UNAUTHORIZED다() throws Exception {
		mockMvc.perform(get("/test/security/member"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.instance").value("/test/security/member"));
	}

	@Test
	@WithMockUser(roles = "USER")
	void 일반_사용자가_관리자_경로를_호출하면_403_FORBIDDEN이다() throws Exception {
		mockMvc.perform(get("/test/security/admin"))
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
	}

	@Test
	@WithMockUser(roles = "USER")
	void 인증된_사용자는_보호_경로를_정상_호출한다() throws Exception {
		mockMvc.perform(get("/test/security/member"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));
	}

	@TestConfiguration
	static class TestSecurityChain {

		/** /test/security/** 에만 인증·인가 규칙을 거는 테스트 전용 체인(운영 체인보다 먼저 적용) */
		@Bean
		@Order(0)
		SecurityFilterChain testSecurityChain(HttpSecurity http,
				ProblemDetailAuthenticationEntryPoint authenticationEntryPoint,
				ProblemDetailAccessDeniedHandler accessDeniedHandler) throws Exception {
			http
					.securityMatcher("/test/security/**")
					.csrf(AbstractHttpConfigurer::disable)
					.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
					.exceptionHandling(exceptions -> exceptions
							.authenticationEntryPoint(authenticationEntryPoint)
							.accessDeniedHandler(accessDeniedHandler))
					.authorizeHttpRequests(auth -> auth
							.requestMatchers("/test/security/admin").hasRole("ADMIN")
							.anyRequest().authenticated());
			return http.build();
		}
	}

	@RestController
	static class SecuredTestController {

		@GetMapping("/test/security/member")
		CommonResponse<String> member() {
			return CommonResponse.success("member");
		}

		@GetMapping("/test/security/admin")
		CommonResponse<String> admin() {
			return CommonResponse.success("admin");
		}
	}
}
