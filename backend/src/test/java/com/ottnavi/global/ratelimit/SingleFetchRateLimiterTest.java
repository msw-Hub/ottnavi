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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * 단건 수집 버킷의 한도 판정과 fail-closed를 검증한다. 메모리 버킷을 쓰므로 Docker·스프링 컨텍스트가 필요 없다.
 * 한도: 통 크기 2, 1분에 60개 충전(= 1초에 1개).
 */
@ExtendWith(OutputCaptureExtension.class)
class SingleFetchRateLimiterTest {

	private static final RateLimitProperties.Limit LIMIT = new RateLimitProperties.Limit(2, 60, Duration.ofMinutes(1));

	private final InMemoryBucketProvider provider = new InMemoryBucketProvider();
	private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
	private final SingleFetchRateLimiter limiter = new SingleFetchRateLimiter(provider, LIMIT, meterRegistry);

	@Test
	void 통을_다_쓰면_다음_요청은_RATE_LIMIT_EXCEEDED이고_rl_fetch_키를_쓴다() {
		// given
		limiter.check("1.1.1.1");
		limiter.check("1.1.1.1");

		// when & then
		assertThatThrownBy(() -> limiter.check("1.1.1.1"))
				.isInstanceOfSatisfying(RateLimitExceededException.class, ex -> {
					assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);
					assertThat(ex.getRetryAfterSeconds()).isEqualTo(1);
				});
		assertThat(provider.hasKey("rl:fetch:1.1.1.1")).isTrue();
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
		SingleFetchRateLimiter failClosedLimiter = new SingleFetchRateLimiter(failing, LIMIT, meterRegistry);

		// when & then: 첫 호출(오류 발생)과 둘째 호출(건너뜀 구간) 모두 거부된다
		assertThatThrownBy(() -> failClosedLimiter.check("1.1.1.1"))
				.isInstanceOfSatisfying(RateLimitExceededException.class,
						ex -> assertThat(ex.getRetryAfterSeconds()).isEqualTo(5));
		assertThatThrownBy(() -> failClosedLimiter.check("1.1.1.1")).isInstanceOf(RateLimitExceededException.class);
		assertThat(failing.callCount).isEqualTo(1);
	}

	@Test
	void 한도_초과_거부만_bucket_single_fetch_카운터를_늘린다() {
		// given
		limiter.check("1.1.1.1");
		limiter.check("1.1.1.1");

		// when
		assertThatThrownBy(() -> limiter.check("1.1.1.1")).isInstanceOf(RateLimitExceededException.class);

		// then
		Counter fetch = meterRegistry.find("ratelimit.rejected").tag("bucket", "single_fetch").counter();
		assertThat(fetch).isNotNull();
		assertThat(fetch.count()).isEqualTo(1.0);
		assertThat(meterRegistry.find("ratelimit.rejected").tag("bucket", "public").counter()).isNull();
	}

	/** 항상 저장소 오류를 던지는 창구 */
	@Test
	void 저장소_오류_경고_로그에_근본_원인_클래스가_남는다(CapturedOutput output) {
		// given
		SingleFetchRateLimiter failClosedLimiter = new SingleFetchRateLimiter(new FailingProvider(), LIMIT, meterRegistry);

		// when
		assertThatThrownBy(() -> failClosedLimiter.check("1.1.1.1")).isInstanceOf(RateLimitExceededException.class);

		// then
		assertThat(output.getOut()).contains("근본원인=java.net.SocketTimeoutException");
	}

	private static class FailingProvider implements RateLimitBucketProvider {

		int callCount;

		@Override
		public Bucket getBucket(String key, BucketConfiguration configuration) {
			callCount++;
			throw new IllegalStateException("Redis 연결 실패(테스트)", new java.net.SocketTimeoutException("시간 초과"));
		}
	}
}
