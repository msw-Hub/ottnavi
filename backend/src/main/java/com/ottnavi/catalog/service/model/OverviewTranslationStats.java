package com.ottnavi.catalog.service.model;

import java.time.Instant;

/**
 * 관리자가 반영한 번역 줄거리의 통계.
 *
 * @param translatedCount    번역 작품 수
 * @param oldestTranslatedAt 가장 오래된 번역의 반영 시각(번역이 없으면 null)
 */
public record OverviewTranslationStats(long translatedCount, Instant oldestTranslatedAt) {
}
