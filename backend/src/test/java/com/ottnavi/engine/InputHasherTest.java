package com.ottnavi.engine;

import static com.ottnavi.engine.EngineFixtures.free;
import static com.ottnavi.engine.EngineFixtures.product;
import static com.ottnavi.engine.EngineFixtures.subscribed;
import static com.ottnavi.engine.EngineFixtures.unit;
import static com.ottnavi.engine.EngineFixtures.unknownUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ottnavi.engine.model.OttProduct;
import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.Priority;
import com.ottnavi.engine.model.WatchUnit;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * input_hash 계산기 테스트: 순서가 달라도 같은 해시, 값이 하나라도 다르면 다른 해시.
 */
class InputHasherTest {

	private static final LocalDate AS_OF = LocalDate.of(2026, 10, 1);

	private static final List<OttProduct> PRODUCTS = List.of(product(1, 9_900), subscribed(2, 7_900), free(3), product(4, 5_500));

	private static final List<WatchUnit> UNITS = List.of(
			unit(1, Priority.MUST, 120, 1L, 2L),
			unit(2, Priority.WANT, 480, 2L),
			unit(3, Priority.MAYBE, 90, 4L, 1L),
			unknownUnit(4, Priority.WANT, 100));

	private static PlanInput input(List<OttProduct> products, List<WatchUnit> units, int budget, int minutes) {
		return new PlanInput(units, products, budget, minutes);
	}

	private static String hashOf(PlanInput input) {
		return InputHasher.hash(input, AS_OF);
	}

	@Test
	@DisplayName("SHA-256 소문자 hex 64자이고 같은 입력은 항상 같은 해시다")
	void 형식과_결정성() {
		String hash = hashOf(input(PRODUCTS, UNITS, 15_000, 600));

		assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
		assertThat(hashOf(input(PRODUCTS, UNITS, 15_000, 600))).isEqualTo(hash);
	}

	@Test
	@DisplayName("시청 단위·상품 목록 순서를 섞어도 같은 해시다")
	void 순서를_섞어도_같다() {
		String expected = hashOf(input(PRODUCTS, UNITS, 15_000, 600));
		List<OttProduct> shuffledProducts = new ArrayList<>(PRODUCTS);
		Collections.reverse(shuffledProducts);
		List<WatchUnit> shuffledUnits = new ArrayList<>(UNITS);
		Collections.reverse(shuffledUnits);

		assertThat(hashOf(input(shuffledProducts, shuffledUnits, 15_000, 600))).isEqualTo(expected);
	}

	@Test
	@DisplayName("시청 가능 상품 ID 집합의 선언 순서와 무관하게 같은 해시다")
	void 시청_가능_상품_집합_순서와_무관하다() {
		List<WatchUnit> reorderedIds = List.of(
				unit(1, Priority.MUST, 120, 2L, 1L),
				unit(2, Priority.WANT, 480, 2L),
				unit(3, Priority.MAYBE, 90, 1L, 4L),
				unknownUnit(4, Priority.WANT, 100));

		assertThat(hashOf(input(PRODUCTS, reorderedIds, 15_000, 600))).isEqualTo(hashOf(input(PRODUCTS, UNITS, 15_000, 600)));
	}

	@Test
	@DisplayName("월 예산·월 시청 분·데이터 기준일 중 하나만 바뀌어도 해시가 달라진다")
	void 숫자_하나만_바뀌어도_달라진다() {
		String base = hashOf(input(PRODUCTS, UNITS, 15_000, 600));

		assertThat(hashOf(input(PRODUCTS, UNITS, 15_001, 600))).isNotEqualTo(base);
		assertThat(hashOf(input(PRODUCTS, UNITS, 15_000, 601))).isNotEqualTo(base);
		assertThat(InputHasher.hash(input(PRODUCTS, UNITS, 15_000, 600), AS_OF.plusDays(1))).isNotEqualTo(base);
	}

	@Test
	@DisplayName("상품의 가격·구독 상태·ID가 바뀌면 해시가 달라진다")
	void 상품이_바뀌면_달라진다() {
		String base = hashOf(input(PRODUCTS, UNITS, 15_000, 600));

		List<OttProduct> priceChanged = List.of(product(1, 9_901), subscribed(2, 7_900), free(3), product(4, 5_500));
		List<OttProduct> conditionChanged = List.of(product(1, 9_900), product(2, 7_900), free(3), product(4, 5_500));

		assertThat(hashOf(input(priceChanged, UNITS, 15_000, 600))).isNotEqualTo(base);
		assertThat(hashOf(input(conditionChanged, UNITS, 15_000, 600))).isNotEqualTo(base);
	}

