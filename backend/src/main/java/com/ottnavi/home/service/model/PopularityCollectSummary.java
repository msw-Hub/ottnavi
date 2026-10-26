package com.ottnavi.home.service.model;

import java.time.LocalDate;

/**
 * 인기 수집 한 번의 결과.
 *
 * @param snapshotDate 저장한 스냅샷 날짜(KST)
 * @param slots 호출 대상 수(서비스 × 영화·드라마)
 * @param failedSlots TMDB 호출이 실패해 건너뛴 대상 수
 * @param savedRows 저장한 스냅샷 행 수
 */
public record PopularityCollectSummary(LocalDate snapshotDate, int slots, int failedSlots, int savedRows) {
}
