package com.ottnavi.global.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import java.time.Duration;
import java.util.function.LongSupplier;

/** 일반·단건 두 버킷이 함께 쓰는 계산: 버킷 설정 만들기와 Retry-After 초 올림 */
final class RateLimitSupport {

	private RateLimitSupport() {
	}

	/** 통 크기와 충전 속도로 버킷 설정을 만든다. 충전은 시간에 비례해 조금씩 채운다 */
	static BucketConfiguration configuration(RateLimitProperties.Limit limit) {
		Bandwidth bandwidth = Bandwidth.builder()
				.capacity(limit.capacity())
				.refillGreedy(limit.refillTokens(), limit.refillPeriod())
				.build();
		return BucketConfiguration.builder().addLimit(bandwidth).build();
	}

	/** 나노초를 초로 올림한다. Retry-After는 정수 초라 0은 1로 올린다(예: 0.2초 → 1초, 1.01초 → 2초) */
	static long toCeilSeconds(long nanos) {
		long seconds = (nanos + 999_999_999L) / 1_000_000_000L;
		if (seconds < 1) {
			return 1;
		}
		return seconds;
	}

	/**
	 * 저장소(Redis) 오류 뒤 일정 시간 저장소를 다시 부르지 않기 위한 표시.
	 * Lettuce는 연결이 끊기면 명령을 큐에 쌓고 타임아웃까지 기다려, 장애 동안 요청마다 지연과 로그가 쌓이기 때문이다.
	 */
	static final class FailureWindow {

		private final long pauseNanos;
		private volatile boolean failedBefore;
		private volatile long failedAtNanos; // System.nanoTime

		FailureWindow(long pauseNanos) {
			this.pauseNanos = pauseNanos;
		}

		void markFailed() {
			failedAtNanos = System.nanoTime();
			failedBefore = true;
		}

		/** 건너뛰는 중이면 남은 나노초, 아니면 0 */
		long remainingNanos() {
			if (!failedBefore) {
				return 0;
			}
			long remaining = pauseNanos - (System.nanoTime() - failedAtNanos);
			if (remaining < 0) {
				return 0;
			}
			return remaining;
		}
	}

	/**
	 * 저장소 오류 경고 로그를 줄이는 장치. 같은 장애가 이어지는 동안 처음 1건만 상세히 남기고,
	 * 그 뒤에는 1분에 한 번 "N건 발생" 요약만 남기게 판단한다. 저장소 호출이 한 번 성공하면 장애가 끝난 것으로 보고
	 * 다음 장애는 다시 처음 1건부터 남긴다. 오류가 드문 경로라 synchronized로 단순하게 처리한다.
	 */
	static final class StoreErrorLog {

		static final long FIRST_ERROR = -1; // recordError 결과: 이 장애의 첫 오류(상세 로그를 남긴다)
		private static final long SUMMARY_INTERVAL_NANOS = Duration.ofMinutes(1).toNanos();

		private final LongSupplier nanoSource; // 테스트에서 시간을 바꿔 넣을 수 있게 둔다
		private volatile boolean inFailure;     // 장애가 이어지는 중인지
		private long lastLoggedAtNanos;         // 마지막으로 로그를 남긴 시각
		private long pendingCount;              // 로그에 아직 반영하지 않은 오류 건수

		StoreErrorLog() {
			this(System::nanoTime);
		}

		StoreErrorLog(LongSupplier nanoSource) {
			this.nanoSource = nanoSource;
		}

		/**
		 * 오류 한 건을 기록하고 로그에 남길 값을 돌려준다.
		 *
		 * @return FIRST_ERROR면 상세 로그, 0이면 로그 없음, 양수면 그 건수만큼 요약 로그
		 */
		synchronized long recordError() {
			long now = nanoSource.getAsLong();
			// 1. 장애의 첫 오류면 상세 로그를 남기게 한다
			if (!inFailure) {
				inFailure = true;
				lastLoggedAtNanos = now;
				pendingCount = 0;
				return FIRST_ERROR;
			}
			// 2. 이어지는 오류는 세어 두고, 마지막 로그 뒤 1분이 지났을 때만 요약을 내보낸다
			pendingCount++;
			if (now - lastLoggedAtNanos < SUMMARY_INTERVAL_NANOS) {
				return 0;
			}
			long summarized = pendingCount;
			pendingCount = 0;
			lastLoggedAtNanos = now;
			return summarized;
		}

		/** 저장소 호출이 성공했을 때 부른다. 장애 중이 아니면 락 없이 바로 끝난다 */
		void markRecovered() {
			if (!inFailure) {
				return;
			}
			synchronized (this) {
				inFailure = false;
				pendingCount = 0;
			}
		}
	}
}
