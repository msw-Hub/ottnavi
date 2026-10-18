package com.ottnavi.catalog.service.model;

import java.time.LocalDate;
import java.util.Map;

/** 작품 ID로 찾을 수 있는 카드 묶음. DB에 없는 작품 ID는 맵에 없다 */
public record TitleCardsById(Map<Long, TitleSearchItemView> byTitleId, LocalDate dataAsOf) {
}
