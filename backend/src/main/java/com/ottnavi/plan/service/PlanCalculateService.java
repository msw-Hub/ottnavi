package com.ottnavi.plan.service;

import com.ottnavi.engine.InputHasher;
import com.ottnavi.plan.service.model.PlanCalcResults;
import com.ottnavi.plan.service.model.PlanDraftView;
import com.ottnavi.plan.service.model.PlanInputBundle;
import com.ottnavi.plan.service.model.SavedDraft;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 플랜 계산 진입점(Task 064): 입력 조립 → 입력 해시 → 엔진 계산(캐시) → DRAFT 저장 → 응답 값 조립을 이어 붙인다.
 * 클래스에 @Transactional을 붙이지 않는다. backend.md의 "서비스는 readOnly 트랜잭션 기본"과 달리, 엔진 계산(CPU)이
 * 트랜잭션 안에 들어가 DB 연결을 쥐고 있지 않게 하려는 것이다. 읽기는 PlanInputService, 쓰기는 PlanDraftWriter가 각자 트랜잭션을 연다.
 * 세 단계가 서로 다른 빈이라 프록시를 거쳐 각 트랜잭션이 적용된다(같은 클래스 안 호출 금지).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanCalculateService {

	private static final String TIMER_NAME = "plan.calculate"; // 계산 전체(입력 조립~저장) 소요 시간 타이머

	private final PlanInputService planInputService;
	private final PlanSolveService planSolveService;
	private final PlanDraftWriter planDraftWriter;
	private final MeterRegistry meterRegistry;
	private final Clock clock;

	/**
	 * 사용자의 DRAFT 3종(추천형·절약형·간편형)을 계산해 저장하고 응답 값을 돌려준다.
	 * 설정이 없거나 다 보지 않은 찜이 없으면 400(VALIDATION_FAILED)이다. 예외가 나면 기존 DRAFT는 그대로 남는다.
	 */
	public List<PlanDraftView> calculate(Long userId) {
		Timer.Sample sample = Timer.start(meterRegistry);
		long startedNanos = System.nanoTime();
		try {
			// 1. DB의 설정·찜·요금을 엔진 입력으로 모은다(읽기 트랜잭션)
			PlanInputBundle bundle = planInputService.assemble(userId);

			// 2. 입력 해시를 만든다. 기준일은 데이터 기준 시각의 한국 날짜다
			LocalDate dataAsOfDate = bundle.dataAsOf().atZone(clock.getZone()).toLocalDate();
			String inputHash = InputHasher.hash(bundle.planInput(), dataAsOfDate);

			// 3. 세 유형을 계산한다(같은 해시면 캐시에서 온다. 트랜잭션 밖)
			PlanCalcResults results = planSolveService.solve(inputHash, bundle.planInput());

			// 4. 기존 DRAFT를 지우고 새 DRAFT를 저장한다(쓰기 트랜잭션 한 번)
			List<SavedDraft> saved = planDraftWriter.saveDrafts(userId, inputHash, bundle, results);

			// 5. DB를 다시 읽지 않고 응답 값을 조립한다
			List<PlanDraftView> views = PlanDraftViewAssembler.assemble(bundle, results, saved, clock.getZone());

			long elapsedMs = (System.nanoTime() - startedNanos) / 1_000_000;
			log.info("플랜 계산 완료: userId={}, inputHash={}, 소요={}ms", userId, inputHash.substring(0, 8), elapsedMs);
			return views;
		} finally {
			sample.stop(meterRegistry.timer(TIMER_NAME));
		}
	}
}
