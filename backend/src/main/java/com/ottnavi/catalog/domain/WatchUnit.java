package com.ottnavi.catalog.domain;

import com.ottnavi.global.common.BaseTimeEntity;
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
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 시청 단위: 영화 1편 또는 시즌 1개(ERD watch_unit). 찜·제공 상태·계산이 모두 이 단위를 기준으로 한다 */
@Getter
@Entity
@Table(name = "watch_unit")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WatchUnit extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "title_id", nullable = false)
    private Title title; // 소속 작품

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private UnitType unitType; // MOVIE / SEASON

    private Integer seasonNumber; // 영화는 null. 시즌 0(스페셜)은 수집하지 않는다

    private Integer tmdbSeasonId; // 시즌만

    @Column(length = 300)
    private String name; // 시즌 표시용

    @Column(length = 200)
    private String posterPath;

    private LocalDate airDate;

    @Column(nullable = false)
    private int episodeCount; // 전체 회차 수(영화는 1)

    @Column(nullable = false)
    private int airedEpisodeCount; // 방영된 회차 수

    @Column(nullable = false)
    private int totalRuntimeMin; // 방영된 회차 runtime 합 / 영화 runtime

    @Column(nullable = false)
    private int runtimeMissingCount; // runtime이 비어 기본값을 쓴 회차 수

    @Column(nullable = false)
    private boolean runtimeEstimated; // 추정값 포함 여부(화면 "추정" 표시)

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(nullable = false)
    private Instant fetchedAt;

    /** 영화 시청 단위를 만든다. 시즌 번호 없이 회차는 1로 둔다 */
    public static WatchUnit createMovie(Title title, int totalRuntimeMin, Instant fetchedAt) {
        WatchUnit unit = new WatchUnit();
        unit.title = title;
        unit.unitType = UnitType.MOVIE;
        unit.episodeCount = 1;
        unit.airedEpisodeCount = 1;
        unit.totalRuntimeMin = totalRuntimeMin;
        unit.fetchedAt = fetchedAt;
        return unit;
    }

    /** 시즌 시청 단위를 만든다. 시즌 번호는 1 이상이다(0은 수집하지 않음) */
    public static WatchUnit createSeason(Title title, int seasonNumber, Integer tmdbSeasonId, String name,
                                         int episodeCount, int airedEpisodeCount, int totalRuntimeMin, Instant fetchedAt) {
        if (seasonNumber < 1) {
            throw new IllegalArgumentException("시즌 번호는 1 이상이어야 한다: " + seasonNumber);
        }
        WatchUnit unit = new WatchUnit();
        unit.title = title;
        unit.unitType = UnitType.SEASON;
        unit.seasonNumber = seasonNumber;
        unit.tmdbSeasonId = tmdbSeasonId;
        unit.name = name;
        unit.episodeCount = episodeCount;
        unit.airedEpisodeCount = airedEpisodeCount;
        unit.totalRuntimeMin = totalRuntimeMin;
        unit.fetchedAt = fetchedAt;
        return unit;
    }
}
