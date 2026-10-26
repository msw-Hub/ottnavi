package com.ottnavi.product.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.product.domain.rule.CalcCandidateRules.Candidate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 계산 후보 선택 규칙 단위 시험(Task 076): 서비스마다 단품 1개, 번들은 모드에 따라 거른다 */
class CalcCandidateRulesTest {

	private Candidate single(long productId, long serviceId, boolean isLowCost, int price) {
		return new Candidate(productId, false, isLowCost, serviceId, price);
	}

	private Candidate bundle(long productId, boolean isLowCost, int price) {
		return new Candidate(productId, true, isLowCost, null, price);
	}

	@Test
	void 모드_끔은_표준_단품_중_가장_싼_것을_고른다() {
		// given
		List<Candidate> products = new ArrayList<>();
		products.add(single(1, 10, false, 13500)); // 표준 스탠다드
		products.add(single(2, 10, false, 17000)); // 표준 프리미엄
		products.add(single(3, 10, true, 7000)); // 저가형 광고형

		// when
		Set<Long> selected = CalcCandidateRules.select(products, false);

		// then
		assertThat(selected).containsExactly(1L);
	}

	@Test
	void 모드_끔에서_표준이_없으면_저가형_중_가장_싼_것을_고른다() {
		// given
		List<Candidate> products = new ArrayList<>();
		products.add(single(1, 10, true, 9000));
		products.add(single(2, 10, true, 5000));

		// when
		Set<Long> selected = CalcCandidateRules.select(products, false);

		// then
		assertThat(selected).containsExactly(2L);
	}

	@Test
	void 모드_켬은_분류와_무관하게_가장_싼_단품을_고른다() {
		// given
		List<Candidate> products = new ArrayList<>();
		products.add(single(1, 10, false, 13500));
		products.add(single(3, 10, true, 7000));

		// when
		Set<Long> selected = CalcCandidateRules.select(products, true);

		// then
		assertThat(selected).containsExactly(3L);
	}

	@Test
	void 금액이_같으면_상품_ID가_작은_것을_고른다() {
		// given
		List<Candidate> products = new ArrayList<>();
		products.add(single(8, 10, false, 10000));
		products.add(single(5, 10, false, 10000));

		// when
		Set<Long> selected = CalcCandidateRules.select(products, false);

		// then
		assertThat(selected).containsExactly(5L);
	}

	@Test
	void 서비스마다_단품이_정확히_1개씩_후보가_된다() {
		// given
		List<Candidate> products = new ArrayList<>();
		products.add(single(1, 10, false, 13500));
		products.add(single(2, 10, true, 7000));
		products.add(single(3, 20, false, 10900));
		products.add(single(4, 30, true, 6000));

		// when
		Set<Long> selected = CalcCandidateRules.select(products, false);

		// then
		assertThat(selected).containsExactlyInAnyOrder(1L, 3L, 4L);
	}

	@Test
	void 번들은_모드_끔이면_표준_번들만_모드_켬이면_전부_후보다() {
		// given
		List<Candidate> products = new ArrayList<>();
		products.add(bundle(11, false, 16000));
		products.add(bundle(12, true, 9000));
		products.add(bundle(13, false, 20000));

		// when
		Set<Long> off = CalcCandidateRules.select(products, false);
		Set<Long> on = CalcCandidateRules.select(products, true);

		// then
		assertThat(off).containsExactlyInAnyOrder(11L, 13L);
		assertThat(on).containsExactlyInAnyOrder(11L, 12L, 13L);
	}

	@Test
	void 사용자가_고친_금액이_들어간_적용_금액으로_가장_싼_단품을_고른다() {
		// given: 광고형이 원래 더 싸지만 사용자가 표준을 5000원으로 고쳤다
		List<Candidate> products = new ArrayList<>();
		products.add(single(1, 10, false, 5000));
		products.add(single(2, 10, true, 7000));

		// when
		Set<Long> selected = CalcCandidateRules.select(products, true);

		// then
		assertThat(selected).containsExactly(1L);
	}

	@Test
	void 상품이_없으면_후보도_없다() {
		// given
		List<Candidate> products = new ArrayList<>();

		// when
		Set<Long> selected = CalcCandidateRules.select(products, true);

		// then
		assertThat(selected).isEmpty();
	}
}
