package com.ottnavi.plan.domain.rule;

import com.ottnavi.plan.domain.enums.PlanChangeReasonType;
import com.ottnavi.plan.domain.enums.PlanTitleChangeType;
import java.util.List;

/**
 * 재계산 변경 요약. plan.change_summary(TEXT)에 JSON으로 저장되는 모양이다(PlanChangeSummaryCodec).
 * 필드는 계약 PlanChangeHistoryEntry의 summary·reasons·monthChanges·titleChanges와 같고, 내부용으로
 * 이유·작품 변화에 watchUnitId·ottServiceId가 더해진다(응답으로 옮길 때 뺀다). changeId·changedAt·planType·dataAsOf는
 * plan 행에서 읽으므로 JSON에 넣지 않는다. 형식이 바뀌면 v를 올린다.
 *
 * @param v            JSON 형식 버전
 * @param summary      서버가 만든 한 줄 요약
 * @param reasons      바뀐 이유(1건 이상)
 * @param monthChanges 달별 구독 변화(달 순서). 새 플랜에만 있는 달은 항상 포함한다
 * @param titleChanges 작품 변화
 */
public record PlanChangeSummary(
		int v,
		String summary,
		List<Reason> reasons,
		List<MonthChange> monthChanges,
		List<TitleChange> titleChanges) {

	/** 현재 JSON 형식 버전 */
	public static final int CURRENT_VERSION = 1;

	/**
	 * 바뀐 이유 한 건.
	 *
	 * @param watchUnitId  내부용. 관련 시청 단위(가격 변경·여러 건이면 첫 건 기준이고 해당 없으면 null)
	 * @param ottServiceId 내부용. 관련 서비스(해당 없으면 null)
	 */
	public record Reason(PlanChangeReasonType reasonType, String description, Long watchUnitId, Long ottServiceId) {
	}

	/**
	 * 한 달의 구독 변화.
	 *
	 * @param month        YYYY-MM
	 * @param previousCost 이전 플랜에 없던 달이면 null
	 */
	public record MonthChange(String month, boolean isConfirmed, List<Product> previousProducts,
			List<Product> currentProducts, Integer previousCost, int currentCost) {
	}

	/** 달별 구독 변화에 보이는 상품 한 개. ottServiceIds는 번들이면 구성 서비스 전부, 단품이면 1개 */
	public record Product(long productId, List<Long> ottServiceIds, String productName) {
	}

	/**
	 * 작품(시즌) 하나의 변화.
	 *
	 * @param watchUnitId 내부용. 시청 단위 ID
	 * @param previous    이전 자리(ADDED면 null)
	 * @param current     새 자리(REMOVED면 null)
	 */
	public record TitleChange(PlanTitleChangeType changeType, long watchUnitId, String title, Integer seasonNumber,
			Placement previous, Placement current) {
	}

	/** 자리: 시청을 끝내는 달(YYYY-MM)과 그 달의 서비스 */
	public record Placement(String month, long ottServiceId) {
	}
}
