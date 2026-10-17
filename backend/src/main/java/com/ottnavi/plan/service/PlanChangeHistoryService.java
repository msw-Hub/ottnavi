package com.ottnavi.plan.service;

import com.ottnavi.plan.domain.entity.Plan;
import com.ottnavi.plan.domain.repository.PlanRepository;
import com.ottnavi.plan.domain.rule.PlanChangeSummary;
import com.ottnavi.plan.service.model.PlanChangeHistoryEntryView;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 내 플랜 변경 내역 조회(Task 069): 변경 요약이 기록된 플랜을 최신순으로 최대 6건 읽어 응답 값으로 만든다. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PlanChangeHistoryService {

	private static final int MAX_ITEMS = 6; // 목록은 최근 6건 고정, 페이지 없음(Task 068 결정 9)

	private final PlanRepository planRepository;
	private final PlanChangeSummaryCodec planChangeSummaryCodec;
	private final Clock clock;

	/** 변경 요약이 있는 내 플랜(ACTIVE·ARCHIVED)을 최신순으로 돌려준다. 없으면 빈 목록이다 */
	public List<PlanChangeHistoryEntryView> listRecent(Long userId) {
		List<PlanChangeHistoryEntryView> entries = new ArrayList<>();
		for (Plan plan : planRepository.findWithChangeSummary(userId, PageRequest.of(0, MAX_ITEMS))) {
			// 1. 저장된 JSON을 읽는다. 읽지 못하는 기록(모르는 버전·깨진 JSON)은 목록에서 뺀다
			Optional<PlanChangeSummary> summary = planChangeSummaryCodec.read(plan.getChangeSummary());
			if (summary.isEmpty()) {
				continue;
			}

			// 2. changeId는 새 플랜 ID, changedAt은 재계산으로 저장된 시각, dataAsOf는 한국 날짜다
			entries.add(new PlanChangeHistoryEntryView(plan.getId(), plan.getActivatedAt(), plan.getPlanType(),
					plan.getDataAsOf().atZone(clock.getZone()).toLocalDate(), summary.get()));
		}
		return entries;
	}
}
