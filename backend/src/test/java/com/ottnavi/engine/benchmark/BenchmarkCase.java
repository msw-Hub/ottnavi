package com.ottnavi.engine.benchmark;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ottnavi.engine.model.OttProductCondition;
import com.ottnavi.engine.model.PlanType;
import com.ottnavi.engine.model.Priority;
import java.util.List;
import java.util.Map;

/**
 * 정답 세트 한 건(engine-benchmark/*.json 파일 하나). 입력과 기대 결과, 무엇을 검증하는 케이스인지 설명을 함께 담는다.
 * 이름·제목은 모두 가상이다(작품A, 서비스A 식). Task 065(관리자 측정 API)도 같은 파일을 쓴다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BenchmarkCase(
		String name,                         // 케이스 이름(파일명과 같다)
		String note,                         // 무엇을 검증하는 케이스인지. 소규모는 손계산 근거 한 줄
		Basis expectationBasis,              // 기대값을 어디서 얻었는지
		String dataAsOf,                     // 데이터 기준일(ISO, yyyy-MM-dd). input_hash에 들어간다
		int monthlyBudget,                   // 월 예산(원)
		int monthlyWatchMinutes,             // 월 시청 가능 시간(분)
		List<ProductSpec> products,          // 후보 상품
		List<UnitSpec> units,                // 시청 단위
		Map<PlanType, ExpectedPlan> expected, // 유형 3종별 기대 결과(완전탐색 기준). 생성 전 입력 파일에는 없다
		ExpectedPlan greedyExpected          // 그리디(추천형)가 완전탐색보다 나쁜 케이스에서만: 그리디의 기대 결과
) {

	/** 기대값의 근거. */
	public enum Basis {
		ORACLE,     // 무차별 대입 오라클 결과(상품 3~4개 소규모). 테스트가 오라클로 다시 계산해 대조한다
		REGRESSION  // 완전탐색 결과를 그 시점에 고정한 회귀 기준(정답이 독립적으로 증명된 것은 아니다)
	}

	/** 상품 하나. */
	public record ProductSpec(
			long id,                      // 상품 ID
			String name,                  // 가상 서비스 이름(설명용, 계산에 쓰지 않는다)
			int monthlyPrice,             // 월 가격(원)
			OttProductCondition condition // 구독 상태
	) {
	}

	/** 시청 단위 하나. */
	public record UnitSpec(
			long id,                          // 시청 단위 ID
			String name,                      // 가상 작품 이름(설명용)
			Priority priority,                // MUST / WANT / MAYBE
			int requiredMinutes,              // 필요 시간(분)
			List<Long> watchableProductIds,   // 시청 가능 상품 ID
			boolean providerUnknown           // 제공 상태가 "모름"인 서비스가 있는지
	) {
	}

	/** 한 유형의 기대 결과. */
	public record ExpectedPlan(
			List<List<Long>> months, // 달별 선택 상품 ID(0=이번 달, 1·2=다음 두 달). 각 달은 오름차순
			int mustCompleted,       // 시청 완료한 MUST 수
			int score,               // WANT 2점·MAYBE 1점 합
			int totalCost            // 3개월 비용 합(원)
	) {
	}
}
