package com.ottnavi.plan.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.plan.domain.enums.PlanChangeReasonType;
import com.ottnavi.plan.domain.enums.PlanTitleChangeType;
import com.ottnavi.plan.domain.rule.PlanChangeSummary;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 변경 요약 JSON 변환을 확인한다.
 * 막으려는 사고: 저장했다 읽었을 때 값이 달라지는 일(null 이전 비용·이전 자리, isConfirmed 이름), 모르는 버전·깨진 JSON이 예외로 번지는 일.
 */
class PlanChangeSummaryCodecTest {

	private final PlanChangeSummaryCodec codec = new PlanChangeSummaryCodec();

	@Test
	void 저장했다_읽으면_같은_값이고_null_필드도_유지된다() {
		// given: 새 달(이전 비용 null)과 ADDED(이전 자리 null)를 포함한 요약
		PlanChangeSummary original = new PlanChangeSummary(PlanChangeSummary.CURRENT_VERSION, "11월 구독이 바뀌었어요",
				List.of(new PlanChangeSummary.Reason(PlanChangeReasonType.PRICE_CHANGED, "요금이 바뀌었어요", null, 5L)),
				List.of(new PlanChangeSummary.MonthChange("2027-01", false, List.of(),
						List.of(new PlanChangeSummary.Product(6L, List.of(5L), "디즈니+ 스탠다드")), null, 9_900)),
				List.of(new PlanChangeSummary.TitleChange(PlanTitleChangeType.ADDED, 10L, "카지노", 1, null,
						new PlanChangeSummary.Placement("2027-01", 5L))));

		// when
		String json = codec.write(original);

		// then: JSON에 버전과 계약 이름(isConfirmed)이 들어 있고, 읽으면 원래 값과 같다
		assertThat(json).contains("\"v\":1").contains("\"isConfirmed\":false");
		assertThat(codec.read(json)).contains(original);
	}

	@Test
	void 모르는_버전이거나_깨진_JSON이면_예외_없이_빈_값이다() {
		assertThat(codec.read("{\"v\":99,\"summary\":\"미래 형식\"}")).isEmpty();
		assertThat(codec.read("{깨진")).isEmpty();
		assertThat(codec.read(null)).isEmpty();
	}
}
