package com.ottnavi.plan.domain.rule;

import com.ottnavi.plan.domain.rule.PlanChangeEvidence.AvailabilityShift;
import com.ottnavi.plan.domain.rule.PlanSnapshot.Unit;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 월 중간 재계산에서 "변경 알림(PLAN_CHANGE)을 보낼 일인가"를 판정한다(Task 073). 순수 Java라 DB 없이 테스트한다.
 * 이번 달 확정 달에 시청을 끝내도록 배정된 작품이, 배정된 그 서비스에서 이전 플랜 이후 빠졌을 때만 알린다.
 * 다음 달·다다음 달 작품이 빠지는 것은 아직 결제 전이라 알리지 않는다.
 *
 * <pre>
 * 이전 플랜: 카지노(시즌 없음)를 11월에 넷플릭스(서비스 1)에서 끝내도록 배정, 지금은 11월
 *   증거: 카지노가 서비스 1에서 이전 플랜 이후 있음 → 없음   → 빠진 작품 [카지노/넷플릭스] (알림 대상)
 * 이전 플랜: 카지노가 12월에 넷플릭스에서 끝남, 지금은 11월    → 빠진 작품 없음 (알림 아님)
 * 카지노가 다른 서비스에 새로 들어옴(빠진 것이 아님)         → 빠진 작품 없음
 * </pre>
 *
 * 빠짐 판정은 변경 요약의 이유(TITLE_LEFT_SERVICE)와 같은 규칙({@link PlanChangeDetector#isLeft})이다.
 * 이유 문장은 여러 작품을 묶어 첫 작품만 들고 있으므로, 이유가 있는지만 보지 않고 작품마다 직접 판정한다.
 * 한계: "배정된 달"은 작품의 시청을 끝내는 달(PlanSnapshot.Placement)이다. 여러 달에 걸쳐 보는 작품이 이번 달에 시작해 다음 달에 끝나면 이번 달 배정으로 세지 않는다.
 */
public final class PlanChangeAlertRule {

	private PlanChangeAlertRule() {
	}

	/**
	 * 이번 달 확정 달에 배정됐다가 그 서비스에서 빠진 작품을 시청 단위 ID 순으로 돌려준다. 없으면 빈 목록이다.
	 *
	 * @param previous       재계산 직전 ACTIVE 플랜의 스냅샷
	 * @param confirmedMonth 새 플랜의 확정 달(1일)
	 * @param evidence       제공 상태 변경 근거
	 */
	public static List<LeftTitle> findLeftTitles(PlanSnapshot previous, LocalDate confirmedMonth,
			PlanChangeEvidence evidence) {
		// 1. 이전 플랜에서 확정 달에 배정됐던 작품만 훑는다(시청 단위 ID 순으로 결과 순서를 고정)
		List<Unit> assigned = new ArrayList<>();
		for (Unit unit : previous.units()) {
			if (unit.placement() != null && unit.placement().month().equals(confirmedMonth)) {
				assigned.add(unit);
			}
		}
		assigned.sort(Comparator.comparingLong(Unit::watchUnitId));

		List<LeftTitle> left = new ArrayList<>();
		for (Unit unit : assigned) {
			// 2. 그 작품이 배정된 서비스의 제공 상태 변경 중 이전 플랜 이후 것만 모은다
			List<AvailabilityShift> shifts = new ArrayList<>();
			for (AvailabilityShift shift : evidence.availabilityShifts()) {
				if (shift.watchUnitId() == unit.watchUnitId()
						&& shift.ottServiceId() == unit.placement().ottServiceId()
						&& shift.changedAt().isAfter(previous.dataAsOf())) {
					shifts.add(shift);
				}
			}

			// 3. 빠졌으면 후보에 넣는다
			if (PlanChangeDetector.isLeft(shifts)) {
				left.add(new LeftTitle(unit.watchUnitId(), unit.title(), unit.seasonNumber(),
						unit.placement().ottServiceId()));
			}
		}
		return left;
	}

	/** 배정된 서비스에서 빠진 작품 한 건. seasonNumber는 영화·시즌 없는 작품이면 null */
	public record LeftTitle(long watchUnitId, String title, Integer seasonNumber, long ottServiceId) {
	}
}
