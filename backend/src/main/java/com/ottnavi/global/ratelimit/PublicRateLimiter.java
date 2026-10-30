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
 * 공개 API의 IP별 일반 요청 한도를 검사한다(PRD FR-05, R11-12: IP당 분당 60회).
 * 버킷은 "토큰이 든 통"이다. 요청 한 번에 토큰 한 개를 쓰고 통은 시간이 지나며 고르게 다시 찬다.
 * 통 크기(capacity)가 순간에 몰아서 보낼 수 있는 양이고, 채우는 속도가 평균 허용량이다.
 *
 * 이 한도는 서버와 Redis를 보호하는 용도다. 저장소(Redis)에 문제가 있으면 요청을 막지 않고 통과시킨다(fail-open).
 * 요청 제한이 죽었다고 서비스 전체가 멈추면 안 되기 때문이다.
 * 거부는 이 클래스의 check 한 곳에서만 결정하며, 거부할 때 ratelimit.rejected{bucket=public}을 센다.
 */
@Slf4j
public class PublicRateLimiter {

	static final String KEY_PREFIX = "rl:public:";                                       // Redis 키: rl:public:{remoteAddr}
	static final String STORE_ERROR_METRIC_NAME = "ratelimit.store_error";               // 저장소 오류 횟수 카운터 이름(세 버킷 공통)
	static final String METRIC_NAME = "ratelimit.rejected";                              // 거부 횟수 카운터 이름(두 버킷 공통)
	private static final long FAIL_OPEN_PAUSE_NANOS = Duration.ofSeconds(5).toNanos();    // 저장소 오류 뒤 검사를 건너뛰는 시간

	private final RateLimitBucketProvider bucketProvider;
	private final BucketConfiguration configuration; // 모든 IP가 같은 한도를 쓴다
	private final MeterRegistry meterRegistry;
	private final RateLimitSupport.StoreErrorLog storeErrorLog = new RateLimitSupport.StoreErrorLog(); // 같은 장애 동안 경고 로그를 줄인다
	private final RateLimitSupport.FailureWindow failureWindow = new RateLimitSupport.FailureWindow(FAIL_OPEN_PAUSE_NANOS);

	public PublicRateLimiter(RateLimitBucketProvider bucketProvider, RateLimitProperties.Limit limit,
			MeterRegistry meterRegistry) {
		this.bucketProvider = bucketProvider;
		this.configuration = RateLimitSupport.configuration(limit);
		this.meterRegistry = meterRegistry;
	}

	/**
	 * 클라이언트 IP의 토큰 한 개를 쓴다. 한도를 넘으면 RateLimitExceededException을 던진다.
	 *
	 * @param clientIp request.getRemoteAddr() 값. 프록시 헤더는 ForwardedHeaderFilter가 이미 반영했다(TECH 4절)
	 */
	public void check(String clientIp) {
		// 1. 최근 저장소 오류가 있었다면 잠시 검사를 건너뛴다(오류 동안 요청마다 연결 대기와 로그가 쌓이는 것을 막는다)
		if (failureWindow.remainingNanos() > 0) {
			return;
		}

		// 2. 토큰 한 개를 쓰고 결과를 받는다. 저장소 오류는 통과시키고 경고만 남긴다
		ConsumptionProbe probe;
		try {
			Bucket bucket = bucketProvider.getBucket(KEY_PREFIX + clientIp, configuration);
			probe = bucket.tryConsumeAndReturnRemaining(1);
			storeErrorLog.markRecovered(); // 저장소가 다시 응답하면 다음 장애는 처음 1건부터 남긴다
		} catch (RuntimeException e) {
			failureWindow.markFailed();
			// 스택은 남기지 않는다. 장애 동안 같은 스택이 반복해서 쌓이지 않게 한다. 메시지도 접속 정보(호스트 등)가 섞일 수 있어 클래스 이름만 남긴다
			meterRegistry.counter(PublicRateLimiter.STORE_ERROR_METRIC_NAME, "bucket", "public").increment(); // 태그는 닫힌 값만
			// 같은 장애 동안은 처음 1건만 상세히, 이후에는 1분에 한 번 건수만 남긴다
			long skippedCount = storeErrorLog.recordError();
			if (skippedCount == RateLimitSupport.StoreErrorLog.FIRST_ERROR) {
				log.warn("요청 제한 저장소 오류로 검사를 건너뜁니다(fail-open): {}, 근본원인={}", e.getClass().getSimpleName(),
						ExceptionRootCause.rootCauseName(e));
			} else if (skippedCount > 0) {
				log.warn("요청 제한 저장소 오류가 계속되고 있습니다(fail-open): 최근 1분간 {}건 발생", skippedCount);
			}
			return;
		}

		// 3. 토큰이 없으면 거부를 세고, 다시 시도할 수 있을 때까지 기다릴 초(올림)를 담아 거부한다
		if (!probe.isConsumed()) {
			meterRegistry.counter(METRIC_NAME, "bucket", "public").increment(); // 태그에 IP는 넣지 않는다(카디널리티)
			long retryAfterSeconds = RateLimitSupport.toCeilSeconds(probe.getNanosToWaitForRefill());
			throw new RateLimitExceededException(ErrorCode.RATE_LIMIT_EXCEEDED.getMessage(), retryAfterSeconds);
		}
	}
}
