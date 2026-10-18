package com.ottnavi.catalog.service.model;

import java.time.LocalDate;
import java.util.List;

/** 새로 볼 수 있게 된 작품 목록(최신순)과 데이터 기준일 */
public record NewlyAvailableResult(List<NewlyAvailableItemView> items, LocalDate dataAsOf) {
}
