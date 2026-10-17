package com.ottnavi.plan.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 변경 요약 문장의 조사 선택을 확인한다(Task 069). */
class PlanChangeMessagesTest {

	// 확인하는 것: 숫자로 끝나는 이름의 "으로/로". 일·칠·팔은 ㄹ 받침이라 "로", 삼·육은 받침 있음이라 "으로". 막으려는 사고: "7으로" 같은 어색한 문장
	@Test
	void 숫자로_끝나는_이름은_읽는_소리에_맞춰_으로_로를_고른다() {
		assertThat(PlanChangeMessages.euroRo("서비스1")).isEqualTo("로");
		assertThat(PlanChangeMessages.euroRo("서비스7")).isEqualTo("로");
		assertThat(PlanChangeMessages.euroRo("서비스8")).isEqualTo("로");
		assertThat(PlanChangeMessages.euroRo("서비스3")).isEqualTo("으로");
		assertThat(PlanChangeMessages.euroRo("서비스6")).isEqualTo("으로");
	}
}
