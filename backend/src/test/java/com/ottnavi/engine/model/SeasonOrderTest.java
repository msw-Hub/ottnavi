package com.ottnavi.engine.model;

import static com.ottnavi.engine.EngineFixtures.assertInvariants;
import static com.ottnavi.engine.EngineFixtures.free;
import static com.ottnavi.engine.EngineFixtures.product;
import static com.ottnavi.engine.EngineFixtures.subscribed;
import static com.ottnavi.engine.EngineFixtures.unit;
import static com.ottnavi.engine.model.Priority.MAYBE;
import static com.ottnavi.engine.model.Priority.MUST;
import static com.ottnavi.engine.model.Priority.WANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ottnavi.engine.rule.Evaluator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 시즌 순서(PRD 5.4): WatchUnit의 titleId·seasonNumber 검증, PlanInput의 시즌 중복 검증과 앞 시즌 끌어올림 순서.
 * 기대 순서는 모두 손으로 계산한 값이다(기본 정렬 = 시청 가능 여부, 우선순위, 필요 시간, id).
 */
class SeasonOrderTest {

	private static final int BUDGET = 15_000;
	private static final int MINUTES = 600;
	private static final List<OttProduct> P1_P2 = List.of(product(1, 10_000), product(2, 20_000)); // 상품 2는 월 예산(15,000원) 초과

	/** 상품 1로 볼 수 있는 시즌 단위. */
	private static WatchUnit season(long id, Priority priority, int minutes, long titleId, int seasonNumber) {
		return new WatchUnit(id, priority, minutes, Set.of(1L), false, titleId, seasonNumber);
	}

	/** 지정한 상품으로만 볼 수 있는 시즌 단위. */
	private static WatchUnit seasonOn(long id, Priority priority, int minutes, long titleId, int seasonNumber, Long... productIds) {
		return new WatchUnit(id, priority, minutes, Set.of(productIds), false, titleId, seasonNumber);
	}

	private static List<Long> orderOf(PlanInput input) {
		return input.units().stream().map(WatchUnit::id).toList();
	}

	private static PlanInput inputOf(int minutes, List<OttProduct> products, WatchUnit... units) {
		return new PlanInput(List.of(units), products, BUDGET, minutes);
	}

	// WatchUnit 검증

	@Test
	void 영화는_titleId와_seasonNumber가_둘_다_null이고_5인자_생성자도_같다() {
		WatchUnit movie = new WatchUnit(1, WANT, 60, Set.of(1L), false);

		assertThat(movie.titleId()).isNull();
		assertThat(movie.seasonNumber()).isNull();
		assertThat(movie).isEqualTo(new WatchUnit(1, WANT, 60, Set.of(1L), false, null, null));
	}

