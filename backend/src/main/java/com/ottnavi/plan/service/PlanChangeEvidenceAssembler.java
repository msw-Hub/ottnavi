package com.ottnavi.plan.service;

import com.ottnavi.catalog.domain.enums.AvailabilityStatus;
import com.ottnavi.catalog.service.AvailabilityChangeQueryService;
import com.ottnavi.catalog.service.model.UnitAvailabilityShift;
import com.ottnavi.plan.domain.rule.PlanChangeEvidence;
import com.ottnavi.plan.domain.rule.PlanSnapshot;
import com.ottnavi.provider.domain.entity.OttService;
import com.ottnavi.provider.service.OttServiceQueryService;
import com.ottnavi.wishlist.service.WishlistService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 변경 이유 판정에 쓰는 근거 자료(PlanChangeEvidence)를 다른 도메인의 서비스에서 모아 온다(Task 069).
 * 다른 도메인의 리포지토리는 쓰지 않는다. 호출하는 쪽의 트랜잭션 안에서 불러야 한다.
 */
@Component
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PlanChangeEvidenceAssembler {

	private final AvailabilityChangeQueryService availabilityChangeQueryService;
	private final WishlistService wishlistService;
	private final OttServiceQueryService ottServiceQueryService;

	/**
	 * 이전 플랜 이후의 제공 상태 변경, 이전 플랜 저장 뒤 다 봤음으로 체크한 시청 단위, 서비스 이름을 모은다.
	 * 변경 조회 범위는 이전·새 플랜에 나오는 시청 단위로 좁힌다.
	 */
	public PlanChangeEvidence assemble(Long userId, PlanSnapshot previous, PlanSnapshot current) {
		// 1. 두 플랜에 나오는 시청 단위를 모은다
		Set<Long> unitIds = new HashSet<>();
		for (PlanSnapshot.Unit unit : previous.units()) {
			unitIds.add(unit.watchUnitId());
		}
		for (PlanSnapshot.Unit unit : current.units()) {
			unitIds.add(unit.watchUnitId());
		}

		// 2. 이전 플랜의 데이터 기준 시각 이후 제공 상태 변경을 "있음 여부"로 바꾼다(신규는 변경 전 있음 아님)
		List<PlanChangeEvidence.AvailabilityShift> shifts = new ArrayList<>();
		for (UnitAvailabilityShift shift : availabilityChangeQueryService.findShiftsAfter(unitIds, previous.dataAsOf())) {
			shifts.add(new PlanChangeEvidence.AvailabilityShift(shift.watchUnitId(), shift.ottServiceId(),
					shift.beforeStatus() == AvailabilityStatus.AVAILABLE,
					shift.afterStatus() == AvailabilityStatus.AVAILABLE, shift.changedAt()));
		}

		// 3. 이전 플랜 저장 뒤 다 봤음으로 체크된 시청 단위
		Set<Long> watched = wishlistService.listWatchedUnitIdsAfter(userId, previous.activatedAt());

		// 4. 서비스 이름
		Map<Long, String> serviceNames = new HashMap<>();
		for (OttService service : ottServiceQueryService.listOttServices()) {
			serviceNames.put(service.getId(), service.getName());
		}
		return new PlanChangeEvidence(shifts, watched, serviceNames);
	}
}
