package com.ottnavi.plan.service;

import com.ottnavi.engine.model.PlanType;
import com.ottnavi.plan.domain.entity.Plan;
import com.ottnavi.plan.domain.enums.PlanStatus;
import com.ottnavi.plan.domain.repository.PlanRepository;
import com.ottnavi.plan.service.model.PlanCalcResult;
import com.ottnavi.plan.service.model.PlanCalcResults;
import com.ottnavi.plan.service.model.PlanInputBundle;
import com.ottnavi.plan.service.model.SavedDraft;
import com.ottnavi.user.service.UserQueryService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계산 결과를 DRAFT 3건으로 저장한다(Task 064). 쓰기 트랜잭션 한 번에 사용자 행 잠금, 기존 DRAFT 삭제, 새 DRAFT 저장을 묶는다.
 * 계산(엔진)은 이 클래스 밖에서 끝난 상태로 들어오므로 트랜잭션이 DB 연결을 쥐는 시간은 INSERT 약 170건 분량이다.
 * 하위 행 저장은 PlanResultWriter가 맡는다(재계산과 공용, Task 069).
 * PlanCalculateService와 다른 빈이어야 @Transactional이 적용된다(같은 클래스 안 호출은 프록시를 거치지 않는다).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class PlanDraftWriter {

	private final UserQueryService userQueryService;
	private final PlanRepository planRepository;
	private final PlanResultWriter planResultWriter;

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
			Plan draft = Plan.createDraft(userId, previousPlanId, result.planType(), bundle.startMonth(),
					bundle.monthlyBudget(), bundle.monthlyWatchMinutes(), result.solverType(),
					PlanDraftMapper.mustTotal(bundle.planInput()), result.mustCompleted(), result.score(),
					result.totalCost(), bundle.allSubscribeCost(), bundle.dataAsOf());
			saved.add(planResultWriter.save(draft, inputHash, bundle, result));
		}
		if (saved.size() != PlanType.values().length) {
			throw new IllegalStateException("계산 결과의 플랜 유형 수가 맞지 않는다: size=" + saved.size());
		}
		return saved;
	}
}
