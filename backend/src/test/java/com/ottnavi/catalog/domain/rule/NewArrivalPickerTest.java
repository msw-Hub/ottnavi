package com.ottnavi.catalog.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.catalog.domain.enums.ArrivalType;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * "새로 볼 수 있게 된 작품" 대표 선택·정렬 규칙을 DB 없이 확인한다.
 * 막으려는 사고: 같은 작품이 시즌마다·서비스마다 여러 줄로 겹쳐 나오는 일, 같은 날짜일 때 공개가 입고를 가리는 일, 정렬이 실행마다 바뀌는 일,
 * 옛 고전이 입고 날짜만 최근이라고 최근 작품 앞에 오는 일, 개봉일이 미래·모르는 작품이 앞에 오는 일.
 */
class NewArrivalPickerTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 27);
	private static final LocalDate D10 = LocalDate.of(2026, 10, 10);
	private static final LocalDate D15 = LocalDate.of(2026, 10, 15);
	private static final LocalDate RECENT = LocalDate.of(2026, 8, 1); // 6개월 이내 개봉
	private static final LocalDate OLD = LocalDate.of(1993, 1, 1);

	private static NewArrivalCandidate candidate(long titleId, long serviceId, ArrivalType type, LocalDate date,
			LocalDate titleRelease) {
		return new NewArrivalCandidate(titleId, serviceId, type, date, titleRelease);
	}

	@Test
	void 같은_작품_서비스는_가장_최근_한_건만_남긴다() {
		// given: 작품 1은 서비스 1에서 두 번(10일, 15일)
		List<NewArrivalCandidate> candidates = List.of(
				candidate(1, 1, ArrivalType.ARRIVAL, D10, RECENT),
				candidate(1, 1, ArrivalType.ARRIVAL, D15, RECENT));

		// when
		List<NewArrivalCandidate> picked = NewArrivalPicker.pick(candidates, 10, TODAY);

		// then
		assertThat(picked).hasSize(1);
		assertThat(picked.get(0).date()).isEqualTo(D15);
	}

	@Test
	void 같은_작품이_여러_서비스에_있으면_가장_최근_한_건만_남긴다() {
		// given: 운영 사례("문무")처럼 서비스 4는 15일 입고, 서비스 1·5는 10일 공개
		List<NewArrivalCandidate> candidates = List.of(
				candidate(1, 1, ArrivalType.RELEASE, D10, RECENT),
				candidate(1, 4, ArrivalType.ARRIVAL, D15, RECENT),
				candidate(1, 5, ArrivalType.RELEASE, D10, RECENT));

		// when
		List<NewArrivalCandidate> picked = NewArrivalPicker.pick(candidates, 10, TODAY);

		// then: 서비스 4의 15일 입고 한 건
		assertThat(picked).hasSize(1);
		assertThat(picked.get(0).ottServiceId()).isEqualTo(4L);
		assertThat(picked.get(0).date()).isEqualTo(D15);
	}

	@Test
	void 날짜가_같으면_서비스가_달라도_입고가_공개보다_앞이다() {
		// given: 서비스 1은 공개, 서비스 2는 입고(같은 날)
		List<NewArrivalCandidate> candidates = List.of(
				candidate(1, 1, ArrivalType.RELEASE, D15, RECENT),
				candidate(1, 2, ArrivalType.ARRIVAL, D15, RECENT));

		// when
		List<NewArrivalCandidate> picked = NewArrivalPicker.pick(candidates, 10, TODAY);

		// then
		assertThat(picked).hasSize(1);
		assertThat(picked.get(0).type()).isEqualTo(ArrivalType.ARRIVAL);
		assertThat(picked.get(0).ottServiceId()).isEqualTo(2L);
	}

	@Test
	void 날짜와_종류가_같으면_입력_순서와_상관없이_서비스_ID가_작은_쪽이_대표다() {
		// given: 서비스 6이 먼저 들어와도
		List<NewArrivalCandidate> candidates = List.of(
				candidate(1, 6, ArrivalType.ARRIVAL, D15, RECENT),
				candidate(1, 5, ArrivalType.ARRIVAL, D15, RECENT));

		// when
		List<NewArrivalCandidate> picked = NewArrivalPicker.pick(candidates, 10, TODAY);

		// then
		assertThat(picked).hasSize(1);
		assertThat(picked.get(0).ottServiceId()).isEqualTo(5L);
	}

	@Test
	void 최근_개봉작이_먼저이고_옛_작품이_그_다음이며_미래와_개봉일_모름이_맨_뒤다() {
		// given: 날짜는 옛 작품·미래·모름이 더 최근이어도 그룹이 우선한다
		List<NewArrivalCandidate> candidates = List.of(
				candidate(1, 1, ArrivalType.ARRIVAL, D15, OLD),
				candidate(2, 1, ArrivalType.ARRIVAL, D10, RECENT),
				candidate(3, 1, ArrivalType.ARRIVAL, D15, TODAY.plusDays(30)),
				candidate(5, 1, ArrivalType.ARRIVAL, D15, null));

		// when
		List<NewArrivalCandidate> picked = NewArrivalPicker.pick(candidates, 10, TODAY);

		// then: 최근(2) → 과거(1) → 미래·모름(날짜 같으면 작품 ID 순: 3, 5)
		assertThat(picked).extracting(NewArrivalCandidate::titleId).containsExactly(2L, 1L, 3L, 5L);
	}

	@Test
	void 같은_그룹_안에서는_날짜_최신순_작품_ID_순으로_정한다() {
		// given
		List<NewArrivalCandidate> candidates = List.of(
				candidate(1, 1, ArrivalType.ARRIVAL, D10, RECENT),
				candidate(3, 2, ArrivalType.ARRIVAL, D15, RECENT),
				candidate(2, 1, ArrivalType.ARRIVAL, D15, RECENT));

		// when
		List<NewArrivalCandidate> picked = NewArrivalPicker.pick(candidates, 10, TODAY);

		// then: 15일(작품 2 → 작품 3) 다음에 10일
		assertThat(picked).extracting(NewArrivalCandidate::titleId).containsExactly(2L, 3L, 1L);
	}

	@Test
	void limit보다_많으면_정렬_앞쪽만_남긴다() {
		// given: 날짜가 최근인 옛 작품과 날짜가 이른 최근 작품
		List<NewArrivalCandidate> candidates = List.of(
				candidate(1, 1, ArrivalType.ARRIVAL, D15, OLD),
				candidate(2, 1, ArrivalType.ARRIVAL, D10, RECENT));

		// when, then: 한 건만 남으면 최근 개봉작이다
		assertThat(NewArrivalPicker.pick(candidates, 1, TODAY)).extracting(NewArrivalCandidate::titleId)
				.containsExactly(2L);
	}
}
