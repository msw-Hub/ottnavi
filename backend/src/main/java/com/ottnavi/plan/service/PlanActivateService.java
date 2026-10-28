package com.ottnavi.plan.service;

import com.ottnavi.collect.service.CollectDataVersionService;
import com.ottnavi.plan.domain.entity.Plan;
import com.ottnavi.plan.domain.enums.PlanStatus;
import com.ottnavi.plan.domain.repository.PlanRepository;
import com.ottnavi.plan.exception.PlanDraftNotFoundException;
import com.ottnavi.plan.exception.PlanDraftStaleException;
import com.ottnavi.plan.service.model.SavedPlanView;
import com.ottnavi.user.service.UserQueryService;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 고른 DRAFT를 ACTIVE로 저장한다(Task 066, TECH 2절). 쓰기 트랜잭션 한 번에 사용자 행 잠금,
 * 기존 ACTIVE 보관, 새 ACTIVE 전환, 나머지 DRAFT 삭제를 묶어 중간 상태가 남지 않게 한다.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PlanActivateService {

	private final UserQueryService userQueryService;
	private final PlanRepository planRepository;
	private final CollectDataVersionService collectDataVersionService;
	private final Clock clock;
	private final ApplicationEventPublisher eventPublisher;

	/**
	 * 초안을 현재 플랜으로 저장한다.
	 * 없거나 남의 것이거나 이미 DRAFT가 아니면 404(PLAN_DRAFT_NOT_FOUND), 계산 뒤 수집이 새로 끝났으면 409(PLAN_DRAFT_STALE)이다.
	 */
	public SavedPlanView activate(Long userId, Long planId) {
		// 1. 사용자 행을 잠가 같은 사용자의 저장·재계산을 한 줄로 세운다
		userQueryService.lockUser(userId);

		// 2. 본인의 DRAFT만 찾는다. 남의 것·ARCHIVED·이미 ACTIVE·삭제된 것은 모두 같은 404로 응답한다
		Plan draft = planRepository.findByIdAndUserIdAndStatus(planId, userId, PlanStatus.DRAFT)
				.orElseThrow(() -> new PlanDraftNotFoundException("저장할 초안을 찾을 수 없다: planId=" + planId));

		// 3. 계산 뒤 수집이 새로 끝났으면 오래된 초안이라 저장하지 않는다(PRD 5.4, TECH 2절)
		if (isStale(draft.getDataAsOf(), collectDataVersionService.findDataAsOf())) {
			log.warn("오래된 초안 저장 시도: userId={}, planId={}, dataAsOf={}", userId, planId, draft.getDataAsOf());
			throw new PlanDraftStaleException("초안 계산 뒤 데이터가 갱신됐다: planId=" + planId);
		}

		// 4. 기존 ACTIVE를 보관하고 flush한다. 부분 유니크 인덱스(사용자당 ACTIVE 1개) 때문에 새 ACTIVE보다 먼저 반영해야 한다
		Long previousPlanId = null;
		Optional<Plan> active = planRepository.findByUserIdAndStatus(userId, PlanStatus.ACTIVE);
		if (active.isPresent()) {
			active.get().archive();
			previousPlanId = active.get().getId();
			planRepository.flush();
		}

		// 5. 초안을 ACTIVE로 바꾸고 이전 플랜을 방금 보관한 플랜으로 덮어쓴다
		Instant now = clock.instant();
		draft.activate(now, previousPlanId);
		planRepository.flush();

		// 6. 응답 값을 먼저 기록한다. 아래 벌크 삭제가 영속성 컨텍스트를 비우기 때문이다
		SavedPlanView view = new SavedPlanView(draft.getId(), draft.getPlanType(), now);

		// 7. 남은 DRAFT를 지운다(ACTIVE가 된 플랜은 status가 달라 대상이 아니다)
		planRepository.deleteDraftsByUserId(userId);

		// 8. 저장 사건을 알린다. 리스너(가입 안내 알림)는 이 트랜잭션이 커밋된 뒤에 별도 스레드에서 돌아, 저장 응답과 롤백에 영향을 주지 않는다
		eventPublisher.publishEvent(new PlanActivatedEvent(userId, view.planId()));

		log.info("플랜 저장: userId={}, planId={}, planType={}, previousPlanId={}", userId, view.planId(),
				view.planType(), previousPlanId);
		return view;
	}

	/**
	 * 초안이 오래됐는지 판정한다. 최신 수집 완료 시각이 있고 초안의 기준 시각이 그보다 이르면 오래된 것이다.
	 * 같으면 통과하고, 완료된 수집이 없으면 비교할 기준이 없어 통과한다.
	 */
	static boolean isStale(Instant draftDataAsOf, Optional<Instant> latestDataAsOf) {
		if (latestDataAsOf.isEmpty()) {
			return false;
		}
		return draftDataAsOf.isBefore(latestDataAsOf.get());
	}
}
