package com.ottnavi.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ottnavi.global.ratelimit.RedisRateLimitBucketProvider;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/** RedisWarmUp 검증: 성공, 실패 후 실패한 쪽만 재시도, 재시도도 실패해도 예외가 안 나감, 설정으로 끄면 빈이 없음 */
class RedisWarmUpTest {

	// 테스트가 오래 기다리지 않도록 재시도 대기를 1ms로 준다
	private static final RedisClientProperties FAST_RETRY =
			new RedisClientProperties(new RedisClientProperties.WarmUp(true, Duration.ofMillis(1)));

	@SuppressWarnings("unchecked")
	private RedisWarmUp create(RedisRateLimitBucketProvider provider, RedisConnectionFactory factory) {
		ObjectProvider<RedisRateLimitBucketProvider> objectProvider = mock(ObjectProvider.class);
		when(objectProvider.getIfAvailable()).thenReturn(provider);
		return new RedisWarmUp(objectProvider, factory, FAST_RETRY);
	}

	@Test
	void 연결이_되면_요청_제한_연결과_PING을_한_번씩만_실행하고_재시도하지_않는다() {
		RedisRateLimitBucketProvider provider = mock(RedisRateLimitBucketProvider.class);
		RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
		RedisConnection connection = mock(RedisConnection.class);
		when(factory.getConnection()).thenReturn(connection);

		assertThatCode(() -> create(provider, factory).warmUp()).doesNotThrowAnyException();

		verify(provider, times(1)).warmUp();
		verify(connection, times(1)).ping();
		verify(connection).close();
	}

	@Test
	void 요청_제한_연결만_실패하면_그쪽만_다시_시도한다() {
		RedisRateLimitBucketProvider provider = mock(RedisRateLimitBucketProvider.class);
		doThrow(new IllegalStateException("연결 실패")).doNothing().when(provider).warmUp(); // 첫 번째만 실패
		RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
		RedisConnection connection = mock(RedisConnection.class);
		when(factory.getConnection()).thenReturn(connection);

		create(provider, factory).warmUp();

		verify(provider, times(2)).warmUp();
		verify(connection, times(1)).ping(); // 성공한 쪽은 다시 하지 않는다
	}

	@Test
	void PING만_실패하면_PING만_다시_시도한다() {
		RedisRateLimitBucketProvider provider = mock(RedisRateLimitBucketProvider.class);
		RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
		RedisConnection connection = mock(RedisConnection.class);
		when(factory.getConnection())
				.thenThrow(new RedisConnectionFailureException("Unable to connect"))
				.thenReturn(connection);

		create(provider, factory).warmUp();

		verify(provider, times(1)).warmUp();
		verify(factory, times(2)).getConnection();
		verify(connection).ping();
	}

	@Test
	void 재시도도_실패해도_예외가_밖으로_나가지_않고_더_반복하지_않는다() {
		RedisRateLimitBucketProvider provider = mock(RedisRateLimitBucketProvider.class);
		doThrow(new IllegalStateException("연결 실패")).when(provider).warmUp();
		RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
		when(factory.getConnection()).thenThrow(new RedisConnectionFailureException("Unable to connect"));

		assertThatCode(() -> create(provider, factory).warmUp()).doesNotThrowAnyException();

		verify(provider, times(2)).warmUp(); // 첫 시도 + 재시도 1회
		verify(factory, times(2)).getConnection();
	}

	@Test
	void 요청_제한이_꺼져_제공자가_없어도_PING만_실행한다() {
		RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
		RedisConnection connection = mock(RedisConnection.class);
		when(factory.getConnection()).thenReturn(connection);

		create(null, factory).warmUp();

		verify(connection).ping();
	}

	@Test
	void 대기_중_인터럽트가_오면_재시도하지_않고_조용히_끝난다() {
		RedisRateLimitBucketProvider provider = mock(RedisRateLimitBucketProvider.class);
		doThrow(new IllegalStateException("연결 실패")).when(provider).warmUp();
		RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
		when(factory.getConnection()).thenReturn(mock(RedisConnection.class));

		Thread.currentThread().interrupt(); // sleep이 곧바로 InterruptedException을 던진다
		try {
			assertThatCode(() -> create(provider, factory).warmUp()).doesNotThrowAnyException();
			assertThat(Thread.currentThread().isInterrupted()).isTrue(); // 인터럽트 표시를 되살려 둔다
		} finally {
			Thread.interrupted(); // 다른 테스트에 번지지 않게 표시를 지운다
		}

		verify(provider, times(1)).warmUp();
	}

	@Test
	void 설정으로_끄면_빈이_만들어지지_않고_켜면_만들어진다() {
		ApplicationContextRunner runner = new ApplicationContextRunner()
				.withUserConfiguration(MockedDependencies.class, RedisWarmUp.class)
				.withBean(RedisClientProperties.class, () -> FAST_RETRY);

		runner.withPropertyValues("app.redis.warm-up.enabled=false")
				.run(context -> assertThat(context).doesNotHaveBean(RedisWarmUp.class));
		runner.run(context -> assertThat(context).hasSingleBean(RedisWarmUp.class)); // 기본은 켜짐
		verify(MockedDependencies.FACTORY, never()).getConnection(); // 빈을 만든 것만으로 연결하지 않는다(ApplicationReadyEvent 때만)
	}

	@Configuration
	static class MockedDependencies {

		static final RedisConnectionFactory FACTORY = mock(RedisConnectionFactory.class);

		@Bean
		RedisConnectionFactory redisConnectionFactory() {
			return FACTORY;
		}
	}
}
