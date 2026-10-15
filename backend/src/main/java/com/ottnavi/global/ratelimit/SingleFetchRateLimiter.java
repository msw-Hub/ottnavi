package com.ottnavi.global.ratelimit;

import com.ottnavi.global.error.ErrorCode;
import com.ottnavi.global.error.RateLimitExceededException;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

/**
 * 단건 수집(검색했는데 DB에 없는 작품을 TMDB에서 가져와 저장) 전용 IP별 한도를 검사한다(PRD R11-12: IP당 분당 10회).
 * 익명 방문자가 TMDB 호출을 일으킬 수 있어, 일반 버킷(분당 60회)보다 낮은 한도를 따로 둔다(TECH 3절).
 *
 * 호출 방법(Task 050): 검색 서비스가 DB를 먼저 조회하고, 없어서 TMDB를 불러야 하는 그 순간에만
 * check(clientIp)를 부른다. DB 결과만으로 응답하는 검색은 토큰을 쓰지 않는다.
 * 서비스 계층은 HttpServletRequest를 모르므로, 컨트롤러가 request.getRemoteAddr()를 꺼내
 * 서비스 메서드 인자(예: String clientIp)로 넘겨 주고 서비스가 그 값을 그대로 check에 넘긴다.
 * (프록시 헤더는 ForwardedHeaderFilter가 이미 remoteAddr에 반영했으므로 헤더를 따로 읽지 않는다. TECH 4절)
 * 한도를 넘으면 RateLimitExceededException(429)이 나가므로 서비스는 잡지 않고 전파한다.
 *
 * 일반 버킷과 달리 저장소(Redis)에 문제가 있으면 요청을 통과시키지 않고 거부한다(fail-closed).
 * 저장소가 죽었을 때 한도 없이 TMDB를 부르게 두면 TMDB 한도(초당 약 40회)를 배치와 나눠 쓰는 문제가 생기기 때문이다.
 * 이때도 기존 RATE_LIMIT_EXCEEDED(429)와 Retry-After를 쓴다(새 오류 코드를 만들지 않는다).
 * 거부는 이 클래스의 check 한 곳에서만 결정하며, 한도 초과일 때 ratelimit.rejected{bucket=single_fetch}를 센다.
 */
@Slf4j
public class SingleFetchRateLimiter {

	static final String KEY_PREFIX = "rl:fetch:";                                          // Redis 키: rl:fetch:{remoteAddr}
	private static final long FAIL_CLOSED_PAUSE_NANOS = Duration.ofSeconds(5).toNanos();   // 저장소 오류 뒤 바로 거부하는 시간

	private final RateLimitBucketProvider bucketProvider;
	private final BucketConfiguration configuration;
	private final MeterRegistry meterRegistry;
	private final RateLimitSupport.FailureWindow failureWindow = new RateLimitSupport.FailureWindow(FAIL_CLOSED_PAUSE_NANOS);

	public SingleFetchRateLimiter(RateLimitBucketProvider bucketProvider, RateLimitProperties.Limit limit,
			MeterRegistry meterRegistry) {
		this.bucketProvider = bucketProvider;
		this.configuration = RateLimitSupport.configuration(limit);
		this.meterRegistry = meterRegistry;
	}

	/**
	 * 클라이언트 IP의 단건 수집 토큰 한 개를 쓴다. 한도를 넘거나 저장소를 쓸 수 없으면 RateLimitExceededException을 던진다.
	 *
	 * @param clientIp 컨트롤러가 request.getRemoteAddr()로 꺼내 넘긴 방문자 IP
	 */
	public void check(String clientIp) {
		// 1. 최근 저장소 오류가 있었다면 저장소를 부르지 않고 바로 거부한다(남은 시간을 Retry-After로 알린다)
		long remainingNanos = failureWindow.remainingNanos();
		if (remainingNanos > 0) {
			throw new RateLimitExceededException(ErrorCode.RATE_LIMIT_EXCEEDED.getMessage(),
					RateLimitSupport.toCeilSeconds(remainingNanos));
		}

		// 2. 토큰 한 개를 쓰고 결과를 받는다. 저장소 오류는 거부한다(fail-closed)
		ConsumptionProbe probe;
		try {
			Bucket bucket = bucketProvider.getBucket(KEY_PREFIX + clientIp, configuration);
			probe = bucket.tryConsumeAndReturnRemaining(1);
		} catch (RuntimeException e) {
			failureWindow.markFailed();
			// 스택은 남기지 않는다. 장애 동안 같은 스택이 반복해서 쌓이지 않게 한다
			log.warn("단건 수집 제한 저장소 오류로 요청을 거부합니다(fail-closed): {}: {}",
					e.getClass().getSimpleName(), e.getMessage());
			throw new RateLimitExceededException(ErrorCode.RATE_LIMIT_EXCEEDED.getMessage(),
					RateLimitSupport.toCeilSeconds(FAIL_CLOSED_PAUSE_NANOS));
		}

		// 3. 토큰이 없으면 거부를 세고, 다시 시도할 수 있을 때까지 기다릴 초(올림)를 담아 거부한다
		if (!probe.isConsumed()) {
			meterRegistry.counter(PublicRateLimiter.METRIC_NAME, "bucket", "single_fetch").increment(); // 태그에 IP는 넣지 않는다
			long retryAfterSeconds = RateLimitSupport.toCeilSeconds(probe.getNanosToWaitForRefill());
			throw new RateLimitExceededException(ErrorCode.RATE_LIMIT_EXCEEDED.getMessage(), retryAfterSeconds);
		}
	}
}
