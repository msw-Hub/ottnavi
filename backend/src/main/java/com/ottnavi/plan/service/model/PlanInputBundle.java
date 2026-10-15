package com.ottnavi.plan.service.model;

import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.user.domain.enums.WatchPreset;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 계산 한 번에 필요한 입력 묶음. 엔진 입력(PlanInput)과 DRAFT 저장에 쓸 값(전체 구독 비용, 가격 출처, 기준 시각, 설정 스냅샷)을 담는다.
 *
 * @param planInput             엔진 입력(시청 단위·후보 상품·월 예산·월 시청 시간)
 * @param allSubscribeCost      전체 구독 비용(원). 세 플랜 유형이 같은 값을 쓴다
 * @param productPrices         상품 ID → 가격 정보(후보 상품과 같은 상품만)
 * @param excludedProductIds    입력 단계에서 제외한 상품 ID(가격 상한 초과)
 * @param wishlistItemIdByUnit  시청 단위 ID → 찜 ID(결과를 찜 기준으로 되돌릴 때 쓴다)
 * @param dataAsOf              데이터 기준 시각(수집 Job 마지막 COMPLETED 종료 시각). 완료된 수집이 없으면 null
 * @param startMonth            첫 달의 1일(오늘 KST가 속한 달)
 * @param monthIndexes          계산하는 달 번호(0~2)
 * @param monthlyBudget         계산 당시 월 예산(원) 스냅샷
 * @param monthlyWatchMinutes   계산 당시 월 시청 시간(분) 스냅샷
 * @param watchPreset           시청 시간을 고른 방식 스냅샷
 */
public record PlanInputBundle(
		PlanInput planInput,
		int allSubscribeCost,
		Map<Long, ProductPriceInfo> productPrices,
		Set<Long> excludedProductIds,
		Map<Long, Long> wishlistItemIdByUnit,
		Instant dataAsOf,
		LocalDate startMonth,
		List<Integer> monthIndexes,
		int monthlyBudget,
		int monthlyWatchMinutes,
		WatchPreset watchPreset) {
}
