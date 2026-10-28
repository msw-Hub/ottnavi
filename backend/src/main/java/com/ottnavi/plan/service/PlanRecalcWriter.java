package com.ottnavi.plan.service;

import com.ottnavi.notification.domain.enums.NotificationType;
import com.ottnavi.notification.service.NotificationCreateService;
import com.ottnavi.notification.service.model.MonthlyPayload;
import com.ottnavi.notification.service.model.PlanChangePayload;
import com.ottnavi.plan.domain.entity.Plan;
import com.ottnavi.plan.domain.enums.PlanStatus;
import com.ottnavi.plan.domain.repository.PlanRepository;
import com.ottnavi.plan.domain.rule.PlanChangeAlertRule;
import com.ottnavi.plan.domain.rule.PlanChangeAlertRule.LeftTitle;
import com.ottnavi.plan.domain.rule.PlanChangeDetector;
import com.ottnavi.plan.domain.rule.PlanChangeEvidence;
import com.ottnavi.plan.domain.rule.PlanChangeSummary;
import com.ottnavi.plan.domain.rule.PlanSnapshot;
import com.ottnavi.plan.service.model.PlanCalcResult;
import com.ottnavi.plan.service.model.PlanInputBundle;
import com.ottnavi.plan.service.model.PlanRecalcOutcome;
import com.ottnavi.user.service.UserQueryService;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재계산 결과를 저장한다(Task 069, TECH 2절). 쓰기 트랜잭션 한 번에 사용자 행 잠금, 이전 ACTIVE 보관(명시 flush),
 * 새 ACTIVE 저장, 변경 요약 기록, 알림 행 생성(Task 073), 오래된 ARCHIVED 정리를 묶는다. DRAFT는 건드리지 않는다.
 * 계산(엔진)은 이 클래스 밖에서 끝난 상태로 들어온다.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PlanRecalcWriter {

	private static final int KEEP_CHANGE_COUNT = 6; // 변경 기록이 있는 플랜을 최근 몇 개까지 남기는지(결정 2, ACTIVE 포함)

	private final UserQueryService userQueryService;
	private final PlanRepository planRepository;
	private final PlanResultWriter planResultWriter;
	private final PlanSnapshotLoader planSnapshotLoader;
	private final PlanChangeEvidenceAssembler planChangeEvidenceAssembler;
	private final PlanChangeSummaryCodec planChangeSummaryCodec;
	private final PlanNotificationComposer notificationComposer;
	private final NotificationCreateService notificationCreateService;
	private final Clock clock;

	/**
	 * 이전 ACTIVE를 보관하고 계산 결과로 새 ACTIVE를 만든다.
	 * 읽기 단계 뒤에 사용자가 직접 저장해 ACTIVE가 바뀌었으면 건너뛴다(그 플랜이 더 최신이다).
	 *
	 * @param previousPlanId 읽기 단계에서 본 ACTIVE 플랜 ID
	 */
	public PlanRecalcOutcome write(Long userId, Long previousPlanId, String inputHash, PlanInputBundle bundle,
			PlanCalcResult result) {
		// 1. 사용자 행을 잠가 같은 사용자의 직접 저장·계산과 한 줄로 세운다
		userQueryService.lockUser(userId);

		// 2. 읽을 때 본 ACTIVE가 그대로여야 한다. 달라졌으면(직접 저장 등) 건너뛴다
		Optional<Plan> active = planRepository.findByUserIdAndStatus(userId, PlanStatus.ACTIVE);
		if (active.isEmpty() || !active.get().getId().equals(previousPlanId)) {
			log.info("재계산을 건너뛴다(읽은 뒤 ACTIVE가 바뀜): userId={}, 읽은 planId={}", userId, previousPlanId);
			return PlanRecalcOutcome.SKIPPED;
		}
		Plan previous = active.get();

		// 3. 이전 ACTIVE를 보관하고 flush한다. 부분 유니크 인덱스(사용자당 ACTIVE 1개) 때문에 새 ACTIVE보다 먼저 반영해야 한다
		previous.archive();
		planRepository.flush();

		// 4. 새 ACTIVE를 만들어 하위 행까지 저장한다(DRAFT 없이 바로 ACTIVE, previous_plan_id는 보관한 플랜)
		Plan next = Plan.createRecalculated(userId, previousPlanId, result.planType(), bundle.startMonth(),
				bundle.monthlyBudget(), bundle.monthlyWatchMinutes(), result.solverType(),
				PlanDraftMapper.mustTotal(bundle.planInput()), result.mustCompleted(), result.score(),
				result.totalCost(), bundle.allSubscribeCost(), bundle.dataAsOf(), clock.instant());
		planResultWriter.save(next, inputHash, bundle, result);

		// 5. 이전·새 플랜을 비교해 기록 조건을 채우면 변경 요약을 남긴다(결정 4: 이유가 없으면 남기지 않는다)
		List<Plan> both = new ArrayList<>();
		both.add(previous);
		both.add(next);
		Map<Long, PlanSnapshot> snapshots = planSnapshotLoader.load(both);
		PlanSnapshot previousSnapshot = snapshots.get(previous.getId());
		PlanSnapshot nextSnapshot = snapshots.get(next.getId());
		PlanChangeEvidence evidence = planChangeEvidenceAssembler.assemble(userId, previousSnapshot, nextSnapshot);
		Optional<PlanChangeSummary> summary = PlanChangeDetector.detect(previousSnapshot, nextSnapshot, evidence);
		if (summary.isPresent()) {
			next.recordChangeSummary(planChangeSummaryCodec.write(summary.get()));
		}
		Long nextId = next.getId();

		// 6. 알림 행을 같은 트랜잭션에서 만든다(이 재계산이 되돌아가면 알림도 함께 되돌아간다). 네이티브 삽입 전에 flush한다
		planRepository.flush();
		createNotifications(userId, previous, next, previousSnapshot, nextSnapshot, evidence, summary, bundle);

		// 7. 오래된 ARCHIVED를 정리한다. 이 벌크 삭제가 영속성 컨텍스트를 비우므로 가장 마지막에 한다
		int deleted = deleteOldArchived(userId, previousPlanId);

		log.info("재계산 완료: userId={}, 이전 planId={}, 새 planId={}, 변경 요약={}, 정리한 ARCHIVED={}건",
				userId, previousPlanId, nextId, summary.isPresent(), deleted);
		return PlanRecalcOutcome.UPDATED;
	}

	/**
	 * 재계산 결과로 알림 행을 만든다(Task 073). 알림이 꺼진 사용자·이미 만든 키는 생성 관문이 걸러 행이 늘지 않는다.
	 *
	 * <pre>
	 * 월이 바뀐 재계산(이전 첫 달 &lt; 새 첫 달)               → MONTHLY (MONTHLY:{userId}:{YYYY-MM}, 달마다 1회)
	 * 월이 그대로이고 변경 요약이 있고 이번 달 작품이 빠짐   → PLAN_CHANGE (PLAN_CHANGE:{userId}:{새 planId})
	 * 그 밖(월 중간 재계산에서 변화 없음·다음 달 작품만 빠짐)  → 만들지 않음
	 * </pre>
	 */
	private void createNotifications(Long userId, Plan previous, Plan next, PlanSnapshot previousSnapshot,
			PlanSnapshot nextSnapshot, PlanChangeEvidence evidence, Optional<PlanChangeSummary> summary,
			PlanInputBundle bundle) {
		// 1. 월이 바뀐 재계산이면 월초 안내를 만든다. 변경 요약이 있으면 이유 문장도 함께 담는다
		if (previous.getStartMonth().isBefore(bundle.startMonth())) {
			MonthlyPayload payload = notificationComposer.composeMonthly(userId, next, summary);
			String dedupeKey = "MONTHLY:" + userId + ":" + YearMonth.from(bundle.startMonth());
			notificationCreateService.create(userId, NotificationType.MONTHLY, dedupeKey, payload);
			return;
		}

		// 2. 월 중간 재계산은 변경 요약이 있을 때만 알림 후보가 된다
		if (summary.isEmpty()) {
			return;
		}

		// 3. 이번 달 확정 달에 배정됐던 작품이 그 서비스에서 빠졌을 때만 변경 알림을 만든다
		List<LeftTitle> left = PlanChangeAlertRule.findLeftTitles(previousSnapshot, bundle.startMonth(), evidence);
		if (left.isEmpty()) {
			return;
		}
		PlanChangePayload payload = notificationComposer.composePlanChange(userId, next, summary.get(), left);
		String dedupeKey = "PLAN_CHANGE:" + userId + ":" + next.getId();
		notificationCreateService.create(userId, NotificationType.PLAN_CHANGE, dedupeKey, payload);
	}

	/**
	 * 변경 기록이 있는 플랜(ACTIVE 포함) 최근 6개와 바로 이전 플랜 1개만 남기고 나머지 ARCHIVED를 벌크로 지운다(결정 2).
	 * 변경 요약 조회는 자동 flush로 방금 기록한 요약까지 보인다.
	 */
	private int deleteOldArchived(Long userId, Long previousPlanId) {
		Set<Long> keepIds = new HashSet<>();
		for (Plan plan : planRepository.findWithChangeSummary(userId, PageRequest.of(0, KEEP_CHANGE_COUNT))) {
			keepIds.add(plan.getId());
		}
		keepIds.add(previousPlanId);
		return planRepository.deleteArchivedExcept(userId, keepIds);
	}
}
