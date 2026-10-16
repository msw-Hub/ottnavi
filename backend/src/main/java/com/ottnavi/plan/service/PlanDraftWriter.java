package com.ottnavi.plan.service;

import com.ottnavi.engine.model.PlanType;
import com.ottnavi.engine.model.WatchUnit;
import com.ottnavi.plan.domain.entity.CalcRun;
import com.ottnavi.plan.domain.entity.Plan;
import com.ottnavi.plan.domain.entity.PlanAssignment;
import com.ottnavi.plan.domain.entity.PlanItem;
import com.ottnavi.plan.domain.entity.PlanMonth;
import com.ottnavi.plan.domain.entity.PlanMonthProduct;
import com.ottnavi.plan.domain.enums.PlanStatus;
import com.ottnavi.plan.domain.repository.CalcRunRepository;
import com.ottnavi.plan.domain.repository.PlanAssignmentRepository;
import com.ottnavi.plan.domain.repository.PlanItemRepository;
import com.ottnavi.plan.domain.repository.PlanMonthProductRepository;
import com.ottnavi.plan.domain.repository.PlanMonthRepository;
import com.ottnavi.plan.domain.repository.PlanRepository;
import com.ottnavi.plan.service.PlanDraftMapper.MonthDraft;
import com.ottnavi.plan.service.PlanDraftMapper.MonthProduct;
import com.ottnavi.plan.service.model.PlanCalcResult;
import com.ottnavi.plan.service.model.PlanCalcResults;
import com.ottnavi.plan.service.model.PlanInputBundle;
import com.ottnavi.plan.service.model.ProductPriceInfo;
import com.ottnavi.plan.service.model.SavedDraft;
import com.ottnavi.user.service.UserQueryService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계산 결과를 DRAFT 3건으로 저장한다(Task 064). 쓰기 트랜잭션 한 번에 사용자 행 잠금, 기존 DRAFT 삭제, 새 DRAFT 저장을 묶는다.
 * 계산(엔진)은 이 클래스 밖에서 끝난 상태로 들어오므로 트랜잭션이 DB 연결을 쥐는 시간은 INSERT 약 170건 분량이다.
 * PlanCalculateService와 다른 빈이어야 @Transactional이 적용된다(같은 클래스 안 호출은 프록시를 거치지 않는다).
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PlanDraftWriter {

	private final UserQueryService userQueryService;
	private final PlanRepository planRepository;
	private final PlanMonthRepository planMonthRepository;
	private final PlanItemRepository planItemRepository;
	private final PlanMonthProductRepository planMonthProductRepository;
	private final PlanAssignmentRepository planAssignmentRepository;
	private final CalcRunRepository calcRunRepository;

	/**
	 * 사용자의 기존 DRAFT를 지우고 새 DRAFT를 유형마다 하나씩 저장한다.
	 * 중간에 예외가 나면 삭제까지 모두 롤백되어 기존 DRAFT가 그대로 남는다.
	 * data_as_of는 입력 조립 시점의 값(bundle.dataAsOf)을 그대로 저장한다. 쓰기 시점에 새로 읽으면
	 * 계산에 쓴 데이터와 기준 시각이 어긋나 저장 단계(Task 066)의 409 안전장치가 무너진다.
	 *
	 * @param inputHash calc_run에 남길 입력 해시
	 */
	public List<SavedDraft> saveDrafts(Long userId, String inputHash, PlanInputBundle bundle, PlanCalcResults results) {
		// 1. 사용자 행을 잠가 같은 사용자의 동시 계산을 한 줄로 세운다. 나중 요청이 자기 결과로 덮어쓴다
		userQueryService.lockUser(userId);

		// 2. 기존 DRAFT를 세 유형 모두 지운다. 잠금 직후 첫 쓰기 문장이라 유니크 인덱스 위반이 생기지 않는다
		// 하위 월·상품·항목·배정은 DB FK CASCADE가 지운다(TECH T-1)
		planRepository.deleteDraftsByUserId(userId);

		// 3. 현재 ACTIVE 플랜 ID를 이전 플랜으로 기록한다(없으면 null)
		Long previousPlanId = null;
		Optional<Plan> active = planRepository.findByUserIdAndStatus(userId, PlanStatus.ACTIVE);
		if (active.isPresent()) {
			previousPlanId = active.get().getId();
		}

		// 4. 세 유형을 차례로 저장한다
		List<SavedDraft> saved = new ArrayList<>();
		Set<PlanType> seen = new HashSet<>();
		for (PlanCalcResult result : results.results()) {
			if (!seen.add(result.planType())) {
				throw new IllegalStateException("플랜 유형이 중복됐다: planType=" + result.planType());
			}
			saved.add(saveOne(userId, previousPlanId, inputHash, bundle, result));
		}
		if (saved.size() != PlanType.values().length) {
			throw new IllegalStateException("계산 결과의 플랜 유형 수가 맞지 않는다: size=" + saved.size());
		}
		return saved;
	}

	/** 유형 하나를 plan → 달 → 항목 → 달별 상품 → 배정 → calc_run 순서로 저장한다. */
	private SavedDraft saveOne(Long userId, Long previousPlanId, String inputHash, PlanInputBundle bundle,
			PlanCalcResult result) {
		// 1. 달별 상품·비용을 만들고 세 달 비용의 합이 총비용과 같은지 확인한다(저장 전에 막는다)
		List<MonthDraft> monthDrafts = PlanDraftMapper.months(bundle, result);
		int costSum = 0;
		for (MonthDraft monthDraft : monthDrafts) {
			costSum += monthDraft.cost();
		}
		if (costSum != result.totalCost()) {
			log.error("달 비용 합이 총비용과 다르다: userId={}, planType={}, 달 비용 합={}, 총비용={}",
					userId, result.planType(), costSum, result.totalCost());
			throw new IllegalStateException("달 비용 합이 총비용과 다르다: planType=" + result.planType());
		}

		// 2. 플랜을 저장한다. 데이터 기준 시각은 조립 시점의 값이다
		Plan plan = planRepository.save(Plan.createDraft(userId, previousPlanId, result.planType(), bundle.startMonth(),
				bundle.monthlyBudget(), bundle.monthlyWatchMinutes(), result.solverType(),
				PlanDraftMapper.mustTotal(bundle.planInput()), result.mustCompleted(), result.score(), result.totalCost(),
				bundle.allSubscribeCost(), bundle.dataAsOf()));

		// 3. 달을 저장한다. 배정이 이 플랜의 달만 가리키도록 monthIndex로 찾을 수 있게 모아 둔다
		Map<Integer, PlanMonth> monthsByIndex = new HashMap<>();
		for (MonthDraft monthDraft : monthDrafts) {
			PlanMonth planMonth = planMonthRepository
					.save(PlanMonth.create(plan, monthDraft.monthIndex(), monthDraft.month(), monthDraft.cost()));
			monthsByIndex.put(monthDraft.monthIndex(), planMonth);
		}

		// 4. 시청 단위마다 항목을 저장한다(배정 여부와 이유는 엔진 결과 그대로)
		Map<Long, PlanCalcResult.Unit> resultUnits = PlanDraftMapper.unitsById(result);
		Map<Long, PlanItem> itemsByUnit = new HashMap<>();
		for (WatchUnit input : bundle.planInput().units()) {
			PlanCalcResult.Unit unit = resultUnits.get(input.id());
			if (unit == null) {
				throw new IllegalStateException("입력 단위의 계산 결과가 없다: unitId=" + input.id());
			}
			PlanItem item;
			if (unit.scheduled()) {
				item = PlanItem.scheduled(plan, input.id(), input.priority(), unit.completedMonthIndex());
			} else {
				item = PlanItem.unscheduled(plan, input.id(), input.priority(), unit.reason());
			}
			itemsByUnit.put(input.id(), planItemRepository.save(item));
		}

		// 5. 달별 상품을 저장한다
		for (MonthDraft monthDraft : monthDrafts) {
			PlanMonth planMonth = monthsByIndex.get(monthDraft.monthIndex());
			for (MonthProduct product : monthDraft.products()) {
				planMonthProductRepository.save(PlanMonthProduct.create(planMonth, product.productId(),
						product.appliedPrice(), product.priceSource()));
			}
		}

		// 6. 배정을 저장한다. 같은 유형(같은 Plan)에서 만든 항목·달만 짝지어 소속이 어긋나지 않게 한다
		for (WatchUnit input : bundle.planInput().units()) {
			PlanCalcResult.Unit unit = resultUnits.get(input.id());
			PlanItem item = itemsByUnit.get(input.id());
			for (PlanCalcResult.Slot slot : unit.assignments()) {
				PlanMonth planMonth = monthsByIndex.get(slot.monthIndex());
				ProductPriceInfo info = bundle.productPrices().get(slot.productId());
				if (planMonth == null || info == null) {
					throw new IllegalStateException("배정의 달 또는 상품을 찾지 못했다: unitId=" + input.id()
							+ ", monthIndex=" + slot.monthIndex() + ", productId=" + slot.productId());
				}
				planAssignmentRepository.save(PlanAssignment.create(item, planMonth, info.ottServiceId(), slot.minutes()));
			}
		}

		// 7. 계산 실행 기록을 남긴다. 캐시에서 온 결과여도 원래 계산의 후보 수·소요 시간을 그대로 쓴다
		calcRunRepository.save(CalcRun.create(plan.getId(), inputHash, result.planType(), result.solverType(),
				result.mustCompleted(), result.score(), result.totalCost(), result.candidateCount(), result.elapsedMs()));

		// 8. 응답 조립에 쓸 ID 묶음을 돌려준다
		Map<Long, Long> planItemIdByUnit = new HashMap<>();
		for (Map.Entry<Long, PlanItem> entry : itemsByUnit.entrySet()) {
			planItemIdByUnit.put(entry.getKey(), entry.getValue().getId());
		}
		return new SavedDraft(result.planType(), plan.getId(), planItemIdByUnit);
	}
}
