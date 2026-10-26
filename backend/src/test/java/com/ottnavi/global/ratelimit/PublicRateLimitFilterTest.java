package com.ottnavi.global.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.global.error.RateLimitExceededException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 요청 제한 필터의 대상 경로 판정과 거부 동작을 검증한다(Docker·스프링 컨텍스트 불필요).
 * 한도는 통 크기 1, 한 시간에 1개 충전이라 같은 IP의 두 번째 요청부터 거부된다.
 * resolver는 넘겨받은 예외를 기록하는 가짜 구현이다.
 */
class PublicRateLimitFilterTest {

	private final List<Exception> resolved = new ArrayList<>();
	private final HandlerExceptionResolver resolver = (request, response, handler, ex) -> {
		resolved.add(ex);
		return null;
	};
	private final InMemoryBucketProvider provider = new InMemoryBucketProvider();
	private final PublicRateLimiter limiter = new PublicRateLimiter(provider,
			new RateLimitProperties.Limit(1, 1, Duration.ofHours(1)), new SimpleMeterRegistry());
	private final PublicRateLimitFilter filter = new PublicRateLimitFilter(limiter, resolver);

	@Test
	void 공개_경로와_인증_경로는_한도를_넘으면_거부한다() throws Exception {
		// given
		String[] targetUris = {
				"/api/public/ott-services", "/api/auth/refresh", "/api/oauth2/authorization/google",
				"/api/login/oauth2/code/google"};

		for (String uri : targetUris) {
			resolved.clear();

			// when: 같은 IP로 두 번 호출한다(통 크기 1)
			boolean firstPassed = pass(request(uri, "10.0.0.1"));
			boolean secondPassed = pass(request(uri, "10.0.0.1"));

			// then
			assertThat(firstPassed).as(uri + " 첫 요청").isTrue();
			assertThat(secondPassed).as(uri + " 두 번째 요청").isFalse();
			assertThat(resolved).hasSize(1);
			assertThat(resolved.get(0)).isInstanceOf(RateLimitExceededException.class);

			// 다음 경로도 같은 IP로 처음부터 세도록 버킷을 비운다
			provider.clear();
		}
	}

	@Test
	void 대상이_아닌_경로는_한도를_세지_않는다() throws Exception {
		// given
		String[] otherUris = {
				"/api/me/wishlist", "/api/admin/collect/executions", "/api/wishlist", "/actuator/health",
				"/api/publicity", "/apis/public/x", "/other"};

		// when & then: 같은 IP로 여러 번 불러도 모두 통과하고 버킷도 만들지 않는다
		for (String uri : otherUris) {
			assertThat(pass(request(uri, "10.0.0.2"))).as(uri + " 첫 요청").isTrue();
			assertThat(pass(request(uri, "10.0.0.2"))).as(uri + " 두 번째 요청").isTrue();
		}
		assertThat(resolved).isEmpty();
		assertThat(provider.callCount).isZero();
	}

	@Test
	void 인코딩하거나_세미콜론을_붙인_경로도_검사_대상이다() throws Exception {
		// given
		String[] disguisedUris = {"/%61pi/public/ott-services", "/api;x=1/public/ott-services", "/api/%70ublic/ott-services"};

		for (String uri : disguisedUris) {
			resolved.clear();
			provider.clear();

			// when
			pass(request(uri, "10.0.0.3"));
			boolean secondPassed = pass(request(uri, "10.0.0.3"));

			// then
			assertThat(secondPassed).as(uri).isFalse();
			assertThat(resolved).hasSize(1);
		}
	}

	@Test
	void IP가_다르면_따로_센다() throws Exception {
		// given
		pass(request("/api/public/ott-services", "10.0.0.4"));

		// when
		boolean otherIpPassed = pass(request("/api/public/ott-services", "10.0.0.5"));

		// then
		assertThat(otherIpPassed).isTrue();
		assertThat(resolved).isEmpty();
	}

	@Test
	void 프록시_헤더_값을_바꿔도_remoteAddr이_같으면_같은_방문자로_센다() throws Exception {
		// given: 필터는 헤더를 직접 읽지 않는다(헤더 반영은 ForwardedHeaderFilter가 remoteAddr에 한다)
		MockHttpServletRequest first = request("/api/public/ott-services", "10.0.0.6");
		first.addHeader("X-Forwarded-For", "1.1.1.1");
		first.addHeader("x-real-ip", "2.2.2.2");
		MockHttpServletRequest second = request("/api/public/ott-services", "10.0.0.6");
		second.addHeader("X-Forwarded-For", "3.3.3.3");
		second.addHeader("x-real-ip", "4.4.4.4");

		// when
		pass(first);
		boolean secondPassed = pass(second);

		// then
		assertThat(secondPassed).isFalse();
		assertThat(provider.hasKey("rl:public:10.0.0.6")).isTrue();
		assertThat(provider.hasKey("rl:public:1.1.1.1")).isFalse();
	}

	/** 필터를 통과시켜 다음 필터(체인)까지 도달했는지 돌려준다 */
	private boolean pass(MockHttpServletRequest request) throws Exception {
		MockFilterChain chain = new MockFilterChain();
		filter.doFilter(request, new MockHttpServletResponse(), chain);
		return chain.getRequest() != null;
	}

	private MockHttpServletRequest request(String uri, String remoteAddr) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
		request.setRequestURI(uri);
		request.setRemoteAddr(remoteAddr);
		return request;
	}
}
