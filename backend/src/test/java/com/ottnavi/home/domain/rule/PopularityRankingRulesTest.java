package com.ottnavi.home.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.catalog.domain.enums.MediaType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 인기 순위 평균 규칙(PRD 11절 42번)을 DB 없이 확인한다.
 * 막으려는 사고: 하루만 반짝 오른 작품이 평균에서 부풀려지는 일, 여러 서비스에 걸친 같은 작품이 중복으로 합쳐지는 일, 동률 순서가 실행마다 바뀌는 일.
 */
class PopularityRankingRulesTest {

	private static final LocalDate DAY1 = LocalDate.of(2026, 10, 17);
	private static final LocalDate DAY2 = LocalDate.of(2026, 10, 18);

	private DailyPopularity row(LocalDate date, MediaType mediaType, int tmdbId, String popularity) {
		return new DailyPopularity(date, mediaType, tmdbId, "작품" + tmdbId, null, null, new BigDecimal(popularity));
	}

	@Test
	void 없던_날은_0으로_세어_한_번만_반짝한_작품이_꾸준한_작품에_밀린다() {
		// given: 이틀(daysCounted=2). 1번은 이틀 내내 30, 2번은 오늘만 50
		List<DailyPopularity> rows = List.of(
				row(DAY1, MediaType.MOVIE, 1, "30"), row(DAY2, MediaType.MOVIE, 1, "30"),
				row(DAY2, MediaType.MOVIE, 2, "50"));

		// when
		List<RankedPopularity> ranked = PopularityRankingRules.rank(rows, 2, 10);

		// then: 1번 평균 30, 2번 평균 25(50÷2)라 1번이 앞이다
		assertThat(ranked).extracting(RankedPopularity::tmdbId).containsExactly(1, 2);
		assertThat(ranked.get(0).averagePopularity()).isEqualByComparingTo("30");
		assertThat(ranked.get(1).averagePopularity()).isEqualByComparingTo("25");
	}

	@Test
	void 같은_날_같은_작품이_여러_줄이면_가장_큰_하나만_센다() {
		// given: 전체 순위에서 서비스 두 곳에 같은 작품(같은 인기도)이 나온다
		List<DailyPopularity> rows = List.of(row(DAY2, MediaType.TV, 7, "40"), row(DAY2, MediaType.TV, 7, "40"));

		// when
		List<RankedPopularity> ranked = PopularityRankingRules.rank(rows, 1, 10);

		// then: 80이 아니라 40이다
		assertThat(ranked).hasSize(1);
		assertThat(ranked.get(0).averagePopularity()).isEqualByComparingTo("40");
	}

	@Test
	void 평균이_같으면_가장_최근_날_인기도_TMDB_ID_순이고_limit만큼만_돌려준다() {
		// given: 1번(어제 60, 오늘 0 없음)과 2번(어제 0 없음, 오늘 60) 평균 30으로 동률, 3번도 동률(어제 30, 오늘 30)
		List<DailyPopularity> rows = List.of(
				row(DAY1, MediaType.MOVIE, 1, "60"),
				row(DAY2, MediaType.MOVIE, 2, "60"),
				row(DAY1, MediaType.MOVIE, 3, "30"), row(DAY2, MediaType.MOVIE, 3, "30"));

		// when
		List<RankedPopularity> ranked = PopularityRankingRules.rank(rows, 2, 2);

		// then: 오늘 인기도 60인 2번이 먼저, 그다음 오늘 30인 3번(1번은 오늘 0이라 limit 밖)
		assertThat(ranked).extracting(RankedPopularity::tmdbId).containsExactly(2, 3);
	}

	@Test
	void 스냅샷_날_수가_0이거나_줄이_없으면_빈_순위다() {
		assertThat(PopularityRankingRules.rank(List.of(row(DAY2, MediaType.MOVIE, 1, "10")), 0, 10)).isEmpty();
		assertThat(PopularityRankingRules.rank(List.of(), 3, 10)).isEmpty();
	}
}
