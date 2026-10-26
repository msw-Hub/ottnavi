package com.ottnavi.catalog.service;

import com.ottnavi.catalog.domain.repository.AvailabilityChangeRepository;
import com.ottnavi.catalog.domain.repository.AvailabilityRepository;
import com.ottnavi.catalog.domain.repository.TitleCastRepository;
import com.ottnavi.catalog.domain.repository.TitleGenreRepository;
import com.ottnavi.catalog.domain.repository.TitleRepository;
import com.ottnavi.catalog.domain.repository.WatchUnitRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * TMDB에서 사라진 작품을 DB에서 지운다(Task 083). 작품과 딸린 시청 단위·제공 상태·변경 이력·장르·출연진을 한 트랜잭션으로 지운다.
 * 찜·플랜 항목은 다른 도메인이라 이 서비스가 지우지 않는다. 호출하는 쪽이 먼저 치워야 한다(FK ON DELETE 없음이라 남아 있으면 실패한다).
 * 모두 벌크 삭제다(JPA cascade 삭제를 쓰지 않는다, TECH T-1).
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TitlePurgeService {

    private final TitleRepository titleRepository;
    private final WatchUnitRepository watchUnitRepository;
    private final AvailabilityRepository availabilityRepository;
    private final AvailabilityChangeRepository availabilityChangeRepository;
    private final TitleGenreRepository titleGenreRepository;
    private final TitleCastRepository titleCastRepository;

    /** 작품의 시청 단위 ID 전부. 찜·플랜에서 먼저 치울 대상을 정할 때 쓴다 */
    public List<Long> findWatchUnitIds(Long titleId) {
        return watchUnitRepository.findIdsByTitleId(titleId);
    }

    /**
     * 작품과 하위 행을 FK 순서대로 지운다. 이 서비스 밖에서 찜·플랜 항목을 먼저 치운 상태여야 한다.
     * 그 사이 누가 찜·플랜 항목을 새로 만들었다면 FK 위반(DataIntegrityViolationException)이 나고 트랜잭션 전체가 되돌아간다.
     */
    @Transactional
    public void purge(Long titleId) {
        // 1. 시청 단위 ID를 모은다
        List<Long> unitIds = watchUnitRepository.findIdsByTitleId(titleId);

        // 2. 시청 단위에 딸린 제공 상태·변경 이력을 먼저 지운다
        if (!unitIds.isEmpty()) {
            availabilityChangeRepository.deleteByWatchUnitIdIn(unitIds);
            availabilityRepository.deleteByWatchUnitIdIn(unitIds);
        }

        // 3. 시청 단위, 장르·출연진 연결, 작품 순서로 지운다
        watchUnitRepository.deleteByTitleId(titleId);
        titleGenreRepository.deleteByTitleId(titleId);
        titleCastRepository.deleteByTitleId(titleId);
        titleRepository.deleteByTitleId(titleId);
        log.info("TMDB에서 사라진 작품을 삭제했다: titleId={}, 시청 단위={}개", titleId, unitIds.size());
    }
}
