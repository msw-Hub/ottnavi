package com.ottnavi.retention.service;

import com.ottnavi.catalog.service.TitlePurgeService;
import com.ottnavi.catalog.service.model.StaleTitle;
import com.ottnavi.infra.tmdb.exception.TmdbException;
import com.ottnavi.plan.service.PlanPurgeService;
import com.ottnavi.plan.service.PlanRecalcService;
import com.ottnavi.retention.service.model.TitleExistence;
import com.ottnavi.retention.service.model.TitleRetentionResult;
import com.ottnavi.wishlist.service.WishlistService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 마지막 수집이 오래된 작품 한 건을 처리한다(Task 083, R-10): TMDB에 다시 물어 있으면 갱신하고, 사라졌으면 찜·플랜을 정리하며 삭제한다.
 * 클래스에 @Transactional을 붙이지 않는다. TMDB 호출을 트랜잭션 밖에서 하고, 삭제 단계마다 각 도메인 서비스가 자기 트랜잭션을 연다.
 * 단계가 나뉘어 있어도 어느 지점에서 멈추든 다음 실행이 같은 작품을 다시 처리하면 이어진다(영향받는 ACTIVE 플랜은 찜이 아니라 플랜 항목으로 찾기 때문이다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TitleRetentionService {

	private final TitleExistenceProbe titleExistenceProbe;
	private final TitleRefresher titleRefresher;
	private final TitlePurgeService titlePurgeService;
	private final WishlistService wishlistService;
	private final PlanPurgeService planPurgeService;
	private final PlanRecalcService planRecalcService;

	/**
	 * 작품 한 건을 TMDB에 다시 물어 갱신하거나 삭제한다.
	 * TMDB 일시 오류·재계산 실패·경합처럼 예상 가능한 실패는 DEFERRED 결과로 알리고, 그 밖의 예외는 호출하는 쪽이 작품 단위로 잡는다.
	 */
	public TitleRetentionResult handle(StaleTitle title) {
		// 1. TMDB에 작품이 있는지 먼저 확인한다
		TitleExistence existence = titleExistenceProbe.probe(title);

		// 2. 알 수 없으면 이번에는 건드리지 않는다
		if (existence == TitleExistence.UNKNOWN) {
			return TitleRetentionResult.DEFERRED;
		}

		// 3. 있으면 상세를 다시 받아 갱신한다
		if (existence == TitleExistence.EXISTS) {
			return refresh(title);
		}

		// 4. 사라졌으면 찜·플랜을 정리하고 삭제한다
		return purge(title);
	}

	private TitleRetentionResult refresh(StaleTitle title) {
		try {
			if (titleRefresher.refresh(title)) {
				return TitleRetentionResult.REFRESHED;
			}
			log.warn("TMDB에 작품은 있지만 상세를 갱신하지 못했다(시즌 조회 404 등): titleId={}, tmdbId={}", title.titleId(),
					title.tmdbId());
			return TitleRetentionResult.REFRESH_SKIPPED;
		} catch (TmdbException e) {
			log.warn("상세 갱신 중 TMDB 호출이 실패해 이번에는 건드리지 않는다: titleId={}, 사유={}", title.titleId(), e.getMessage());
			return TitleRetentionResult.DEFERRED;
		}
	}

	private TitleRetentionResult purge(StaleTitle title) {
		// 1. 지울 시청 단위를 모으고, 이 단위가 든 ACTIVE 플랜을 찜이 사라지기 전에 미리 찾아 둔다
		List<Long> unitIds = titlePurgeService.findWatchUnitIds(title.titleId());
		List<Long> activePlanIds = planPurgeService.findActivePlanIds(unitIds);

		// 2. 저장 전 초안(DRAFT)과 모든 사용자의 찜을 지운다. 찜이 먼저 없어져야 재계산 입력에서 이 작품이 빠진다
		planPurgeService.deleteDrafts(unitIds);
		wishlistService.removeByWatchUnitIds(unitIds);

		// 3. 영향받은 ACTIVE 플랜을 다시 계산한다. 일시 오류면 이번에는 여기서 멈추고 다음 실행에 이어서 처리한다
		for (Long planId : activePlanIds) {
			try {
				planRecalcService.recalcOne(planId);
			} catch (RuntimeException e) {
				log.error("사라진 작품의 ACTIVE 플랜 재계산에 실패해 삭제를 미룬다(다음 실행에서 다시 처리): titleId={}, planId={}",
						title.titleId(), planId, e);
				return TitleRetentionResult.DEFERRED;
			}
		}

		// 4. 이 단위가 남아 있는 플랜을 통째로 지운다: 재계산으로 보관된 이전 플랜과 재계산하지 못한 ACTIVE(찜이 하나뿐이었던 경우)
		planPurgeService.deleteAllPlans(unitIds);

		// 5. 작품과 하위 행을 지운다. 그 사이 사용자가 찜·플랜을 새로 만들었다면 FK 위반이라 이번에는 건너뛴다
		try {
			titlePurgeService.purge(title.titleId());
		} catch (DataIntegrityViolationException e) {
			log.warn("작품을 지우는 중 다른 요청이 같은 작품을 참조해 이번에는 건너뛴다(다음 실행에서 다시 처리): titleId={}",
					title.titleId(), e);
			return TitleRetentionResult.DEFERRED;
		}
		return TitleRetentionResult.PURGED;
	}
}
