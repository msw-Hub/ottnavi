package com.ottnavi.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 관리자 배치 토큰 필터의 대상 판정과 토큰 비교를 검증한다.
 * resolver는 넘겨받은 예외를 기록하는 가짜 구현을 쓴다(Docker·스프링 컨텍스트 불필요).
 */
class AdminBatchTokenFilterTest {

	private static final String TOKEN = "test-batch-token";

	private final List<Exception> resolved = new ArrayList<>();
	private final HandlerExceptionResolver resolver = (request, response, handler, ex) -> {
		resolved.add(ex);
		return null;
	};
	private final AdminBatchTokenFilter filter = new AdminBatchTokenFilter(TOKEN, resolver);

	@Test
	void 토큰_설정이_비면_생성_단계에서_실패한다() {
		// given & when & then
		assertThatThrownBy(() -> new AdminBatchTokenFilter(null, resolver)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new AdminBatchTokenFilter("  ", resolver)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void 수집_경로가_아니면_검사하지_않는다() throws Exception {
		// given
		MockFilterChain chain = new MockFilterChain();

		// when
		MockHttpServletRequest publicRequest = request("/api/public/ott-services", null);
		filter.doFilter(publicRequest, new MockHttpServletResponse(), chain);
		MockFilterChain chain2 = new MockFilterChain();
		MockHttpServletRequest usersRequest = request("/api/admin/users", null);
		filter.doFilter(usersRequest, new MockHttpServletResponse(), chain2);

		// then
		assertThat(chain.getRequest()).isNotNull();
		assertThat(chain2.getRequest()).isNotNull();
		assertThat(resolved).isEmpty();
	}

	@Test
	void 올바른_토큰이면_다음_필터로_넘어간다() throws Exception {
		// given
		MockFilterChain chain = new MockFilterChain();
		MockHttpServletRequest request = request("/api/admin/collect/executions", "Bearer " + TOKEN);

		// when
		filter.doFilter(request, new MockHttpServletResponse(), chain);

		// then
		assertThat(chain.getRequest()).isNotNull();
		assertThat(resolved).isEmpty();
	}

	@Test
	void 토큰이_없거나_틀리면_401용_예외를_넘긴다() throws Exception {
		// given
		MockFilterChain chain = new MockFilterChain();

		// when
		filter.doFilter(request("/api/admin/collect/executions", null), new MockHttpServletResponse(), chain);
		filter.doFilter(request("/api/admin/collect/executions", "Bearer wrong"), new MockHttpServletResponse(), chain);
		filter.doFilter(request("/api/admin/plans/monthly", "Basic " + TOKEN), new MockHttpServletResponse(), chain);

		// then
		assertThat(chain.getRequest()).isNull();
		assertThat(resolved).hasSize(3);
		assertThat(resolved).allMatch(ex -> ex instanceof InsufficientAuthenticationException);
	}

	@Test
	void Bearer_접두사는_대소문자를_가리지_않는다() throws Exception {
		// given
		MockFilterChain chain = new MockFilterChain();

		// when
		filter.doFilter(request("/api/admin/collect/executions", "bearer " + TOKEN), new MockHttpServletResponse(), chain);

		// then
		assertThat(chain.getRequest()).isNotNull();
		assertThat(resolved).isEmpty();
	}

	@Test
	void 인코딩한_admin_경로도_검사_대상이다() throws Exception {
		// given
		MockFilterChain chain = new MockFilterChain();
		MockHttpServletRequest request = request("/api/%61dmin/collect/executions", null);

		// when
		filter.doFilter(request, new MockHttpServletResponse(), chain);

		// then
		assertThat(chain.getRequest()).isNull();
		assertThat(resolved).hasSize(1);
	}

	@Test
	void 세미콜론을_붙인_경로도_검사_대상이다() throws Exception {
		// given
		MockFilterChain chain = new MockFilterChain();

		// when
		filter.doFilter(request("/api/admin/collect;x=1/executions", null), new MockHttpServletResponse(), chain);
		filter.doFilter(request("/api/admin/collect;x=1", null), new MockHttpServletResponse(), chain);

		// then
		assertThat(chain.getRequest()).isNull();
		assertThat(resolved).hasSize(2);
	}

	@Test
	void 접두사만_같은_다른_경로는_대상이_아니다() throws Exception {
		// given
		MockFilterChain chain1 = new MockFilterChain();
		MockFilterChain chain2 = new MockFilterChain();

		// when
		filter.doFilter(request("/api/admin/collection", null), new MockHttpServletResponse(), chain1);
		filter.doFilter(request("/api/admin/plans/monthly-report", null), new MockHttpServletResponse(), chain2);

		// then
		assertThat(chain1.getRequest()).isNotNull();
		assertThat(chain2.getRequest()).isNotNull();
		assertThat(resolved).isEmpty();
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
