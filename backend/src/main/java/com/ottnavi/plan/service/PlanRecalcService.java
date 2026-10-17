package com.ottnavi.plan.service;

import com.ottnavi.engine.InputHasher;
import com.ottnavi.global.error.FieldValidationException;
import com.ottnavi.plan.domain.entity.Plan;
import com.ottnavi.plan.domain.enums.PlanStatus;
import com.ottnavi.plan.domain.repository.PlanRepository;
import com.ottnavi.plan.service.model.PlanCalcResult;
import com.ottnavi.plan.service.model.PlanCalcResults;
import com.ottnavi.plan.service.model.PlanInputBundle;
import com.ottnavi.plan.service.model.PlanRecalcOutcome;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 플랜 한 건의 매월 재계산 진입점(Task 069): 읽기 → 엔진 계산 → 쓰기 트랜잭션을 이어 붙인다.
 * 클래스에 @Transactional을 붙이지 않는다(PlanCalculateService와 같은 이유). 엔진 계산(CPU)이 트랜잭션 안에서 DB 연결을 쥐지 않게 하고,
 * 읽기는 PlanInputService, 쓰기는 PlanRecalcWriter가 각자 트랜잭션을 연다. 서로 다른 빈이라 프록시를 거쳐 트랜잭션이 적용된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanRecalcService {

	private final PlanRepository planRepository;
	private final PlanInputService planInputService;
	private final PlanSolveService planSolveService;
	private final PlanRecalcWriter planRecalcWriter;
	private final Clock clock;

	/**
	 * ACTIVE 플랜 한 건을 같은 유형으로 다시 계산해 새 ACTIVE로 갱신한다.
	 * 이미 ACTIVE가 아니거나(그 사이 사용자가 저장함), 다 보지 않은 찜이 없어 계산할 수 없으면 건너뛴다.
	 * 사용자가 직접 저장한 플랜과의 경합은 쓰기 단계에서 users 행 잠금 뒤 한 번 더 확인한다.
	 */
	public PlanRecalcOutcome recalcOne(long planId) {
		// 1. 대상 플랜이 아직 ACTIVE인지 확인한다. 선정 뒤 사용자가 직접 저장해 보관됐다면 건너뛴다
		Optional<Plan> found = planRepository.findById(planId);
		if (found.isEmpty() || found.get().getStatus() != PlanStatus.ACTIVE) {
			log.info("재계산을 건너뛴다(더 이상 ACTIVE가 아님): planId={}", planId);
			return PlanRecalcOutcome.SKIPPED;
		}
		Plan previous = found.get();
		Long userId = previous.getUserId();

		// 2. 입력을 모은다. 설정이 없거나 다 보지 않은 찜이 없으면 계산할 수 없어 건너뛴다(결정 9)
		PlanInputBundle bundle;
		try {
			bundle = planInputService.assemble(userId);
		} catch (FieldValidationException e) {
			log.info("재계산을 건너뛴다(계산할 입력이 없음): planId={}, userId={}, 사유={}", planId, userId, e.getMessage());
			return PlanRecalcOutcome.SKIPPED;
		}

		// 3. 세 유형을 풀고(같은 입력이면 캐시, 트랜잭션 밖) 이전 플랜과 같은 유형의 결과만 쓴다
		LocalDate dataAsOfDate = bundle.dataAsOf().atZone(clock.getZone()).toLocalDate();
		String inputHash = InputHasher.hash(bundle.planInput(), dataAsOfDate);
		PlanCalcResults results = planSolveService.solve(inputHash, bundle.planInput());
		PlanCalcResult mine = null;
		for (PlanCalcResult result : results.results()) {
			if (result.planType() == previous.getPlanType()) {
				mine = result;
			}
		}
		if (mine == null) {
			throw new IllegalStateException("이전 플랜 유형의 계산 결과가 없다: planType=" + previous.getPlanType());
		}

		// 4. 쓰기 트랜잭션 한 번으로 보관 → 새 ACTIVE → 변경 요약 → ARCHIVED 정리를 묶는다
		return planRecalcWriter.write(userId, previous.getId(), inputHash, bundle, mine);
	}
}
