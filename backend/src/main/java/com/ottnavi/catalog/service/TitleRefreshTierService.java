package com.ottnavi.catalog.service;

import com.ottnavi.catalog.domain.repository.TitleRepository;
import com.ottnavi.catalog.service.model.RefreshTierRealignment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 작품의 갱신 주기(refresh_tier)를 찜 현황에 맞춰 한꺼번에 맞춘다(Task 077).
 * 찜을 추가·삭제할 때마다 바꾸지 않고, 수집 Job을 시작할 때 이 서비스가 전체를 한 번에 재맞춤한다.
 * 그래서 회원 탈퇴로 찜이 벌크 삭제돼도 다음 수집 때 WEEKLY로 돌아온다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TitleRefreshTierService {

    private final TitleRepository titleRepository;

    /**
     * 찜 행이 있는 작품은 DAILY, 없는 작품은 WEEKLY로 맞춘다. 이미 맞는 작품은 건드리지 않는다.
     * 예: 작품 A에 찜 행이 있고 WEEKLY → DAILY로, 작품 B의 찜이 모두 지워졌고 DAILY → WEEKLY로 바뀐다.
     */
    @Transactional
    public RefreshTierRealignment realignByWishlist() {
        // 1. 찜이 생긴 작품을 DAILY로 올린다
        int promoted = titleRepository.promoteWishlistedToDaily();

        // 2. 찜이 모두 사라진 작품을 WEEKLY로 되돌린다
        int demoted = titleRepository.demoteUnwishlistedToWeekly();
        return new RefreshTierRealignment(promoted, demoted);
    }
}
