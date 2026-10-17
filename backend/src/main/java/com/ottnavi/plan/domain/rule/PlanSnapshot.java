package com.ottnavi.plan.domain.rule;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 변경 판정(PlanChangeDetector)에 넣는 플랜 한 건의 스냅샷. DB 엔티티가 아닌 값만 담아 DB 없이 테스트한다.
 * 서비스가 plan·plan_month·plan_month_product·plan_item·plan_assignment에서 읽어 이 모양으로 바꿔 넘긴다.
 *
 * @param planId      플랜 ID
 * @param dataAsOf    계산에 쓴 마지막 완료 수집 시각(제공 상태 변경이 이 뒤에 생겼는지 가르는 기준)
 * @param activatedAt 저장(활성화) 시각(다 봤음 체크가 이 뒤에 생겼는지 가르는 기준)
 * @param months      플랜이 다루는 달(보통 3개)
 * @param units       찜 단위(시청 단위)별 자리. 배정 안 된 단위도 포함한다
 */
public record PlanSnapshot(long planId, Instant dataAsOf, Instant activatedAt, List<Month> months, List<Unit> units) {

	/**
	 * 한 달의 구독.
	 *
	 * @param month       달의 1일
	 * @param isConfirmed 재계산 당시 첫 달(곧 결제하는 달)인지
	 * @param cost        그 달 비용(원)
	 * @param products    그 달 구독 상품
	 */
	public record Month(LocalDate month, boolean isConfirmed, int cost, List<Product> products) {
	}

	/**
	 * 달에 담긴 상품 하나.
	 * ottServiceIds는 상품이 포함하는 서비스 전부다(단품은 1개, 번들은 구성 서비스 전부. 번들은 Task 076에서 생긴다).
	 *
	 * @param appliedPrice 원래 월 요금(원). 이미 구독 중이라 0원으로 계산된 달도 원래 요금을 담는다(plan_month_product.applied_price)
	 */
	public record Product(long productId, List<Long> ottServiceIds, String productName, int appliedPrice) {
	}

	/**
	 * 시청 단위(작품 또는 시즌) 하나의 자리.
	 *
	 * @param seasonNumber 영화·시즌 없는 작품은 null
	 * @param placement    시청을 끝내는 달과 그 달의 서비스. 배정하지 못했으면 null
	 */
	public record Unit(long watchUnitId, String title, Integer seasonNumber, Placement placement) {
	}

	/** 자리: (시청 완료 달의 1일, 그 달의 서비스) */
	public record Placement(LocalDate month, long ottServiceId) {
	}
}
