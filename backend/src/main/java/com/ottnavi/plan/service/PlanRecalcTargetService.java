package com.ottnavi.plan.service;

import com.ottnavi.catalog.service.AvailabilityChangeQueryService;
import com.ottnavi.plan.domain.entity.Plan;
import com.ottnavi.plan.domain.enums.PlanStatus;
import com.ottnavi.plan.domain.repository.PlanItemRepository;
import com.ottnavi.plan.domain.repository.PlanRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매월 재계산 대상 플랜을 고른다(Task 069 결정 5, R-19).
 * ACTIVE 플랜 중 (1) 첫 달이 이번 달(KST) 1일보다 앞이거나 (2) 플랜 항목의 시청 단위 중 data_as_of 이후 제공 상태가 바뀐 것이 있으면 대상이다.
 * 제공 상태 비교는 다른 도메인 리포지토리를 쓰지 않으려고 시청 단위별 최근 변경 시각을 서비스로 받아 Java에서 한다.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PlanRecalcTargetService {

	private final PlanRepository planRepository;
	private final PlanItemRepository planItemRepository;
	private final AvailabilityChangeQueryService availabilityChangeQueryService;
	private final Clock clock;

	/** 재계산할 ACTIVE 플랜 ID를 오름차순으로 돌려준다 */
	public List<Long> findTargetPlanIds() {
		// 1. 이번 달 1일(KST)과 ACTIVE 플랜 전체
		LocalDate thisMonth = LocalDate.now(clock).withDayOfMonth(1);
		List<Plan> actives = planRepository.findAllByStatus(PlanStatus.ACTIVE);

		// 2. 첫 달이 지난 플랜은 바로 대상이고, 나머지는 제공 상태 변경 여부를 본다
		Set<Long> targets = new HashSet<>();
		List<Plan> needCheck = new ArrayList<>();
		for (Plan plan : actives) {
			if (plan.getStartMonth().isBefore(thisMonth)) {
				targets.add(plan.getId());
			} else {
				needCheck.add(plan);
			}
		}

		// 3. 확인이 필요한 플랜의 시청 단위와 단위별 최근 변경 시각을 한 번에 모은다
		if (!needCheck.isEmpty()) {
			List<Long> checkIds = new ArrayList<>();
			for (Plan plan : needCheck) {
				checkIds.add(plan.getId());
			}
			Map<Long, Set<Long>> unitsByPlan = new HashMap<>();
			Set<Long> allUnitIds = new HashSet<>();
			for (Object[] row : planItemRepository.findUnitIdsByPlanIds(checkIds)) {
				Long planId = (Long) row[0];
				Long unitId = (Long) row[1];
				Set<Long> units = unitsByPlan.get(planId);
				if (units == null) {
					units = new HashSet<>();
					unitsByPlan.put(planId, units);
				}
				units.add(unitId);
				allUnitIds.add(unitId);
			}
			Map<Long, Instant> latestChange = availabilityChangeQueryService.findLatestChangedAtByUnitIds(allUnitIds);

			// 4. data_as_of보다 늦게 바뀐 단위가 하나라도 있으면 대상이다
			for (Plan plan : needCheck) {
				Set<Long> unitIds = unitsByPlan.get(plan.getId());
				if (unitIds == null) {
					continue;
				}
				for (Long unitId : unitIds) {
					Instant changedAt = latestChange.get(unitId);
					if (changedAt != null && changedAt.isAfter(plan.getDataAsOf())) {
						targets.add(plan.getId());
						break;
					}
				}
			}
		}

		List<Long> sorted = new ArrayList<>(targets);
		sorted.sort(null);
		log.info("재계산 대상 플랜을 골랐다: ACTIVE={}건, 대상={}건", actives.size(), sorted.size());
		return sorted;
	}
}
