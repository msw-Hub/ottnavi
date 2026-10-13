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

/** 제공 상태 변경 이력(ERD availability_change). 수집이 계속 쌓기만 하고 수정하지 않는다 */
@Getter
@Entity
@Table(name = "availability_change")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AvailabilityChange extends BaseTimeEntity {

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
    @Column(length = 20)
    private AvailabilityStatus beforeStatus; // 신규는 null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AvailabilityStatus afterStatus;

    private Long jobExecutionId; // Spring Batch 실행 ID(FK 없이 참조값만). 배치 밖 수집이면 null

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(nullable = false)
    private Instant changedAt;

    /** 변경 이력 한 줄을 만든다. beforeStatus가 null이면 신규 등록이다 */
    public static AvailabilityChange create(WatchUnit watchUnit, OttService ottService,
                                            AvailabilityStatus beforeStatus, AvailabilityStatus afterStatus, Long jobExecutionId, Instant changedAt) {
        AvailabilityChange change = new AvailabilityChange();
        change.watchUnit = watchUnit;
        change.ottService = ottService;
        change.beforeStatus = beforeStatus;
        change.afterStatus = afterStatus;
        change.jobExecutionId = jobExecutionId;
        change.changedAt = changedAt;
        return change;
    }
}
