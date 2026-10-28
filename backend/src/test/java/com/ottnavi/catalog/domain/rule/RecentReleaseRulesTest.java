package com.ottnavi.catalog.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * 최근 개봉 그룹 판정과 인기도 비교를 DB 없이 확인한다.
 * 막으려는 사고: 기간 경계(정확히 6개월 전·오늘·내일)가 어긋나는 일, 미래·모름 작품이 앞에 오는 일, 인기도 없는 작품이 앞에 오는 일.
 */
class RecentReleaseRulesTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 27);

	@Test
	void 오늘과_기간_경계일은_최근_그룹이다() {
		assertThat(RecentReleaseRules.group(TODAY, TODAY, RecentReleaseRules.EXCLUSIVE_WINDOW))
				.isEqualTo(RecentReleaseRules.GROUP_RECENT);
		assertThat(RecentReleaseRules.group(LocalDate.of(2026, 4, 27), TODAY, RecentReleaseRules.EXCLUSIVE_WINDOW))
				.isEqualTo(RecentReleaseRules.GROUP_RECENT);
	}

	@Test
	void 기간_하루_전보다_오래되면_과거_그룹이다() {
		assertThat(RecentReleaseRules.group(LocalDate.of(2026, 4, 26), TODAY, RecentReleaseRules.EXCLUSIVE_WINDOW))
				.isEqualTo(RecentReleaseRules.GROUP_PAST);
		assertThat(RecentReleaseRules.group(LocalDate.of(1993, 1, 1), TODAY, RecentReleaseRules.SEARCH_WINDOW))
				.isEqualTo(RecentReleaseRules.GROUP_PAST);
	}

	@Test
	void 개봉일이_내일_이후이거나_null이면_맨_뒤_그룹이다() {
		assertThat(RecentReleaseRules.group(TODAY.plusDays(1), TODAY, RecentReleaseRules.EXCLUSIVE_WINDOW))
				.isEqualTo(RecentReleaseRules.GROUP_FUTURE_OR_UNKNOWN);
		assertThat(RecentReleaseRules.group(null, TODAY, RecentReleaseRules.EXCLUSIVE_WINDOW))
				.isEqualTo(RecentReleaseRules.GROUP_FUTURE_OR_UNKNOWN);
	}

	@Test
	void 검색_기간은_2년이라_1년_전은_최근이고_3년_전은_과거다() {
		assertThat(RecentReleaseRules.group(TODAY.minusYears(1), TODAY, RecentReleaseRules.SEARCH_WINDOW))
				.isEqualTo(RecentReleaseRules.GROUP_RECENT);
		assertThat(RecentReleaseRules.group(TODAY.minusYears(3), TODAY, RecentReleaseRules.SEARCH_WINDOW))
				.isEqualTo(RecentReleaseRules.GROUP_PAST);
	}

	@Test
	void 인기도는_높은_쪽이_앞이고_null은_뒤이며_동률은_0이다() {
		assertThat(RecentReleaseRules.comparePopularityDesc(new BigDecimal("80"), new BigDecimal("10"))).isNegative();
		assertThat(RecentReleaseRules.comparePopularityDesc(new BigDecimal("10"), new BigDecimal("80"))).isPositive();
		assertThat(RecentReleaseRules.comparePopularityDesc(null, new BigDecimal("1"))).isPositive();
		assertThat(RecentReleaseRules.comparePopularityDesc(new BigDecimal("1"), null)).isNegative();
		assertThat(RecentReleaseRules.comparePopularityDesc(null, null)).isZero();
		assertThat(RecentReleaseRules.comparePopularityDesc(new BigDecimal("5.0"), new BigDecimal("5.00"))).isZero();
	}
}
