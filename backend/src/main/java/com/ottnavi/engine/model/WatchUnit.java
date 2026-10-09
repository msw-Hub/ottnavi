package com.ottnavi.engine.model;

import java.util.Comparator;
import java.util.Set;

/**
 * 시청 단위: 영화 1편 또는 드라마 시즌 1개(PRD 5.4).
 * 시청 가능 상품에는 제공 상태가 AVAILABLE인 서비스의 상품만 넣는다. "모름"은 시청 가능으로 세지 않고(PRD 5.4 구독 상태 반영),
 * FREE 상품도 다른 상품과 똑같이 넣는다(D1, 선택하지 않아도 볼 수 있다는 처리는 평가기가 한다).
 */
public record WatchUnit(
		long id,                       // 시청 단위 ID(watchUnitId)
		Priority priority,             // 우선순위: MUST / WANT / MAYBE
		int requiredMinutes,           // 필요 시간(분), 1 이상
		Set<Long> watchableProductIds, // 시청 가능 상품 ID(이 시청 단위를 볼 수 있는 상품). 없으면 빈 집합
		boolean isProviderUnknown      // 제공 상태가 "모름"인 서비스가 있는지. 시청 가능 상품이 없을 때 NO_PROVIDER와 UNKNOWN을 가른다
) {

	/**
	 * 시청 단위의 배정 순서(PRD 5.4 배정 규칙 1).
	 * ① 시청 가능 상품이 있는 단위 먼저(없는 단위, 즉 NO_PROVIDER·UNKNOWN은 맨 뒤) → ② 우선순위 MUST → WANT → MAYBE
	 * → ③ 필요 시간 짧은 순 → ④ watchUnitId 오름차순(결과를 항상 같게 하려는 도출, TECH 1절).
	 * ①은 사용자 결정(2026-10-09, PRD 5.4·안내서에 반영 필요)이다. 시청 가능 상품이 없는 단위는 시간을 쓰지 않으므로
	 * 배정 결과는 바뀌지 않고 결과 목록 순서만 바뀐다. 맨 뒤에 모아 두면 평가기가 이 단위들을 배정 루프에서 건너뛸 수 있다.
	 * {@link PlanInput}이 생성될 때 이 순서로 한 번 정렬해 보관한다(D8). 정렬 기준은 정렬 대상인 이 record 옆에 둔다.
	 */
	public static final Comparator<WatchUnit> ASSIGNMENT_ORDER =
			Comparator.comparingInt((WatchUnit unit) -> unit.watchableProductIds().isEmpty() ? 1 : 0) // ① 있음(0)이 없음(1)보다 먼저
					.thenComparing(WatchUnit::priority)           // ② enum 선언 순서(MUST → WANT → MAYBE)가 앞일수록 먼저
					.thenComparingInt(WatchUnit::requiredMinutes) // ③ 필요 시간 짧은 순
					.thenComparingLong(WatchUnit::id);            // ④ watchUnitId 오름차순

	/** 값을 검증하고 시청 가능 상품 집합을 불변으로 복사한다. */
	public WatchUnit {
		if (priority == null) {
			throw new IllegalArgumentException("시청 단위 우선순위는 비어 있을 수 없다: watchUnitId=" + id);
		}
		if (requiredMinutes <= 0) {
			throw new IllegalArgumentException("시청 단위 필요 시간은 1분 이상이어야 한다: watchUnitId=" + id + ", requiredMinutes=" + requiredMinutes);
		}
		if (watchableProductIds == null) {
			throw new IllegalArgumentException("시청 가능 상품 ID 집합은 null일 수 없다(없으면 빈 집합): watchUnitId=" + id);
		}
		for (Long productId : watchableProductIds) {
			if (productId == null) {
				throw new IllegalArgumentException("시청 가능 상품 ID에 null이 있다: watchUnitId=" + id);
			}
		}
		// record는 필드 참조만 final이라 바깥 집합이 바뀌면 결과가 흔들린다. 생성 때 한 번 불변 복사한다
		watchableProductIds = Set.copyOf(watchableProductIds);
	}
}