	@Test
	@DisplayName("시청 단위의 우선순위·필요 분·시청 가능 상품·모름 여부가 바뀌면 해시가 달라진다")
	void 시청_단위가_바뀌면_달라진다() {
		String base = hashOf(input(PRODUCTS, UNITS, 15_000, 600));

		assertThat(hashOf(input(PRODUCTS, replaceUnit(unit(1, Priority.WANT, 120, 1L, 2L)), 15_000, 600))).isNotEqualTo(base);
		assertThat(hashOf(input(PRODUCTS, replaceUnit(unit(1, Priority.MUST, 121, 1L, 2L)), 15_000, 600))).isNotEqualTo(base);
		assertThat(hashOf(input(PRODUCTS, replaceUnit(unit(1, Priority.MUST, 120, 1L)), 15_000, 600))).isNotEqualTo(base);
		assertThat(hashOf(input(PRODUCTS, replaceUnit(unknownUnit(1, Priority.MUST, 120, 1L, 2L)), 15_000, 600))).isNotEqualTo(base);
	}

	@Test
	@DisplayName("항목 경계가 달라 값이 이어 붙어도 같은 해시가 되지 않는다(구분자)")
	void 구분자로_경계가_구분된다() {
		// 상품 11 하나(가격 1)와 상품 1(가격 11)처럼 숫자를 이어 붙이면 같아 보일 수 있는 입력
		PlanInput first = input(List.of(product(11, 1)), List.of(), 1_000, 100);
		PlanInput second = input(List.of(product(1, 11)), List.of(), 1_000, 100);

		assertThat(hashOf(first)).isNotEqualTo(hashOf(second));
	}

	@Test
	@DisplayName("정규화 문자열은 버전·예산·시청 분·기준일(ISO)·정렬된 상품과 단위 순으로 고정 구분자로 이어진다")
	void 정규화_문자열_형식() {
		PlanInput input = input(
				List.of(product(2, 7_900), subscribed(1, 9_900)),
				List.of(unit(2, Priority.WANT, 480, 2L, 1L), unknownUnit(1, Priority.MUST, 120)),
				15_000, 600);

		assertThat(InputHasher.canonicalize(input, AS_OF)).isEqualTo(
				"v2|budget=15000|minutes=600|asOf=2026-10-01|products=1:9900:SUBSCRIBED;2:7900:NONE"
						+ "|units=1:MUST:120::true:-:-;2:WANT:480:1,2:false:-:-");
	}

	@Test
	@DisplayName("v2 정규화 문자열에 시즌 단위는 titleId와 seasonNumber가 들어가고 영화는 -다")
	void 정규화_문자열에_시즌이_들어간다() {
		PlanInput input = input(List.of(product(1, 9_900)),
				List.of(season(2, Priority.WANT, 480, 7L, 3), unit(1, Priority.MUST, 120, 1L)), 15_000, 600);

		assertThat(InputHasher.canonicalize(input, AS_OF)).endsWith("|units=1:MUST:120:1:false:-:-;2:WANT:480:1:false:7:3");
	}

	@Test
	@DisplayName("시즌 단위도 순서를 섞어도 같은 해시이고 시즌 번호나 titleId만 바뀌어도 달라진다")
	void 시즌_해시는_순서와_무관하고_시즌_번호에_민감하다() {
		List<OttProduct> products = List.of(product(1, 9_900));
		List<WatchUnit> units = List.of(season(1, Priority.WANT, 100, 7L, 1), season(2, Priority.WANT, 100, 7L, 2));
		String base = hashOf(input(products, units, 15_000, 600));

		assertThat(hashOf(input(products, List.of(units.get(1), units.get(0)), 15_000, 600))).isEqualTo(base);
		// 시즌 번호를 서로 바꾸면(1번 단위가 2번 시즌, 2번 단위가 1번 시즌) 시즌 순서가 달라져 다른 입력이다
		List<WatchUnit> swapped = List.of(season(1, Priority.WANT, 100, 7L, 2), season(2, Priority.WANT, 100, 7L, 1));
		assertThat(hashOf(input(products, swapped, 15_000, 600))).isNotEqualTo(base);
		List<WatchUnit> otherTitle = List.of(season(1, Priority.WANT, 100, 8L, 1), season(2, Priority.WANT, 100, 7L, 2));
		assertThat(hashOf(input(products, otherTitle, 15_000, 600))).isNotEqualTo(base);
		// 영화(null)와 시즌 단위도 구분된다
		List<WatchUnit> asMovie = List.of(unit(1, Priority.WANT, 100, 1L), season(2, Priority.WANT, 100, 7L, 2));
		assertThat(hashOf(input(products, asMovie, 15_000, 600))).isNotEqualTo(base);
	}

	/** 시즌 단위를 만든다(시청 가능 상품은 상품 1 하나). */
	private static WatchUnit season(long id, Priority priority, int minutes, Long titleId, Integer seasonNumber) {
		return new WatchUnit(id, priority, minutes, java.util.Set.of(1L), false, titleId, seasonNumber);
	}

	@Test
	@DisplayName("null 입력은 IllegalArgumentException이다")
	void null_입력은_예외다() {
		assertThatThrownBy(() -> InputHasher.hash(null, AS_OF)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> InputHasher.hash(input(PRODUCTS, UNITS, 15_000, 600), null)).isInstanceOf(IllegalArgumentException.class);
	}

	/** UNITS의 1번 단위를 바꾼 목록을 만든다. */
	private static List<WatchUnit> replaceUnit(WatchUnit replacement) {
		List<WatchUnit> units = new ArrayList<>(UNITS);
		units.set(0, replacement);
		return units;
	}
}
