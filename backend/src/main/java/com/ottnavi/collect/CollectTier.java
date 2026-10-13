package com.ottnavi.collect;

/**
 * 수집 Job 파라미터 tier. 작품의 RefreshTier(DAILY/WEEKLY)와 달리 ALL이 있다.
 * title.refresh_tier의 DB CHECK가 DAILY/WEEKLY만 허용하므로 둘을 한 enum으로 합치지 않는다.
 */
public enum CollectTier {
    DAILY, // 찜한 작품만(2단계, Task 077)
    WEEKLY, // 나머지 후보만(2단계, Task 077)
    ALL // 전체 일괄 수집. MVP는 항상 이 값
}
