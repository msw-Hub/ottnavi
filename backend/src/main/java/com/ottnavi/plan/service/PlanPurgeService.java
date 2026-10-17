package com.ottnavi.plan.service;

import com.ottnavi.plan.domain.repository.CalcRunRepository;
import com.ottnavi.plan.domain.repository.PlanRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 데이터 정리(Task 083)가 플랜 쪽에서 하는 일: TMDB에서 사라진 작품이 들어 있는 플랜 정리와 오래된 계산 기록 삭제.
 * 플랜 삭제는 벌크 JPQL이고 하위 행(월·상품·항목·배정)은 DB FK CASCADE가 지운다(TECH T-1).
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PlanPurgeService {

	private final PlanRepository planRepository;
	private final CalcRunRepository calcRunRepository;

	/** 해당 시청 단위가 항목에 들어 있는 ACTIVE 플랜 ID. 찜을 지운 뒤 다시 계산할 대상이다 */
	public List<Long> findActivePlanIds(Collection<Long> watchUnitIds) {
		if (watchUnitIds.isEmpty()) {
			return List.of();
		}
		return planRepository.findActiveIdsByWatchUnitIds(watchUnitIds);
	}

	/** 해당 시청 단위가 들어 있는 DRAFT를 지운다. 저장 전 임시 계산이라 사용자가 다시 계산하면 된다 */
	@Transactional
	public int deleteDrafts(Collection<Long> watchUnitIds) {
		if (watchUnitIds.isEmpty()) {
			return 0;
		}
		int deleted = planRepository.deleteDraftsByWatchUnitIds(watchUnitIds);
		log.info("사라진 작품이 든 DRAFT 플랜을 삭제했다: 시청 단위={}개, 삭제 건수={}", watchUnitIds.size(), deleted);
		return deleted;
	}

	/**
	 * 해당 시청 단위가 들어 있는 남은 플랜을 상태와 상관없이 통째로 지운다.
	 * 재계산이 끝난 뒤 이전 ACTIVE(ARCHIVED)와 재계산하지 못한 ACTIVE가 여기서 지워진다. 항목만 지우면 합계·점수가 어긋나기 때문이다.
	 */
	@Transactional
	public int deleteAllPlans(Collection<Long> watchUnitIds) {
		if (watchUnitIds.isEmpty()) {
			return 0;
		}
		int deleted = planRepository.deleteByWatchUnitIds(watchUnitIds);
		log.info("사라진 작품이 든 플랜을 통째로 삭제했다: 시청 단위={}개, 삭제 건수={}", watchUnitIds.size(), deleted);
		return deleted;
	}

	/** 플랜과 연결이 끊긴 계산 기록(calc_run) 중 cutoff보다 오래된 것을 지우고 지운 건수를 돌려준다 */
	@Transactional
	public int deleteOldDetachedCalcRuns(Instant cutoff) {
		int deleted = calcRunRepository.deleteDetachedBefore(cutoff);
		log.info("오래된 계산 기록을 삭제했다: 기준={}, 삭제 건수={}", cutoff, deleted);
		return deleted;
	}
}
