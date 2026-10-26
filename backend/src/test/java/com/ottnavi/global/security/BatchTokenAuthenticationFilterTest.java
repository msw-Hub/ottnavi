package com.ottnavi.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 배치 토큰 필터의 대상 판정과 토큰 비교를 검증한다(Docker·스프링 컨텍스트 불필요).
 * 맞으면 ROLE_BATCH 인증을 채우고, 틀리거나 대상이 아니면 인증을 건드리지 않고 항상 다음 필터로 넘긴다.
 */
class BatchTokenAuthenticationFilterTest {

	private static final String TOKEN = "test-batch-token";

	private final BatchTokenAuthenticationFilter filter = new BatchTokenAuthenticationFilter(TOKEN);

	@AfterEach
	void 보안_컨텍스트를_비운다() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void 토큰_설정이_비면_생성_단계에서_실패한다() {
		// given & when & then
		assertThatThrownBy(() -> new BatchTokenAuthenticationFilter(null)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new BatchTokenAuthenticationFilter("  ")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void 올바른_토큰이면_ROLE_BATCH_인증을_채우고_넘긴다() throws Exception {
		// when
		filter.doFilter(request("/api/admin/collect/executions", "Bearer " + TOKEN), new MockHttpServletResponse(), new MockFilterChain());

		// then
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		assertThat(authentication).isNotNull();
		assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_BATCH");
	}

	@Test
	void Bearer_접두사는_대소문자를_가리지_않는다() throws Exception {
		// given & when
		filter.doFilter(request("/api/admin/plans/monthly", "bearer " + TOKEN), new MockHttpServletResponse(),
				new MockFilterChain());

		// then
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
	}

	@Test
	void 토큰이_없거나_틀리면_인증을_채우지_않고_넘긴다() throws Exception {
		// when
		filter.doFilter(request("/api/admin/collect/executions", null), new MockHttpServletResponse(), new MockFilterChain());
		filter.doFilter(request("/api/admin/collect/executions", "Bearer wrong"), new MockHttpServletResponse(), new MockFilterChain());
		filter.doFilter(request("/api/admin/plans/monthly", "Basic " + TOKEN), new MockHttpServletResponse(), new MockFilterChain());

		// then
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	@Test
	void 대상_경로가_아니면_토큰이_맞아도_인증을_채우지_않는다() throws Exception {
		// when
		filter.doFilter(request("/api/admin/providers", "Bearer " + TOKEN), new MockHttpServletResponse(), new MockFilterChain());
		filter.doFilter(request("/api/admin/collection", "Bearer " + TOKEN), new MockHttpServletResponse(), new MockFilterChain());
		filter.doFilter(request("/api/admin/plans/monthly-report", "Bearer " + TOKEN), new MockHttpServletResponse(),
				new MockFilterChain());

		// then
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	@Test
	void 인코딩한_admin_경로도_대상이다() throws Exception {
		// given & when
		filter.doFilter(request("/api/%61dmin/collect/executions", "Bearer " + TOKEN), new MockHttpServletResponse(),
				new MockFilterChain());

		// then
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
	}

	@Test
	void 세미콜론을_붙인_경로도_대상이다() throws Exception {
		// given & when
		filter.doFilter(request("/api/admin/collect;x=1/executions", "Bearer " + TOKEN), new MockHttpServletResponse(),
				new MockFilterChain());

		// then
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
	}

	private MockHttpServletRequest request(String uri, String authorization) {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
		request.setRequestURI(uri);
		if (authorization != null) {
			request.addHeader("Authorization", authorization);
		}
		return request;
	}
}
