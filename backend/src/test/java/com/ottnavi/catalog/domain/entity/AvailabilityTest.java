package com.ottnavi.catalog.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ottnavi.catalog.domain.enums.AvailabilityStatus;
import com.ottnavi.catalog.domain.enums.UnknownReason;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Availability 도메인 메서드 단위 테스트(Task 035). Spring 없이 DB CHECK와 같은 규칙이 저장 전에 막히는지 본다 */
class AvailabilityTest {

    private static final Instant NOW = Instant.parse("2026-10-13T01:02:03Z");

    @Test
    void 모름_상태인데_이유가_없으면_거부한다() {
        assertThatThrownBy(() -> Availability.create(
                null, null, AvailabilityStatus.UNKNOWN, null, false, false, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 모름이_아닌데_이유가_있으면_거부한다() {
        assertThatThrownBy(() -> Availability.create(
                null, null, AvailabilityStatus.AVAILABLE, UnknownReason.NO_KR_DATA, true, false, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 상태를_바꾸면_이유와_플래그가_함께_바뀐다() {
        Availability availability = Availability.create(
                null, null, AvailabilityStatus.UNKNOWN, UnknownReason.NO_KR_DATA, false, false, NOW);

        availability.changeState(AvailabilityStatus.AVAILABLE, null, false, true, NOW.plusSeconds(1));

        assertThat(availability.getStatus()).isEqualTo(AvailabilityStatus.AVAILABLE);
        assertThat(availability.getUnknownReason()).isNull();
        assertThat(availability.isInAdTier()).isTrue();
    }

    @Test
    void 검증에_실패하면_기존_상태가_바뀌지_않는다() {
        Availability availability = Availability.create(
                null, null, AvailabilityStatus.AVAILABLE, null, true, false, NOW);

        assertThatThrownBy(() -> availability.changeState(AvailabilityStatus.UNKNOWN, null, false, false, NOW))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(availability.getStatus()).isEqualTo(AvailabilityStatus.AVAILABLE);
        assertThat(availability.isInStandard()).isTrue();
    }
}
