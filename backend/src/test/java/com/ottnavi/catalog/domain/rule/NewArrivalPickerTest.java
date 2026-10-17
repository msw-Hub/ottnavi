package com.ottnavi.catalog.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.catalog.domain.enums.ArrivalType;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * "새로 볼 수 있게 된 작품" 대표 선택 규칙을 DB 없이 확인한다.
 * 막으려는 사고: 같은 작품·서비스가 시즌마다 여러 줄로 나오는 일, 같은 날짜일 때 공개가 입고를 가리는 일, 정렬이 실행마다 바뀌는 일.
 */
class NewArrivalPickerTest {

	private static final LocalDate D10 = LocalDate.of(2026, 10, 10);
	private static final LocalDate D15 = LocalDate.of(2026, 10, 15);

	@Test
	void 같은_작품_서비스는_가장_최근_한_건만_남기고_최신순으로_정렬한다() {
		// given: 작품 1은 서비스 1에서 두 번(10일, 15일), 작품 2는 서비스 1에서 12일
		List<NewArrivalCandidate> candidates = List.of(
				new NewArrivalCandidate(1, 1, ArrivalType.ARRIVAL, D10),
				new NewArrivalCandidate(1, 1, ArrivalType.ARRIVAL, D15),
				new NewArrivalCandidate(2, 1, ArrivalType.RELEASE, LocalDate.of(2026, 10, 12)));

		// when
		List<NewArrivalCandidate> picked = NewArrivalPicker.pick(candidates, 10);

		// then
		assertThat(picked).extracting(NewArrivalCandidate::titleId).containsExactly(1L, 2L);
		assertThat(picked.get(0).date()).isEqualTo(D15);
	}

	@Test
	void 날짜가_같으면_입고가_공개보다_앞이고_다른_서비스는_따로_센다() {
		// given
		List<NewArrivalCandidate> candidates = List.of(
				new NewArrivalCandidate(1, 1, ArrivalType.RELEASE, D15),
				new NewArrivalCandidate(1, 1, ArrivalType.ARRIVAL, D15),
				new NewArrivalCandidate(1, 2, ArrivalType.RELEASE, D15));

		// when
		List<NewArrivalCandidate> picked = NewArrivalPicker.pick(candidates, 10);

		// then: 서비스 1은 ARRIVAL 한 건, 서비스 2는 RELEASE 한 건
		assertThat(picked).hasSize(2);
		assertThat(picked.get(0).type()).isEqualTo(ArrivalType.ARRIVAL);
		assertThat(picked.get(1).ottServiceId()).isEqualTo(2L);
	}

	@Test
	void limit보다_많으면_최신_것만_자른다() {
		// given
		List<NewArrivalCandidate> candidates = List.of(
				new NewArrivalCandidate(1, 1, ArrivalType.ARRIVAL, D10),
				new NewArrivalCandidate(2, 1, ArrivalType.ARRIVAL, D15));

		// when, then
		assertThat(NewArrivalPicker.pick(candidates, 1)).extracting(NewArrivalCandidate::titleId).containsExactly(2L);
	}
}
