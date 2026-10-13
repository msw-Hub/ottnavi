package com.ottnavi.catalog.domain;

import com.ottnavi.global.common.BaseTimeEntity;
import com.ottnavi.provider.domain.OttService;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 시청 단위 × 서비스 제공 상태(ERD availability). 상태와 tier 플래그는 changeState에서만 바꾼다 */
@Getter
@Entity
@Table(name = "availability")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Availability extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "watch_unit_id", nullable = false)
    private WatchUnit watchUnit;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ott_service_id", nullable = false)
    private OttService ottService;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AvailabilityStatus status; // AVAILABLE / NOT_AVAILABLE / UNKNOWN

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private UnknownReason unknownReason; // UNKNOWN일 때만 값이 있다

    @Column(nullable = false)
    private boolean inStandard; // STANDARD tier 제공처가 있었는지

    @Column(nullable = false)
    private boolean inAdTier; // AD tier 제공처가 있었는지(광고형 판정)

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(nullable = false)
    private Instant checkedAt; // 마지막 확인 시각

    /** 제공 상태를 처음 만든다. 값 검증은 changeState와 같은 규칙을 쓴다 */
    public static Availability create(WatchUnit watchUnit, OttService ottService, AvailabilityStatus status,
                                      UnknownReason unknownReason, boolean inStandard, boolean inAdTier, Instant checkedAt) {
        Availability availability = new Availability();
        availability.watchUnit = watchUnit;
        availability.ottService = ottService;
        availability.changeState(status, unknownReason, inStandard, inAdTier, checkedAt);
        return availability;
    }

    /**
     * 제공 상태·이유·tier 플래그·확인 시각을 한 번에 바꾼다.
     * DB CHECK(UNKNOWN일 때만 unknown_reason이 있다)와 같은 규칙을 먼저 검사해, 저장 전에 의미 있는 예외로 막는다.
     */
    public void changeState(AvailabilityStatus status, UnknownReason unknownReason, boolean inStandard,
                            boolean inAdTier, Instant checkedAt) {
        if (status == null) {
            throw new IllegalArgumentException("제공 상태가 없다");
        }
        boolean unknown = status == AvailabilityStatus.UNKNOWN;
        if (unknown && unknownReason == null) {
            throw new IllegalArgumentException("UNKNOWN 상태에는 이유가 필요하다");
        }
        if (!unknown && unknownReason != null) {
            throw new IllegalArgumentException("UNKNOWN이 아닌 상태에는 이유를 둘 수 없다: " + status);
        }
        this.status = status;
        this.unknownReason = unknownReason;
        this.inStandard = inStandard;
        this.inAdTier = inAdTier;
        this.checkedAt = checkedAt;
    }
}
