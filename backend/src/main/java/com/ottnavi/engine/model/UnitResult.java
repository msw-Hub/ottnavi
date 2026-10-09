package com.ottnavi.engine.model;

import java.util.List;

/**
 * 시청 단위 하나의 평가 결과. 배정됐으면 다 본 달과 배정 행을, 못 했으면 이유 코드를 갖는다.
 * 미배정이면 배정 행이 0개다(PRD 5.4: 3개월 안에 끝나지 않으면 배정하지 않고 일부 배정했던 시간은 풀어 준다, D4).
 */
public record UnitResult(
		long watchUnitId,             // 시청 단위 ID
		boolean scheduled,            // 3개월 안에 다 보도록 배정됐는지
		Integer completedMonthIndex,  // 다 본 달(0~2). 점수는 이 달에 얻는다. 미배정이면 null
		UnitReason reason,            // 미배정 이유. 배정됐으면 null
		List<Assignment> assignments  // 배정 행(달 오름차순). 미배정이면 빈 목록
) {

	/** 배정 여부·이유 코드·다 본 달·배정 행이 서로 맞는지 검증한다. */
	public UnitResult {
		if (scheduled != (reason == null)) {
			throw new IllegalArgumentException("배정 여부와 이유 코드가 맞지 않는다(배정되면 이유 없음, 미배정이면 이유 필수): watchUnitId=" + watchUnitId);
		}
		assignments = assignments == null ? List.of() : List.copyOf(assignments);
		if (scheduled) {
			if (completedMonthIndex == null || completedMonthIndex < 0 || completedMonthIndex >= Selection.MONTH_COUNT) {
				throw new IllegalArgumentException("배정된 단위의 다 본 달은 0~2여야 한다: watchUnitId=" + watchUnitId + ", completedMonthIndex=" + completedMonthIndex);
			}
			if (assignments.isEmpty()) {
				throw new IllegalArgumentException("배정된 단위는 배정 행이 1개 이상이어야 한다: watchUnitId=" + watchUnitId);
			}
		} else {
			if (completedMonthIndex != null) {
				throw new IllegalArgumentException("미배정 단위는 다 본 달이 없어야 한다: watchUnitId=" + watchUnitId);
			}
			if (!assignments.isEmpty()) {
				throw new IllegalArgumentException("미배정 단위는 배정 행이 없어야 한다: watchUnitId=" + watchUnitId);
			}
		}
	}

	/** 배정되지 못한 단위의 결과를 만든다. */
	public static UnitResult unscheduled(long watchUnitId, UnitReason reason) {
		return new UnitResult(watchUnitId, false, null, reason, List.of());
	}

	/** 배정된 단위의 결과를 만든다. */
	public static UnitResult scheduled(long watchUnitId, int completedMonthIndex, List<Assignment> assignments) {
		return new UnitResult(watchUnitId, true, completedMonthIndex, null, assignments);
	}
}
