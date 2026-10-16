package com.ottnavi.plan.service.model;

import java.util.List;

/**
 * 한 입력의 세 유형(추천형·절약형·간편형) 계산 결과 묶음. planCalc 캐시 값의 타입이다.
 *
 * @param results 플랜 유형 순서(추천형, 절약형, 간편형)의 결과
 */
public record PlanCalcResults(List<PlanCalcResult> results) {
}
