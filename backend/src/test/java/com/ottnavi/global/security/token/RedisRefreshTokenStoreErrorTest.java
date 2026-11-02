package com.ottnavi.global.security.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ottnavi.global.error.AuthStoreUnavailableException;
import com.ottnavi.global.security.jwt.JwtProperties;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.dao.QueryTimeoutException;

/**
 * Redis가 끊기거나 시간 초과가 나면 저장소가 AuthStoreUnavailableException으로 바꿔 던지는지 확인한다(Redis 없이 목으로).
 * 막으려는 사고: Redis 장애가 그대로 500이 되는 일, 원인 예외가 사라져 추적이 안 되는 일.
 */
class RedisRefreshTokenStoreErrorTest {

	// 형식만 맞는 토큰(familyId 22자, secret 43자). Redis에 닿기 전 형식 검사를 통과시키려는 용도다
	private static final String TOKEN = "A".repeat(22) + "." + "B".repeat(43);

	private StringRedisTemplate redis;
	private RedisRefreshTokenStore store;

	@BeforeEach
	void setUp() {
		redis = mock(StringRedisTemplate.class);
		store = new RedisRefreshTokenStore(redis, new JwtProperties("unused", Duration.ofMinutes(30), "ottnavi"));
	}

	@Test
	void 발급_중_Redis_연결_실패는_AuthStoreUnavailableException이다() {
		// given
		when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
				.thenThrow(new RedisConnectionFailureException("연결 실패"));

		// when & then
		assertThatThrownBy(() -> store.issue(1L))
				.isInstanceOf(AuthStoreUnavailableException.class)
				.hasCauseInstanceOf(RedisConnectionFailureException.class);
	}

	@Test
	void 교체_중_Redis_시간_초과는_AuthStoreUnavailableException이다() {
		// given
		when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
				.thenThrow(new QueryTimeoutException("시간 초과"));

		// when & then
		assertThatThrownBy(() -> store.rotate(TOKEN))
				.isInstanceOf(AuthStoreUnavailableException.class)
				.hasCauseInstanceOf(QueryTimeoutException.class);
	}

	@Test
	void 폐기_중_Redis_오류는_AuthStoreUnavailableException이다() {
		// given
		when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
				.thenThrow(new RedisConnectionFailureException("연결 실패"));

		// when & then
		assertThatThrownBy(() -> store.revoke(TOKEN)).isInstanceOf(AuthStoreUnavailableException.class);
	}

	@Test
	@SuppressWarnings("unchecked")
	void 사용자_전체_폐기_중_Redis_오류는_AuthStoreUnavailableException이다() {
		// given
		SetOperations<String, String> setOperations = mock(SetOperations.class);
		when(redis.opsForSet()).thenReturn(setOperations);
		when(setOperations.members(anyString())).thenThrow(new RedisConnectionFailureException("연결 실패"));

		// when & then
		assertThatThrownBy(() -> store.revokeAllOf(1L)).isInstanceOf(AuthStoreUnavailableException.class);
	}

	@Test
	void 형식이_틀린_토큰은_Redis를_부르지_않고_예외도_없다() {
		// when
		RefreshRotation rotation = store.rotate("형식이-틀림");

		// then
		assertThat(rotation.result()).isEqualTo(RefreshRotation.Result.NOT_FOUND);
	}
}
