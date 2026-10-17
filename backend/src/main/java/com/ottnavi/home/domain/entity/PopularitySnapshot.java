package com.ottnavi.home.domain.entity;

import com.ottnavi.catalog.domain.enums.MediaType;
import com.ottnavi.global.common.BaseTimeEntity;
import com.ottnavi.provider.domain.entity.OttService;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 서비스별 인기 순위의 일별 스냅샷 한 줄(ERD popularity_snapshot, V9).
 * 하루 한 번 TMDB discover의 인기순 상위 작품을 서비스·매체별로 저장하고, "최근 7일" 순위는 이 인기도의 평균으로 만든다.
 * 한 번 저장한 행은 바꾸지 않는다(같은 날 재실행은 행을 지우고 다시 넣는다).
 */
@Getter
@Entity
@Table(name = "popularity_snapshot")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PopularitySnapshot extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private LocalDate snapshotDate; // 스냅샷 날짜(KST)

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "ott_service_id", nullable = false)
	private OttService ottService;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private MediaType mediaType; // MOVIE / TV

	@Column(nullable = false)
	private Integer tmdbId;

	@Column(nullable = false, length = 300)
	private String title; // 한국어 제목(우리 title 표에 없는 작품도 순위에 보이도록 함께 저장)

	@Column(length = 200)
	private String posterPath; // 없으면 null

	private LocalDate releaseDate; // 영화 개봉일 / 드라마 첫 방영일, 모르면 null

	@Column(nullable = false, precision = 12, scale = 3)
	private BigDecimal popularity; // TMDB 인기도(그날 값)

	/** 스냅샷 한 줄을 만든다 */
	public static PopularitySnapshot create(LocalDate snapshotDate, OttService ottService, MediaType mediaType,
			Integer tmdbId, String title, String posterPath, LocalDate releaseDate, BigDecimal popularity) {
		PopularitySnapshot snapshot = new PopularitySnapshot();
		snapshot.snapshotDate = snapshotDate;
		snapshot.ottService = ottService;
		snapshot.mediaType = mediaType;
		snapshot.tmdbId = tmdbId;
		snapshot.title = title;
		snapshot.posterPath = posterPath;
		snapshot.releaseDate = releaseDate;
		snapshot.popularity = popularity;
		return snapshot;
	}
}
