package com.ottnavi.retention.service.model;

/**
 * 한 번의 작품 점검 결과 요약.
 *
 * @param staleCount 기준을 넘긴 작품 전체 수(상한과 무관하게 센 값)
 * @param processed 이번에 처리한 작품 수(상한으로 잘린 값)
 */
public record TitleRetentionSummary(
		long staleCount,
		int processed,
		int refreshed,
		int refreshSkipped,
		int purged,
		int deferred) {
}
