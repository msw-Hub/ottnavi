package com.ottnavi.catalog.service.model;

/** 출연진 한 명(조회 API용) */
public record CastView(
        String name,
        String character) { // 배역. 모르면 null
}
