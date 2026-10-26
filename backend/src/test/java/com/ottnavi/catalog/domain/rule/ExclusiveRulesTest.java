package com.ottnavi.catalog.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.catalog.domain.enums.AvailabilityStatus;
import com.ottnavi.catalog.domain.enums.UnitType;
import com.ottnavi.catalog.domain.enums.UnknownReason;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 서비스별 독점 판정(Task 078, PRD FR-04)의 단위 테스트.
 * 막으려는 사고: 둘 이상 서비스에 있는 작품이 독점으로 나오는 일, 구조적 공백(쿠팡플레이 영화)이 "모름"으로 세어져 안내가 부풀려지는 일,
 * 모름을 "다른 곳에 있음"으로 잘못 봐서 독점이 사라지는 일, 드라마의 일부 시즌만 독점인 경우를 놓치는 일.
 * 서비스 ID는 1~7이고 7이 쿠팡플레이다.
 */
class ExclusiveRulesTest {

	private static final List<Long> SERVICE_IDS = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L);
	private static final Instant CHECKED_AT = Instant.parse("2026-10-18T00:00:00Z");

	/** 영화 시청 단위 하나: 지정한 서비스만 있음, 나머지는 없음. 7번(쿠팡플레이)은 구조적 공백이다 */
	private List<ExclusiveSourceRow> movie(long unitId, long titleId, long availableServiceId) {
		List<ExclusiveSourceRow> rows = new ArrayList<>();
		for (long serviceId : SERVICE_IDS) {
			if (serviceId == 7L) {
				rows.add(row(unitId, titleId, UnitType.MOVIE, null, serviceId, AvailabilityStatus.UNKNOWN,
						UnknownReason.STRUCTURAL_GAP));
			} else if (serviceId == availableServiceId) {
				rows.add(row(unitId, titleId, UnitType.MOVIE, null, serviceId, AvailabilityStatus.AVAILABLE, null));
			} else {
				rows.add(row(unitId, titleId, UnitType.MOVIE, null, serviceId, AvailabilityStatus.NOT_AVAILABLE, null));
			}
		}
		return rows;
	}

	/** 드라마 시즌 시청 단위 하나: 지정한 서비스만 있음, 나머지는 없음 */
	private List<ExclusiveSourceRow> season(long unitId, long titleId, int seasonNumber, long availableServiceId) {
		List<ExclusiveSourceRow> rows = new ArrayList<>();
		for (long serviceId : SERVICE_IDS) {
			AvailabilityStatus status = AvailabilityStatus.NOT_AVAILABLE;
			if (serviceId == availableServiceId) {
				status = AvailabilityStatus.AVAILABLE;
			}
			rows.add(row(unitId, titleId, UnitType.SEASON, seasonNumber, serviceId, status, null));
		}
		return rows;
	}

	private ExclusiveSourceRow row(long unitId, long titleId, UnitType unitType, Integer seasonNumber, long serviceId,
			AvailabilityStatus status, UnknownReason reason) {
		return new ExclusiveSourceRow(unitId, titleId, unitType, seasonNumber, serviceId, status, reason, CHECKED_AT);
	}

	/** 한 시청 단위의 서비스 한 곳 상태를 바꾼 새 목록 */
	private List<ExclusiveSourceRow> withStatus(List<ExclusiveSourceRow> rows, long serviceId,
			AvailabilityStatus status, UnknownReason reason) {
		List<ExclusiveSourceRow> changed = new ArrayList<>();
		for (ExclusiveSourceRow r : rows) {
			if (r.ottServiceId() == serviceId) {
				changed.add(row(r.watchUnitId(), r.titleId(), r.unitType(), r.seasonNumber(), serviceId, status, reason));
			} else {
				changed.add(r);
			}
		}
		return changed;
	}

	@Test
	void 한_서비스에만_있으면_그_서비스의_독점이고_영화는_구조적_공백을_뺀_5곳과_비교한다() {
		// given: 영화가 넷플릭스(1)에만 있고 쿠팡플레이(7)는 구조적 공백
		List<ExclusiveSourceRow> rows = movie(10, 100, 1);

		// when
		Map<Long, List<ExclusiveTitleGroup>> result = ExclusiveRules.findExclusiveTitles(rows, SERVICE_IDS);

		// then
		assertThat(result).containsOnlyKeys(1L);
		ExclusiveTitleGroup group = result.get(1L).get(0);
		assertThat(group.titleId()).isEqualTo(100);
		assertThat(group.seasonNumbers()).isNull();
		assertThat(group.comparedServiceCount()).isEqualTo(5); // 7번 제외, 자기 자신 제외
		assertThat(group.unknownElsewhereCount()).isZero(); // 구조적 공백은 모름으로 세지 않는다
	}

	@Test
	void 두_서비스_이상에_있으면_독점이_아니다() {
		// given: 넷플릭스(1)와 티빙(4) 모두 있음
		List<ExclusiveSourceRow> rows = withStatus(movie(10, 100, 1), 4, AvailabilityStatus.AVAILABLE, null);

		// when, then
		assertThat(ExclusiveRules.findExclusiveTitles(rows, SERVICE_IDS)).isEmpty();
	}

	@Test
	void 어느_서비스에도_없으면_독점이_아니다() {
		assertThat(ExclusiveRules.findExclusiveTitles(
				withStatus(movie(10, 100, 1), 1, AvailabilityStatus.NOT_AVAILABLE, null), SERVICE_IDS)).isEmpty();
	}

	@Test
	void 다른_서비스가_모름이어도_독점이고_모름_수를_센다() {
		// given: 웨이브(5)는 KR 데이터 없음(NO_KR_DATA)이라 모름
		List<ExclusiveSourceRow> rows = withStatus(movie(10, 100, 1), 5, AvailabilityStatus.UNKNOWN,
				UnknownReason.NO_KR_DATA);

		// when
		ExclusiveTitleGroup group = ExclusiveRules.findExclusiveTitles(rows, SERVICE_IDS).get(1L).get(0);

		// then: 비교 5곳 중 모름 1곳
		assertThat(group.comparedServiceCount()).isEqualTo(5);
		assertThat(group.unknownElsewhereCount()).isEqualTo(1);
	}

	@Test
	void 쿠팡플레이에_있는_드라마는_쿠팡플레이_독점이고_다른_서비스는_독점이_아니다() {
		// given: 드라마 시즌이 쿠팡플레이(7)에만 있음
		List<ExclusiveSourceRow> rows = season(20, 200, 1, 7);

		// when, then: 7번 독점으로 나온다(데이터 부족 서비스도 같은 규칙으로 판정한다)
		assertThat(ExclusiveRules.findExclusiveTitles(rows, SERVICE_IDS)).containsOnlyKeys(7L);
	}

	@Test
	void 쿠팡플레이_영화는_모름_구조적_공백이라_어떤_경우에도_쿠팡플레이_독점이_아니다() {
		// given: 영화가 어느 서비스에도 없고, 쿠팡플레이(7)는 구조적 공백(모름)
		List<ExclusiveSourceRow> rows = withStatus(movie(10, 100, 1), 1, AvailabilityStatus.NOT_AVAILABLE, null);

		// when, then: 있음이 하나도 없으니 독점이 없다
		assertThat(ExclusiveRules.findExclusiveTitles(rows, SERVICE_IDS)).isEmpty();

		// given: 넷플릭스에만 있는 영화(쿠팡플레이는 구조적 공백)
		List<ExclusiveSourceRow> netflixOnly = movie(11, 101, 1);

		// when, then: 독점은 넷플릭스(1)에만 나오고 쿠팡플레이(7)에는 나오지 않는다
		assertThat(ExclusiveRules.findExclusiveTitles(netflixOnly, SERVICE_IDS)).doesNotContainKey(7L);
	}

	@Test
	void 쿠팡플레이에_있는_드라마_시즌은_다른_서비스의_독점에서_빠진다() {
		// given: 시즌이 넷플릭스(1)와 쿠팡플레이(7) 모두 있음
		List<ExclusiveSourceRow> rows = withStatus(season(20, 200, 1, 1), 7, AvailabilityStatus.AVAILABLE, null);

		// when, then
		assertThat(ExclusiveRules.findExclusiveTitles(rows, SERVICE_IDS)).isEmpty();
	}

	@Test
	void 쿠팡플레이가_있음인_영화는_다른_서비스의_독점이_아니다() {
		// given: 넷플릭스와 쿠팡플레이 모두 있음(쿠팡플레이 영화는 보통 모름이지만 있음이면 비교에 넣는다)
		List<ExclusiveSourceRow> rows = withStatus(movie(10, 100, 1), 7, AvailabilityStatus.AVAILABLE, null);

		// when, then
		assertThat(ExclusiveRules.findExclusiveTitles(rows, SERVICE_IDS)).isEmpty();
	}

	@Test
	void 드라마는_독점인_시즌만_모아_오름차순으로_돌려주고_다른_서비스에도_있는_시즌은_뺀다() {
		// given: 시즌 1·2는 넷플릭스(1)만, 시즌 3은 넷플릭스와 디즈니+(2) 모두 있음
		List<ExclusiveSourceRow> rows = new ArrayList<>();
		rows.addAll(season(32, 300, 2, 1));
		rows.addAll(season(31, 300, 1, 1));
		rows.addAll(withStatus(season(33, 300, 3, 1), 2, AvailabilityStatus.AVAILABLE, null));

		// when
		Map<Long, List<ExclusiveTitleGroup>> result = ExclusiveRules.findExclusiveTitles(rows, SERVICE_IDS);

		// then
		assertThat(result).containsOnlyKeys(1L);
		assertThat(result.get(1L)).hasSize(1);
		ExclusiveTitleGroup group = result.get(1L).get(0);
		assertThat(group.seasonNumbers()).containsExactly(1, 2);
		assertThat(group.comparedServiceCount()).isEqualTo(6);
	}

	@Test
	void 행이_없는_서비스는_모름으로_센다() {
		// given: 시청 단위에 서비스 2·3 행이 아예 없다
		List<ExclusiveSourceRow> rows = new ArrayList<>();
		for (ExclusiveSourceRow r : season(20, 200, 1, 1)) {
			if (r.ottServiceId() != 2 && r.ottServiceId() != 3) {
				rows.add(r);
			}
		}

		// when
		ExclusiveTitleGroup group = ExclusiveRules.findExclusiveTitles(rows, SERVICE_IDS).get(1L).get(0);

		// then
		assertThat(group.comparedServiceCount()).isEqualTo(6);
		assertThat(group.unknownElsewhereCount()).isEqualTo(2);
	}
}
