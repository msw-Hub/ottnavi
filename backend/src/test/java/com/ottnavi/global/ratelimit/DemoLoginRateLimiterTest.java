package com.ottnavi.global.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ottnavi.global.error.ErrorCode;
import com.ottnavi.global.error.RateLimitExceededException;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * 데모 로그인 버킷의 한도 판정과 fail-closed를 검증한다. 메모리 버킷을 쓰므로 Docker·스프링 컨텍스트가 필요 없다.
 * 한도: 통 크기 2, 15분에 2개 충전(운영은 5개이고, 여기서는 소진을 빨리 만들려고 줄였다).
 *
 * | 확인하는 것 | 막으려는 사고 |
 * | 통을 다 쓰면 429와 Retry-After | 비밀번호를 한도 없이 맞혀 보는 것 |
 * | 저장소 오류면 통과시키지 않고 거부, 잠시 저장소를 다시 부르지 않음 | Redis가 죽었을 때 한도가 풀려 비밀번호를 무제한 시도하는 것 |
 * | 거부 카운터는 bucket=demo_login만 늘어남 | 일반·단건 수집 지표와 섞여 원인을 못 찾는 것 |
 */
class DemoLoginRateLimiterTest {

	private static final RateLimitProperties.Limit LIMIT = new RateLimitProperties.Limit(2, 2, Duration.ofMinutes(15));

	private final InMemoryBucketProvider provider = new InMemoryBucketProvider();
	private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
	private final DemoLoginRateLimiter limiter = new DemoLoginRateLimiter(provider, LIMIT, meterRegistry);

	@Test
	void 통을_다_쓰면_다음_시도는_RATE_LIMIT_EXCEEDED이고_rl_demo_키를_쓴다() {
		// given
		limiter.check("1.1.1.1");
		limiter.check("1.1.1.1");

		// when & then
		assertThatThrownBy(() -> limiter.check("1.1.1.1"))
				.isInstanceOfSatisfying(RateLimitExceededException.class, ex -> {
					assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);
					assertThat(ex.getRetryAfterSeconds()).isPositive();
				});
		assertThat(provider.hasKey("rl:demo:1.1.1.1")).isTrue();
		assertThat(provider.hasKey("rl:public:1.1.1.1")).isFalse(); // 일반 버킷과 키가 겹치지 않는다
	}

	@Test
	void IP가_다르면_한도를_따로_센다() {
		// given
		limiter.check("1.1.1.1");
		limiter.check("1.1.1.1");

		// when & then
		assertThatCode(() -> limiter.check("2.2.2.2")).doesNotThrowAnyException();
	}

	@Test
	void 저장소_오류가_나면_통과시키지_않고_거부하며_잠시_저장소를_다시_부르지_않는다() {
		// given
		FailingProvider failing = new FailingProvider();
		DemoLoginRateLimiter failClosedLimiter = new DemoLoginRateLimiter(failing, LIMIT, meterRegistry);

		// when & then: 첫 호출(오류 발생)과 둘째 호출(건너뜀 구간) 모두 거부된다
		assertThatThrownBy(() -> failClosedLimiter.check("1.1.1.1"))
				.isInstanceOfSatisfying(RateLimitExceededException.class, ex -> {
					assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);
					assertThat(ex.getRetryAfterSeconds()).isEqualTo(5);
				});
		assertThatThrownBy(() -> failClosedLimiter.check("1.1.1.1")).isInstanceOf(RateLimitExceededException.class);
		assertThat(failing.callCount).isEqualTo(1);
	}

	@Test
	void 한도_초과_거부만_bucket_demo_login_카운터를_늘린다() {
		// given
		limiter.check("1.1.1.1");
		limiter.check("1.1.1.1");

		// when
		assertThatThrownBy(() -> limiter.check("1.1.1.1")).isInstanceOf(RateLimitExceededException.class);

		// then
		Counter demo = meterRegistry.find("ratelimit.rejected").tag("bucket", "demo_login").counter();
		assertThat(demo).isNotNull();
		assertThat(demo.count()).isEqualTo(1.0);
		assertThat(meterRegistry.find("ratelimit.rejected").tag("bucket", "public").counter()).isNull();
	}

	@Test
	void 저장소_오류_거부는_한도_초과_카운터를_늘리지_않는다() {
		// given
		DemoLoginRateLimiter failClosedLimiter = new DemoLoginRateLimiter(new FailingProvider(), LIMIT, meterRegistry);

		// when
		assertThatThrownBy(() -> failClosedLimiter.check("1.1.1.1")).isInstanceOf(RateLimitExceededException.class);

		// then
		assertThat(meterRegistry.find("ratelimit.rejected").counter()).isNull();
	}

	/** 항상 저장소 오류를 던지는 창구 */
	private static class FailingProvider implements RateLimitBucketProvider {

		int callCount;

		@Override
		public Bucket getBucket(String key, BucketConfiguration configuration) {
			callCount++;
			throw new IllegalStateException("Redis 연결 실패(테스트)");
		}
	}
}
