package com.ottnavi.home.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 인기 작품 상세 채우기 설정의 범위 검사를 확인한다. 막으려는 사고: 잘못된 값으로 기동되어 TMDB를 과하게 부르거나 아무것도 못 채우는 일 */
class PopularityFillPropertiesTest {

	@Test
	void 범위_안의_값은_그대로_만들어진다() {
		// when
		PopularityFillProperties properties = new PopularityFillProperties(true, 20, 1);

		// then
		assertThat(properties.topN()).isEqualTo(20);
		assertThat(properties.maxTitles()).isEqualTo(1);
	}

	@Test
	void topN이_1_미만이거나_20_초과이면_기동을_막는다() {
		// when, then
		assertThatThrownBy(() -> new PopularityFillProperties(true, 0, 20)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PopularityFillProperties(true, 21, 20)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void maxTitles가_1_미만이면_기동을_막는다() {
		// when, then
		assertThatThrownBy(() -> new PopularityFillProperties(true, 20, 0)).isInstanceOf(IllegalArgumentException.class);
	}
}
