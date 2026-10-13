package com.ottnavi.catalog.domain;

import com.ottnavi.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 작품(영화·드라마 공통, ERD title). (mediaType, tmdbId)가 유일하다 */
@Getter
@Entity
@Table(name = "title")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Title extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MediaType mediaType; // MOVIE / TV

    @Column(nullable = false)
    private Integer tmdbId; // TMDB 작품 ID. 영화와 드라마가 겹칠 수 있다

    @Column(nullable = false, length = 300)
    private String title; // 한국어 제목(없으면 원제)

    @Column(length = 300)
    private String originalTitle;

    @Column(columnDefinition = "TEXT")
    private String overview; // 줄거리, 비어 있을 수 있다

    @Column(length = 5)
    private String overviewLang; // ko / en, 영어로 대체했는지 표시

    private LocalDate releaseDate; // 영화 개봉일 / 드라마 첫 방영일

    @Column(length = 200)
    private String posterPath;

    @Column(length = 200)
    private String backdropPath;

    @Column(length = 10)
    private String originalLanguage;

    @Column(length = 30)
    private String tvStatus; // 드라마만(Returning Series, Ended 등)

    @Column(precision = 10, scale = 3)
    private BigDecimal popularity; // NUMERIC이라 Double이 아닌 BigDecimal

    @Column(precision = 4, scale = 2)
    private BigDecimal voteAverage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RefreshTier refreshTier; // DAILY(찜에 있음) / WEEKLY(나머지)

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(nullable = false)
    private Instant fetchedAt; // 마지막 수집 시각

    /** 필수 값만으로 작품을 만든다. 갱신 주기는 WEEKLY로 시작한다 */
    public static Title create(MediaType mediaType, Integer tmdbId, String title, Instant fetchedAt) {
        Title entity = new Title();
        entity.mediaType = mediaType;
        entity.tmdbId = tmdbId;
        entity.title = title;
        entity.refreshTier = RefreshTier.WEEKLY; // DB 기본값과 같다. Hibernate는 DB 기본값을 쓰지 않으므로 직접 채운다
        entity.fetchedAt = fetchedAt;
        return entity;
    }

    /** TMDB 상세 응답으로 선택 필드를 채우거나 갱신한다. 수집 시각도 함께 바꾼다 */
    public void updateDetail(String title, String originalTitle, String overview, String overviewLang,
                             LocalDate releaseDate, String posterPath, String backdropPath, String originalLanguage,
                             String tvStatus, BigDecimal popularity, BigDecimal voteAverage, Instant fetchedAt) {
        this.title = title;
        this.originalTitle = originalTitle;
        this.overview = overview;
        this.overviewLang = overviewLang;
        this.releaseDate = releaseDate;
        this.posterPath = posterPath;
        this.backdropPath = backdropPath;
        this.originalLanguage = originalLanguage;
        this.tvStatus = tvStatus;
        this.popularity = popularity;
        this.voteAverage = voteAverage;
        this.fetchedAt = fetchedAt;
    }

    /** 갱신 주기를 바꾼다(찜에 들어오면 DAILY, 빠지면 WEEKLY) */
    public void changeRefreshTier(RefreshTier refreshTier) {
        this.refreshTier = refreshTier;
    }
}
