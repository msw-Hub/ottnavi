package com.ottnavi.global.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.global.security.AuthUser;
import com.ottnavi.user.domain.enums.UserRole;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

/** JWT 필터가 유효한 토큰만 인증으로 바꾸고, 그 밖에는 익명으로 통과시키는지 확인한다 */
class JwtAuthenticationFilterTest {

	private final JwtTokenProvider provider = new JwtTokenProvider(new JwtProperties(
			Base64.getEncoder().encodeToString("k".repeat(32).getBytes()), Duration.ofMinutes(30), "ottnavi"));
	private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(provider);

	@AfterEach
	void 보안_컨텍스트를_비운다() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void 유효한_토큰이면_사용자와_권한으로_인증한다() throws Exception {
		// given
		String token = provider.issueAccessToken(7L, UserRole.ADMIN);
		MockHttpServletRequest request = request("Bearer " + token);
		MockFilterChain chain = new MockFilterChain();

		// when
		filter.doFilter(request, new MockHttpServletResponse(), chain);

		// then
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		assertThat(authentication.getPrincipal()).isEqualTo(new AuthUser(7L, UserRole.ADMIN));
		assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
		assertThat(chain.getRequest()).isNotNull();
	}

	@Test
	void 토큰이_없거나_무효하면_인증_없이_통과한다() throws Exception {
		// given
		MockFilterChain noHeader = new MockFilterChain();
		MockFilterChain invalid = new MockFilterChain();
		MockFilterChain basic = new MockFilterChain();

		// when
		filter.doFilter(request(null), new MockHttpServletResponse(), noHeader);
		filter.doFilter(request("Bearer not-a-jwt"), new MockHttpServletResponse(), invalid);
		filter.doFilter(request("Basic abc"), new MockHttpServletResponse(), basic);

		// then
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		assertThat(noHeader.getRequest()).isNotNull();
		assertThat(invalid.getRequest()).isNotNull();
		assertThat(basic.getRequest()).isNotNull();
	}

	@Test
	void 이미_인증이_채워져_있으면_덮어쓰지_않는다() throws Exception {
		// given: 배치 토큰 필터가 먼저 채운 상황
		Authentication batch = new UsernamePasswordAuthenticationToken("batch", null,
				AuthorityUtils.createAuthorityList("ROLE_BATCH"));
		SecurityContextHolder.getContext().setAuthentication(batch);
		String token = provider.issueAccessToken(7L, UserRole.USER);

		// when
		filter.doFilter(request("Bearer " + token), new MockHttpServletResponse(), new MockFilterChain());

		// then
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(batch);
	}

	private MockHttpServletRequest request(String authorization) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me/settings");
		if (authorization != null) {
			request.addHeader("Authorization", authorization);
		}
		return request;
	}
}
