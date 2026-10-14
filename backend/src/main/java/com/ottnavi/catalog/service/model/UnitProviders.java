package com.ottnavi.catalog.service.model;

import com.ottnavi.catalog.domain.rule.KrProviders;

/** 시청 단위 하나의 KR 제공처 조회 결과. 판정 Step의 Processor가 만들고 Writer가 판정·저장한다 */
public record UnitProviders(
        long watchUnitId,
        KrProviders kr) {
}
