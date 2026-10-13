package com.ottnavi.catalog.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ottnavi.catalog.domain.enums.AvailabilityStatus;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 제공 상태 변경 비교 단위 테스트(Task 042). 이력을 남길 경우와 남기지 않을 경우를 가른다 */
class AvailabilityChangeDetectorTest {

    @Test
    void 기존_상태가_없으면_신규로_이전_상태가_null인_변경을_돌려준다() {
        Optional<StatusChange> change = AvailabilityChangeDetector.detect(null, AvailabilityStatus.AVAILABLE);

        assertThat(change).contains(new StatusChange(null, AvailabilityStatus.AVAILABLE));
    }

    @Test
    void 상태가_바뀌면_이전과_이후를_돌려준다() {
        Optional<StatusChange> change = AvailabilityChangeDetector.detect(
                AvailabilityStatus.AVAILABLE, AvailabilityStatus.NOT_AVAILABLE);

        assertThat(change).contains(new StatusChange(AvailabilityStatus.AVAILABLE, AvailabilityStatus.NOT_AVAILABLE));
    }

    @Test
    void 모름에서_있음으로_바뀌어도_변경이다() {
        assertThat(AvailabilityChangeDetector.detect(AvailabilityStatus.UNKNOWN, AvailabilityStatus.AVAILABLE)).isPresent();
    }

    @Test
    void 상태가_같으면_변경이_없다() {
        assertThat(AvailabilityChangeDetector.detect(AvailabilityStatus.UNKNOWN, AvailabilityStatus.UNKNOWN)).isEmpty();
        assertThat(AvailabilityChangeDetector.detect(AvailabilityStatus.AVAILABLE, AvailabilityStatus.AVAILABLE)).isEmpty();
    }

    @Test
    void 새_상태가_null이면_거부한다() {
        assertThatThrownBy(() -> AvailabilityChangeDetector.detect(AvailabilityStatus.AVAILABLE, null))
                .isInstanceOf(NullPointerException.class);
    }
}
