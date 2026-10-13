package com.ottnavi.catalog.domain.rule;

import com.ottnavi.catalog.domain.enums.AvailabilityStatus;
import java.util.Objects;
import java.util.Optional;

/** 새 판정과 기존 제공 상태를 비교해 이력을 남길지 정한다. 저장과 이력 엔티티 생성은 Task 044가 한다 */
public final class AvailabilityChangeDetector {

    private AvailabilityChangeDetector() {
    }

    /**
     * 상태가 바뀌었거나 처음 생긴 경우에만 (before, after)를 돌려준다.
     * 이유(unknown_reason)나 tier 플래그만 바뀌는 것은 이력 대상이 아니다(이력은 status 변화만 기록한다).
     *
     * @param before 기존 상태. 아직 availability 행이 없으면 null
     * @param after 이번 판정 상태(null이면 안 된다)
     */
    public static Optional<StatusChange> detect(AvailabilityStatus before, AvailabilityStatus after) {
        Objects.requireNonNull(after, "새 제공 상태가 없다");
        if (before == after) {
            return Optional.empty();
        }
        return Optional.of(new StatusChange(before, after));
    }
}
