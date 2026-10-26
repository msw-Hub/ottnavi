package com.ottnavi.collect.job;

import com.ottnavi.catalog.domain.enums.RefreshTier;
import java.util.List;

/**
 * 수집 Job 파라미터 tier. 작품의 RefreshTier(DAILY/WEEKLY)와 달리 ALL이 있다.
 * title.refresh_tier의 DB CHECK가 DAILY/WEEKLY만 허용하므로 둘을 한 enum으로 합치지 않는다.
 */
public enum CollectTier {
    DAILY, // 찜한 작품(refresh_tier=DAILY)만 상세·판정을 다시 받는다. 후보 발견은 건너뛴다(Task 077)
    WEEKLY, // 후보 발견은 하고 나머지 작품(refresh_tier=WEEKLY)만 상세·판정을 다시 받는다(Task 077)
    ALL; // 전체 일괄 수집. 수동 전체 실행

    /**
     * 이 tier의 상세·판정 Step이 읽을 작품의 refresh_tier 목록.
     * 예: DAILY → [DAILY], WEEKLY → [WEEKLY], ALL → [DAILY, WEEKLY](조건 없음과 같다).
     */
    public List<RefreshTier> targetRefreshTiers() {
        if (this == DAILY) {
            return List.of(RefreshTier.DAILY);
        }
        if (this == WEEKLY) {
            return List.of(RefreshTier.WEEKLY);
        }
        return List.of(RefreshTier.DAILY, RefreshTier.WEEKLY);
    }
}
