package com.ottnavi.plan.service;

import com.ottnavi.engine.model.PlanType;
import com.ottnavi.plan.domain.entity.Plan;
import com.ottnavi.plan.domain.enums.PlanStatus;
import com.ottnavi.plan.domain.repository.PlanRepository;
import com.ottnavi.plan.service.model.ActivePlanView;
import com.ottnavi.plan.service.model.PlanDraftView;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 내 플랜 조회(Task 066): 지금 있는 초안(DRAFT)과 현재 플랜(ACTIVE)을 DB에서 읽어 응답 값으로 만든다. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PlanQueryService {

	private final PlanRepository planRepository;
	private final PlanViewLoader planViewLoader;

	/** 내 초안을 플랜 유형 순서(추천형·절약형·간편형)로 돌려준다. 계산 전이거나 저장한 뒤에는 빈 목록이다. */
	public List<PlanDraftView> listDrafts(Long userId) {
		// 1. DRAFT를 읽어 플랜 유형 선언 순서로 정렬한다
		List<Plan> drafts = planRepository.findAllByUserIdAndStatus(userId, PlanStatus.DRAFT);
		List<Plan> ordered = new ArrayList<>();
		for (PlanType planType : PlanType.values()) {
			for (Plan draft : drafts) {
				if (draft.getPlanType() == planType) {
					ordered.add(draft);
				}
			}
		}

		// 2. 응답 값으로 바꾼다
		return planViewLoader.load(ordered);
	}

	/** 내 현재 플랜(ACTIVE)을 돌려준다. 없으면 빈 값이다(404가 아니다). */
	public Optional<ActivePlanView> getCurrent(Long userId) {
		// 1. ACTIVE는 사용자당 최대 1건이다
		Optional<Plan> active = planRepository.findByUserIdAndStatus(userId, PlanStatus.ACTIVE);
		if (active.isEmpty()) {
			return Optional.empty();
		}

		// 2. 초안과 같은 방식으로 내용을 만들고 저장 시각을 붙인다
		Plan plan = active.get();
		List<Plan> single = new ArrayList<>();
		single.add(plan);
		PlanDraftView view = planViewLoader.load(single).get(0);
		return Optional.of(new ActivePlanView(view, plan.getActivatedAt()));
	}

	/**
	 * ACTIVE 플랜을 가진 사용자 수를 센다. 관리자·데모 계정처럼 user 도메인이 알려 준 ID는 뺀다.
	 * 목록이 비면 NOT IN 없이 전체를 센다(빈 컬렉션 바인딩 동작에 기대지 않는다).
	 */
	public long countActivePlanUsers(Collection<Long> excludedUserIds) {
		if (excludedUserIds == null || excludedUserIds.isEmpty()) {
			return planRepository.countActivePlanUsers();
		}
		return planRepository.countActivePlanUsersExcluding(excludedUserIds);
	}
}
