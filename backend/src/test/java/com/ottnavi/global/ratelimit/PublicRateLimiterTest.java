package com.ottnavi.global.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * 일반 버킷의 한도 판정을 검증한다. 메모리 버킷을 쓰므로 Docker·스프링 컨텍스트가 필요 없다.
 * 한도: 통 크기 2, 1분에 60개 충전(= 1초에 1개).
 */
@ExtendWith(OutputCaptureExtension.class)
class PublicRateLimiterTest {

	private static final RateLimitProperties.Limit LIMIT = new RateLimitProperties.Limit(2, 60, Duration.ofMinutes(1));

	private final InMemoryBucketProvider provider = new InMemoryBucketProvider();
	private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
	private final PublicRateLimiter limiter = new PublicRateLimiter(provider, LIMIT, meterRegistry);

	@Test
	void 통_크기_안의_요청은_통과한다() {
		// given & when & then
		assertThatCode(() -> {
			limiter.check("1.1.1.1");
			limiter.check("1.1.1.1");
		}).doesNotThrowAnyException();
	}

	@Test
	void 통을_다_쓰면_다음_요청은_RateLimitExceededException이고_대기_초는_올림한다() {
		// given
		limiter.check("1.1.1.1");
		limiter.check("1.1.1.1");

		// when & then: 초당 1개씩 차므로 다음 토큰까지 1초 이하이고, 올림해서 1초로 알린다
		assertThatThrownBy(() -> limiter.check("1.1.1.1"))
				.isInstanceOfSatisfying(RateLimitExceededException.class,
						ex -> assertThat(ex.getRetryAfterSeconds()).isEqualTo(1));
	}

	@Test
	void IP가_다르면_한도를_따로_센다() {
		// given
		limiter.check("1.1.1.1");
		limiter.check("1.1.1.1");

		// when & then
		assertThatCode(() -> limiter.check("2.2.2.2")).doesNotThrowAnyException();
		assertThat(provider.hasKey("rl:public:1.1.1.1")).isTrue();
		assertThat(provider.hasKey("rl:public:2.2.2.2")).isTrue();
	}

	@Test
	void 저장소_오류가_나면_요청을_통과시키고_잠시_저장소를_다시_부르지_않는다() {
		// given
		FailingProvider failing = new FailingProvider();
		PublicRateLimiter failOpenLimiter = new PublicRateLimiter(failing, LIMIT, meterRegistry);

		// when & then
		assertThatCode(() -> failOpenLimiter.check("1.1.1.1")).doesNotThrowAnyException();
		assertThatCode(() -> failOpenLimiter.check("1.1.1.1")).doesNotThrowAnyException();
		assertThat(failing.callCount).isEqualTo(1); // 두 번째는 건너뛰어 저장소를 부르지 않았다
	}

	@Test
	void 저장소_오류_경고_로그에_근본_원인_클래스가_남는다(CapturedOutput output) {
		// given
		PublicRateLimiter failOpenLimiter = new PublicRateLimiter(new FailingProvider(), LIMIT, meterRegistry);

		// when
		failOpenLimiter.check("1.1.1.1");

		// then
		assertThat(output.getOut()).contains("근본원인=java.net.SocketTimeoutException").doesNotContain("Redis 연결 실패(테스트)"); // 예외 메시지는 로그에 남기지 않는다
	}

	@Test
	void 거부할_때만_ratelimit_rejected_카운터가_bucket_public_태그로_늘어난다() {
		// given
		limiter.check("1.1.1.1");
		limiter.check("1.1.1.1");
		double beforeReject = rejectedCount("public");

		// when
		assertThatThrownBy(() -> limiter.check("1.1.1.1")).isInstanceOf(RateLimitExceededException.class);

		// then
		assertThat(beforeReject).isZero();
		assertThat(rejectedCount("public")).isEqualTo(1.0);
	}

	private double rejectedCount(String bucket) {
		Counter counter = meterRegistry.find("ratelimit.rejected").tag("bucket", bucket).counter();
		if (counter == null) {
			return 0;
		}
		return counter.count();
	}

	/** 항상 저장소 오류를 던지는 창구 */
	@Test
	void 저장소_오류가_나면_ratelimit_store_error_카운터가_bucket_public_태그로_늘어난다() {
		// given
		PublicRateLimiter errorLimiter = new PublicRateLimiter(new FailingProvider(), LIMIT, meterRegistry);

		// when
		errorLimiter.check("1.1.1.1");

		// then
		Counter counter = meterRegistry.find("ratelimit.store_error").tag("bucket", "public").counter();
		assertThat(counter.count()).isEqualTo(1.0);
	}

	@Test
	void 저장소_오류가_이어지는_동안에도_상세_경고는_한_번만_남는다(CapturedOutput output) {
		// given
		PublicRateLimiter errorLimiter = new PublicRateLimiter(new FailingProvider(), LIMIT, meterRegistry);

		// when
		errorLimiter.check("1.1.1.1");
		errorLimiter.check("1.1.1.1");

		// then
		assertThat(output.getOut()).containsOnlyOnce("근본원인=");
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
