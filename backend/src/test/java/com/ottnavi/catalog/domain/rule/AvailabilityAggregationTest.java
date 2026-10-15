package com.ottnavi.catalog.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.catalog.domain.enums.AvailabilityStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 제공 상태 합치기·데이터 기준일 규칙 단위 테스트(Task 050·051). Spring 없이 입력별 기대값만 확인한다 */
class AvailabilityAggregationTest {

    private static final List<Long> SERVICE_IDS = List.of(1L, 2L, 3L);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);

    @Test
    void 시즌_중_하나라도_있음이면_작품은_있음이다() {
        // given
        List<AvailabilityStatus> statuses = List.of(
                AvailabilityStatus.NOT_AVAILABLE, AvailabilityStatus.UNKNOWN, AvailabilityStatus.AVAILABLE);

        // when
        AvailabilityStatus merged = AvailabilityAggregation.merge(statuses);

        // then
        assertThat(merged).isEqualTo(AvailabilityStatus.AVAILABLE);
    }

    @Test
    void 있음이_없고_모름이_하나라도_있으면_작품은_모름이다() {
        // given
        List<AvailabilityStatus> statuses = List.of(AvailabilityStatus.NOT_AVAILABLE, AvailabilityStatus.UNKNOWN);

        // when
        AvailabilityStatus merged = AvailabilityAggregation.merge(statuses);

        // then
        assertThat(merged).isEqualTo(AvailabilityStatus.UNKNOWN);
    }

    @Test
    void 전부_없음이면_작품은_없음이다() {
        // given
        List<AvailabilityStatus> statuses = List.of(AvailabilityStatus.NOT_AVAILABLE, AvailabilityStatus.NOT_AVAILABLE);

        // when
        AvailabilityStatus merged = AvailabilityAggregation.merge(statuses);

        // then
        assertThat(merged).isEqualTo(AvailabilityStatus.NOT_AVAILABLE);
    }

    @Test
    void 시청_단위가_없으면_모름이다() {
        // given
        List<AvailabilityStatus> statuses = List.of();

        // when
        AvailabilityStatus merged = AvailabilityAggregation.merge(statuses);

        // then
        assertThat(merged).isEqualTo(AvailabilityStatus.UNKNOWN);
    }

    @Test
    void 판정_행이_없는_서비스는_모름으로_채운다() {
        // given: 서비스 1만 판정이 있다
        Map<Long, AvailabilityStatus> rows = Map.of(1L, AvailabilityStatus.AVAILABLE);

        // when
        Map<Long, AvailabilityStatus> result = AvailabilityAggregation.statusByService(SERVICE_IDS, rows);

        // then
        assertThat(result).containsExactly(
                Map.entry(1L, AvailabilityStatus.AVAILABLE),
                Map.entry(2L, AvailabilityStatus.UNKNOWN),
                Map.entry(3L, AvailabilityStatus.UNKNOWN));
    }

    @Test
    void 시즌이_둘이면_서비스별로_합치고_행이_없는_시즌은_모름으로_본다() {
        // given: 시즌1은 서비스1 있음·서비스2 없음, 시즌2는 서비스2 판정 행이 없고 서비스3만 없음
        Map<Long, AvailabilityStatus> season1 = Map.of(
                1L, AvailabilityStatus.AVAILABLE,
                2L, AvailabilityStatus.NOT_AVAILABLE,
                3L, AvailabilityStatus.NOT_AVAILABLE);
        Map<Long, AvailabilityStatus> season2 = Map.of(
                1L, AvailabilityStatus.NOT_AVAILABLE,
                3L, AvailabilityStatus.NOT_AVAILABLE);

        // when
        Map<Long, AvailabilityStatus> result = AvailabilityAggregation.mergeByService(SERVICE_IDS, List.of(season1, season2));

        // then
        assertThat(result).containsExactly(
                Map.entry(1L, AvailabilityStatus.AVAILABLE),
                Map.entry(2L, AvailabilityStatus.UNKNOWN),
                Map.entry(3L, AvailabilityStatus.NOT_AVAILABLE));
    }

    @Test
    void 데이터_기준일은_가장_오래된_확인_시각의_KST_날짜다() {
        // given: 2026-10-14 15:30Z는 KST로 10월 15일 00:30, 다른 하나는 10월 16일
        List<Instant> checkedAts = List.of(Instant.parse("2026-10-15T20:00:00Z"), Instant.parse("2026-10-14T15:30:00Z"));

        // when
        LocalDate dataAsOf = AvailabilityAggregation.dataAsOf(checkedAts, Instant.parse("2026-12-01T00:00:00Z"), TODAY);

        // then
        assertThat(dataAsOf).isEqualTo(LocalDate.of(2026, 10, 15));
    }

    @Test
    void 대상_판정이_없으면_전체_최신_확인일을_쓴다() {
        // given
        Instant latestOverall = Instant.parse("2026-10-10T16:00:00Z"); // KST 10월 11일 01:00

        // when
        LocalDate dataAsOf = AvailabilityAggregation.dataAsOf(List.of(), latestOverall, TODAY);

        // then
        assertThat(dataAsOf).isEqualTo(LocalDate.of(2026, 10, 11));
    }

    @Test
    void 판정이_전혀_없으면_오늘이다() {
        // when
        LocalDate dataAsOf = AvailabilityAggregation.dataAsOf(List.of(), null, TODAY);

        // then
        assertThat(dataAsOf).isEqualTo(TODAY);
    }
}
