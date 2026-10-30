package com.ottnavi.global.ratelimit;

import com.ottnavi.global.common.ExceptionRootCause;
import com.ottnavi.global.error.ErrorCode;
import com.ottnavi.global.error.RateLimitExceededException;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

/**
 * 데모 로그인(POST /api/auth/demo-login) 전용 IP별 시도 한도를 검사한다(IP당 15분 5회, 계정 잠금은 없다).
 * 데모 계정은 아이디·비밀번호 한 쌍이 모두에게 같아, 비밀번호를 맞혀 보는 시도를 일반 버킷(분당 60회)보다 훨씬 낮게 막는다.
 * 성공·실패와 상관없이 시도 한 번에 토큰 한 개를 쓴다(성공만 따로 빼면 성공 응답 여부로 한도를 우회할 수 있다).
 *
 * 구조는 SingleFetchRateLimiter와 같다. 저장소(Redis)에 문제가 있으면 통과시키지 않고 거부한다(fail-closed):
 * 저장소가 죽었을 때 한도 없이 비밀번호를 맞혀 보게 두지 않기 위함이다. 이때도 RATE_LIMIT_EXCEEDED(429)와 Retry-After를 쓴다.
 * 한도 초과일 때 ratelimit.rejected{bucket=demo_login}을 센다.
 */
@Slf4j
public class DemoLoginRateLimiter {

	static final String KEY_PREFIX = "rl:demo:";                                           // Redis 키: rl:demo:{remoteAddr}
	private static final long FAIL_CLOSED_PAUSE_NANOS = Duration.ofSeconds(5).toNanos();   // 저장소 오류 뒤 바로 거부하는 시간

	private final RateLimitBucketProvider bucketProvider;
	private final BucketConfiguration configuration;
	private final MeterRegistry meterRegistry;
	private final RateLimitSupport.StoreErrorLog storeErrorLog = new RateLimitSupport.StoreErrorLog(); // 같은 장애 동안 경고 로그를 줄인다
	private final RateLimitSupport.FailureWindow failureWindow = new RateLimitSupport.FailureWindow(FAIL_CLOSED_PAUSE_NANOS);

	public DemoLoginRateLimiter(RateLimitBucketProvider bucketProvider, RateLimitProperties.Limit limit,
			MeterRegistry meterRegistry) {
		this.bucketProvider = bucketProvider;
		this.configuration = RateLimitSupport.configuration(limit);
		this.meterRegistry = meterRegistry;
	}

	/**
	 * 클라이언트 IP의 데모 로그인 시도 토큰 한 개를 쓴다. 한도를 넘거나 저장소를 쓸 수 없으면 RateLimitExceededException을 던진다.
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
			storeErrorLog.markRecovered(); // 저장소가 다시 응답하면 다음 장애는 처음 1건부터 남긴다
		} catch (RuntimeException e) {
			failureWindow.markFailed();
			// 스택은 남기지 않는다. 장애 동안 같은 스택이 반복해서 쌓이지 않게 한다. 메시지도 접속 정보(호스트 등)가 섞일 수 있어 클래스 이름만 남긴다
			meterRegistry.counter(PublicRateLimiter.STORE_ERROR_METRIC_NAME, "bucket", "demo_login").increment(); // 태그는 닫힌 값만
			// 같은 장애 동안은 처음 1건만 상세히, 이후에는 1분에 한 번 건수만 남긴다
			long skippedCount = storeErrorLog.recordError();
			if (skippedCount == RateLimitSupport.StoreErrorLog.FIRST_ERROR) {
				log.warn("데모 로그인 제한 저장소 오류로 요청을 거부합니다(fail-closed): {}, 근본원인={}", e.getClass().getSimpleName(),
						ExceptionRootCause.rootCauseName(e));
			} else if (skippedCount > 0) {
				log.warn("데모 로그인 제한 저장소 오류가 계속되고 있습니다(fail-closed): 최근 1분간 {}건 발생", skippedCount);
			}
			throw new RateLimitExceededException(ErrorCode.RATE_LIMIT_EXCEEDED.getMessage(),
					RateLimitSupport.toCeilSeconds(FAIL_CLOSED_PAUSE_NANOS));
		}

		// 3. 토큰이 없으면 거부를 세고, 다시 시도할 수 있을 때까지 기다릴 초(올림)를 담아 거부한다
		if (!probe.isConsumed()) {
			meterRegistry.counter(PublicRateLimiter.METRIC_NAME, "bucket", "demo_login").increment(); // 태그에 IP는 넣지 않는다
			long retryAfterSeconds = RateLimitSupport.toCeilSeconds(probe.getNanosToWaitForRefill());
			throw new RateLimitExceededException(ErrorCode.RATE_LIMIT_EXCEEDED.getMessage(), retryAfterSeconds);
		}
	}
}