	@Test
	void titleId와_seasonNumber는_둘_다_있거나_둘_다_없어야_한다() {
		assertThatThrownBy(() -> new WatchUnit(1, WANT, 60, Set.of(), false, 5L, null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new WatchUnit(1, WANT, 60, Set.of(), false, null, 1)).isInstanceOf(IllegalArgumentException.class);
		assertThat(new WatchUnit(1, WANT, 60, Set.of(), false, 5L, 1).seasonNumber()).isEqualTo(1);
	}

	@Test
	void titleId는_양수이고_seasonNumber는_1_이상이어야_한다() {
		assertThatThrownBy(() -> new WatchUnit(1, WANT, 60, Set.of(), false, 0L, 1)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new WatchUnit(1, WANT, 60, Set.of(), false, -3L, 1)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new WatchUnit(1, WANT, 60, Set.of(), false, 5L, 0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new WatchUnit(1, WANT, 60, Set.of(), false, 5L, -1)).isInstanceOf(IllegalArgumentException.class);
	}

	// PlanInput 중복 검증

	@Test
	void 같은_titleId와_seasonNumber가_중복되면_PlanInput을_만들_수_없다() {
		List<WatchUnit> units = List.of(season(1, WANT, 60, 7, 1), season(2, MUST, 60, 7, 1));

		assertThatThrownBy(() -> new PlanInput(units, P1_P2, BUDGET, MINUTES))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("titleId=7")
				.hasMessageContaining("seasonNumber=1");
	}

	@Test
	void 시즌_번호가_같아도_작품이_다르면_중복이_아니다() {
		PlanInput input = inputOf(MINUTES, P1_P2, season(1, WANT, 60, 7, 1), season(2, WANT, 60, 8, 1));

		assertThat(input.units()).hasSize(2);
	}

	// 끌어올림 정렬

	@Test
	void 시즌이_없는_입력은_기본_정렬과_완전히_같다() {
		List<WatchUnit> units = List.of(
				unit(5, MAYBE, 10, 1L), unit(4, WANT, 90, 1L), unit(3, WANT, 90, 1L), unit(2, WANT, 30, 1L), unit(1, MUST, 500, 1L),
				unit(9, MUST, 10), unit(8, WANT, 5));
		List<WatchUnit> expected = new ArrayList<>(units);
		expected.sort(WatchUnit.ASSIGNMENT_ORDER);

		assertThat(new PlanInput(units, P1_P2, BUDGET, MINUTES).units()).containsExactlyElementsOf(expected);
	}

	@Test
	void 시즌_일부만_찜하면_있는_시즌끼리_번호순으로_끌어올린다() {
		// 1번 시즌(id 10, WANT 300)과 3번 시즌(id 12, WANT 100)만 찜하고 영화(id 1, WANT 200)도 찜
		// 기본 정렬(WANT, 필요 시간 짧은 순): 12(100), 1(200), 10(300)
		// 12를 내보낼 때 안 나온 앞 시즌 10을 바로 앞에 끼운다 → 10, 12, 그다음 1
		PlanInput input = inputOf(MINUTES, P1_P2,
				season(12, WANT, 100, 7, 3), unit(1, WANT, 200, 1L), season(10, WANT, 300, 7, 1));

		assertThat(orderOf(input)).containsExactly(10L, 12L, 1L);
	}

	@Test
	void 앞_시즌이_MAYBE이고_뒤_시즌이_MUST이면_앞_시즌이_바로_앞에_끼워진다() {
		// 기본 정렬: 영화 5(MUST 30), 2시즌 21(MUST 60), 1시즌 20(MAYBE 60)
		// 5 다음 21을 내보낼 때 앞 시즌 20을 바로 앞에 → 5, 20, 21 (우선순위 값은 그대로 MAYBE·MUST)
		PlanInput input = inputOf(MINUTES, P1_P2,
				season(20, MAYBE, 60, 7, 1), season(21, MUST, 60, 7, 2), unit(5, MUST, 30, 1L));

		assertThat(orderOf(input)).containsExactly(5L, 20L, 21L);
		assertThat(input.units().get(1).priority()).isEqualTo(MAYBE);
		assertThat(input.units().get(1).requiredMinutes()).isEqualTo(60);
	}

	@Test
	void 시즌_1_2_3이_모두_있으면_번호순으로_뒤_시즌_앞에_붙는다() {
		// 기본 정렬: 53(MUST), 52(WANT), 51(MAYBE). 53을 내보낼 때 51, 52를 번호순으로 앞에 → 51, 52, 53
		PlanInput input = inputOf(MINUTES, P1_P2,
				season(53, MUST, 60, 9, 3), season(52, WANT, 60, 9, 2), season(51, MAYBE, 60, 9, 1));

		assertThat(orderOf(input)).containsExactly(51L, 52L, 53L);
	}

	@Test
	void 서로_다른_시리즈가_섞여도_각_시리즈의_앞_시즌만_끌어올린다() {
		// A(작품 1): A1 id 31 MAYBE 60, A2 id 32 MUST 60 / B(작품 2): B1 id 41 WANT 60, B2 id 42 MUST 30
		// 기본 정렬: 42(MUST 30), 32(MUST 60), 41(WANT), 31(MAYBE)
		// 42를 내보낼 때 41을 앞에 → 41, 42. 32를 내보낼 때 31을 앞에 → 31, 32. 41·31은 이미 나왔으므로 건너뜀
		PlanInput input = inputOf(MINUTES, P1_P2,
				season(31, MAYBE, 60, 1, 1), season(32, MUST, 60, 1, 2), season(41, WANT, 60, 2, 1), season(42, MUST, 30, 2, 2));

		assertThat(orderOf(input)).containsExactly(41L, 42L, 31L, 32L);
	}

	@Test
	void 입력_순서를_섞어도_배정_순서가_같다() {
		List<WatchUnit> units = new ArrayList<>(List.of(
				season(31, MAYBE, 60, 1, 1), season(32, MUST, 60, 1, 2), season(41, WANT, 60, 2, 1), season(42, MUST, 30, 2, 2),
				unit(5, MUST, 30, 1L)));
		List<Long> expected = orderOf(new PlanInput(units, P1_P2, BUDGET, MINUTES));
		Collections.reverse(units);

		assertThat(orderOf(new PlanInput(units, P1_P2, BUDGET, MINUTES))).isEqualTo(expected);
	}

	// 정적 불가능이면 끌어올리지 않는다

	@Test
	void 예산을_넘는_상품으로만_볼_수_있는_앞_시즌이_있으면_끌어올리지_않는다() {
		// 앞 시즌 20(MAYBE)은 상품 2(20,000원, 예산 15,000원 초과)로만 볼 수 있어 정적 불가능
		// 기본 정렬: 21(MUST), 5(WANT), 20(MAYBE). 끌어올렸다면 20, 21, 5가 됐을 것
		PlanInput input = inputOf(MINUTES, P1_P2,
				seasonOn(20, MAYBE, 60, 7, 1, 2L), season(21, MUST, 60, 7, 2), unit(5, WANT, 30, 1L));

		assertThat(orderOf(input)).containsExactly(21L, 5L, 20L);
	}

	@Test
	void 예산을_넘는_상품으로만_볼_수_있는_MUST_뒷_시즌도_앞_시즌을_끌어오지_않는다() {
		// 뒤 시즌 21(MUST)이 상품 2로만 볼 수 있어 정적 불가능. 기본 정렬: 21, 20
		PlanInput input = inputOf(MINUTES, P1_P2, season(20, MAYBE, 60, 7, 1), seasonOn(21, MUST, 60, 7, 2, 2L));

		assertThat(orderOf(input)).containsExactly(21L, 20L);
	}

	@Test
	void 예산을_넘어도_구독_중이거나_무료인_상품이_있으면_가능한_시즌이다() {
		// 상품 2는 구독 중(20,000원), 상품 3은 무료. 둘 다 정적 불가능 사유 ①이 아니므로 끌어올린다
		List<OttProduct> products = List.of(product(1, 10_000), subscribed(2, 20_000), free(3));
		PlanInput subscribedInput = inputOf(MINUTES, products, season(20, MAYBE, 60, 7, 1), seasonOn(21, MUST, 60, 7, 2, 2L));
		PlanInput freeInput = inputOf(MINUTES, products, season(30, MAYBE, 60, 8, 1), seasonOn(31, MUST, 60, 8, 2, 3L));

		assertThat(orderOf(subscribedInput)).containsExactly(20L, 21L);
		assertThat(orderOf(freeInput)).containsExactly(30L, 31L);
	}

	@Test
	void 필요_시간이_월_시청_시간의_3배를_넘는_시즌이_있으면_끌어올리지_않는다() {
		// 월 600분: 3배 = 1,800분. 뒤 시즌 21(MUST)이 1,801분이면 불가능 → 기본 정렬 21, 20 그대로
		PlanInput impossible = inputOf(MINUTES, P1_P2, season(20, MAYBE, 60, 7, 1), season(21, MUST, 1_801, 7, 2));
		// 경계: 정확히 1,800분은 가능하므로 끌어올린다 → 20, 21
		PlanInput boundary = inputOf(MINUTES, P1_P2, season(20, MAYBE, 60, 7, 1), season(21, MUST, 1_800, 7, 2));

		assertThat(orderOf(impossible)).containsExactly(21L, 20L);
		assertThat(orderOf(boundary)).containsExactly(20L, 21L);
	}

	@Test
	void 월_시청_시간이_120분_미만인데_긴_시즌이_있으면_끌어올리지_않는다() {
		// 월 100분(< 120): 앞 시즌 20은 100분 이하라 가능, 뒤 시즌 21은 150분(긴 시즌, 3배 300분 이내)이라 ③으로 불가능 → 21, 20
		PlanInput impossible = inputOf(100, P1_P2, season(20, MAYBE, 50, 7, 1), season(21, MUST, 150, 7, 2));
		// 대조: 월 120분이면 긴 시즌도 시작할 수 있으므로(150 ≤ 360) 끌어올린다 → 20, 21
		PlanInput possible = inputOf(120, P1_P2, season(20, MAYBE, 50, 7, 1), season(21, MUST, 150, 7, 2));

		assertThat(orderOf(impossible)).containsExactly(21L, 20L);
		assertThat(orderOf(possible)).containsExactly(20L, 21L);
	}

	@Test
	void 불가능한_시즌이_사이에_끼면_그_뒤_시즌도_끌어올리지_않는다() {
		// 2시즌 52(WANT)가 상품 2로만 볼 수 있어 불가능. 3시즌 53(MUST)을 내보낼 때 안 나온 앞 시즌 51, 52 중 52가 불가능 → 아무것도 끌어올리지 않음
		// 기본 정렬: 53(MUST), 52(WANT), 51(MAYBE)
		PlanInput input = inputOf(MINUTES, P1_P2,
				season(51, MAYBE, 60, 9, 1), seasonOn(52, WANT, 60, 9, 2, 2L), season(53, MUST, 60, 9, 3));

		assertThat(orderOf(input)).containsExactly(53L, 52L, 51L);
	}

	@Test
	void 시청_가능_상품이_없는_단위는_시즌이어도_맨_뒤에_두고_앞_시즌도_끌어오지_않는다() {
		// 1시즌 21(WANT)은 볼 수 있고 2시즌 22(MUST)는 볼 수 있는 상품이 없다. 22는 정적 불가능이라 21을 끌어오지 않고 맨 뒤
		// 기본 정렬: 21(시청 가능 먼저), 22(없음, 맨 뒤)
		PlanInput noProviderLast = inputOf(MINUTES, P1_P2, season(21, WANT, 60, 7, 1), seasonOn(22, MUST, 60, 7, 2));
		// 반대로 1시즌 31(MUST)이 볼 수 없으면 2시즌 32(WANT)가 먼저(31은 불가능이라 끌어올려지지 않음)
		PlanInput earlierNoProvider = inputOf(MINUTES, P1_P2, seasonOn(31, MUST, 60, 8, 1), season(32, WANT, 60, 8, 2));

		assertThat(orderOf(noProviderLast)).containsExactly(21L, 22L);
		assertThat(orderOf(earlierNoProvider)).containsExactly(32L, 31L);
		// 시청 가능 단위가 앞에 모여 있어야 하는 평가기 불변식이 깨지지 않는다
		for (PlanInput input : List.of(noProviderLast, earlierNoProvider)) {
			Evaluator evaluator = new Evaluator(input);
			assertInvariants(input, evaluator.evaluate(Selection.of(Set.of(1L), Set.of(1L), Set.of(1L))));
		}
	}

	// 평가기와의 계약

	@Test
	void 끌어올린_입력도_평가_결과가_입력_단위_순서와_같다() {
		PlanInput input = inputOf(MINUTES, P1_P2,
				season(31, MAYBE, 60, 1, 1), season(32, MUST, 60, 1, 2), season(41, WANT, 60, 2, 1), season(42, MUST, 30, 2, 2),
				unit(5, MUST, 30, 1L), unit(6, WANT, 30));

		Evaluation evaluation = new Evaluator(input).evaluate(Selection.of(Set.of(1L), Set.of(), Set.of()));

		assertInvariants(input, evaluation); // 결과 순서 = PlanInput.units() 순서 포함
		assertThat(orderOf(input)).containsExactly(5L, 41L, 42L, 31L, 32L, 6L);
	}
}
