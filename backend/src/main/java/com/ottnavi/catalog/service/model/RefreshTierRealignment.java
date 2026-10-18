package com.ottnavi.catalog.service.model;

/**
 * 작품 갱신 주기(refresh_tier) 재맞춤 결과.
 *
 * @param promotedToDaily 찜이 생겨 WEEKLY에서 DAILY로 바뀐 작품 수
 * @param demotedToWeekly 찜이 모두 사라져 DAILY에서 WEEKLY로 돌아온 작품 수
 */
public record RefreshTierRealignment(int promotedToDaily, int demotedToWeekly) {
}
