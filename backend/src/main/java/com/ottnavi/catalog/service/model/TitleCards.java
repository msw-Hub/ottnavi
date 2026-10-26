package com.ottnavi.catalog.service.model;

import java.time.LocalDate;
import java.util.List;

/** 작품 목록을 카드(작품 정보 + 서비스별 제공 상태)로 만든 결과 */
public record TitleCards(
        List<TitleSearchItemView> items, // 입력한 작품 순서와 같다
        LocalDate dataAsOf) { // 이 작품들 판정의 가장 오래된 확인일(KST). 판정이 없으면 전체 최신 확인일
}
