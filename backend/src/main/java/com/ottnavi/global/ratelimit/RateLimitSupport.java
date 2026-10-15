package com.ottnavi.global.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;

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
}
