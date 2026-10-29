package com.ottnavi.catalog.domain.entity;

import com.ottnavi.catalog.domain.enums.MediaType;
import com.ottnavi.catalog.domain.enums.RefreshTier;
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
import java.util.Objects;

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

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    private Instant overviewTranslatedAt; // 관리자가 반영한 번역의 반영 시각. null이면 번역이 아니다(TMDB가 준 줄거리이거나 없음)

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

    /**
     * TMDB 상세 응답으로 선택 필드를 채우거나 갱신한다. 수집 시각도 함께 바꾼다.
     * isOverviewTranslated가 true면 관리자 번역을 그대로 둔다는 뜻이라 번역 시각을 유지하고(줄거리도 기존 번역이 넘어온다),
     * false면 줄거리가 TMDB 값이 되므로 번역 시각을 비운다.
     */
    public void updateDetail(String title, String originalTitle, String overview, String overviewLang,
                             boolean isOverviewTranslated, LocalDate releaseDate, String posterPath, String backdropPath, String originalLanguage,
                             String tvStatus, BigDecimal popularity, BigDecimal voteAverage, Instant fetchedAt) {
        this.title = title;
        this.originalTitle = originalTitle;
        this.overview = overview;
        this.overviewLang = overviewLang;
        if (!isOverviewTranslated) {
            this.overviewTranslatedAt = null;
        }
        this.releaseDate = releaseDate;
        this.posterPath = posterPath;
        this.backdropPath = backdropPath;
        this.originalLanguage = originalLanguage;
        this.tvStatus = tvStatus;
        this.popularity = popularity;
        this.voteAverage = voteAverage;
        this.fetchedAt = fetchedAt;
    }

    /** 줄거리가 관리자가 반영한 번역인지 */
    public boolean isOverviewTranslated() {
        return overviewTranslatedAt != null;
    }

    /** 갱신 주기를 바꾼다(찜에 들어오면 DAILY, 빠지면 WEEKLY) */
    public void changeRefreshTier(RefreshTier refreshTier) {
        this.refreshTier = refreshTier;
    }

    /**
     * discover 목록 요약 값으로 작품을 만든다(수집 Job 후보 단계). create에 요약 필드를 얹은 것이다.
     * 줄거리·줄거리 언어·드라마 상태는 비워 둔다(한국어→영어 대체 규칙과 함께 상세 수집이 채운다, updateDetail).
     */
    public static Title createFromSummary(MediaType mediaType, Integer tmdbId, String title, String originalTitle,
                                          LocalDate releaseDate, String posterPath, String backdropPath,
                                          String originalLanguage, BigDecimal popularity, BigDecimal voteAverage,
                                          Instant fetchedAt) {
        Title entity = create(mediaType, tmdbId, title, fetchedAt);
        entity.originalTitle = originalTitle;
        entity.releaseDate = releaseDate;
        entity.posterPath = posterPath;
        entity.backdropPath = backdropPath;
        entity.originalLanguage = originalLanguage;
        entity.popularity = popularity;
        entity.voteAverage = voteAverage;
        return entity;
    }

    /**
     * 자주 바뀌는 요약 값(포스터·배경·인기도·평점)만 갱신한다. fetchedAt은 상세 수집 시각이라 건드리지 않는다.
     * 값이 같으면 대입하지 않는다: 더티 체킹이 변경 없음으로 보아 UPDATE가 나가지 않는다(BigDecimal은 scale 차이를 무시하고 비교).
     */
    public void refreshSummary(String posterPath, String backdropPath, BigDecimal popularity, BigDecimal voteAverage) {
        if (!Objects.equals(this.posterPath, posterPath)) {
            this.posterPath = posterPath;
        }
        if (!Objects.equals(this.backdropPath, backdropPath)) {
            this.backdropPath = backdropPath;
        }
        if (differs(this.popularity, popularity)) {
            this.popularity = popularity;
        }
        if (differs(this.voteAverage, voteAverage)) {
            this.voteAverage = voteAverage;
        }
    }

    private static boolean differs(BigDecimal current, BigDecimal incoming) {
        if (current == null || incoming == null) {
            return current != incoming;
        }
        return current.compareTo(incoming) != 0;
    }

}
