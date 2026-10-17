package com.ottnavi.home.service;

import com.ottnavi.catalog.service.TitleCardAssembler;
import com.ottnavi.catalog.service.model.TitleKey;
import com.ottnavi.catalog.service.model.TitleSearchItemView;
import com.ottnavi.home.domain.entity.PopularitySnapshot;
import com.ottnavi.home.domain.enums.PopularityPeriod;
import com.ottnavi.home.domain.repository.PopularitySnapshotRepository;
import com.ottnavi.home.domain.rule.DailyPopularity;
import com.ottnavi.home.domain.rule.PopularityRankingRules;
import com.ottnavi.home.domain.rule.RankedPopularity;
import com.ottnavi.home.service.model.PopularityRankingView;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 서비스별 인기 순위 조회(Task 107, FR-22). 일별 스냅샷(popularity_snapshot)으로 순위를 만들고 TMDB를 호출하지 않는다.
 * 계산 규칙은 PopularityRankingRules 한 곳에 있다. 이 서비스는 기간(날짜 범위)을 정하고 결과에 작품 카드를 붙인다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PopularityRankingService {

	public static final String NOTICE = "TMDB 인기도 기준, OTT 공식 순위 아님"; // 화면 표기용 기준 안내 문구
	public static final int WEEK_DAYS = 7; // WEEK는 가장 최근 스냅샷 날짜를 끝으로 이 일수 범위를 본다

	private final PopularitySnapshotRepository snapshotRepository;
	private final TitleCardAssembler titleCardAssembler;

	/**
	 * 인기 순위를 돌려준다. 스냅샷이 하나도 없으면 빈 목록(snapshotDate=null, daysCounted=0)이다.
	 *
	 * @param period       TODAY(가장 최근 스냅샷 하루) 또는 WEEK(그 날짜까지 최근 7일의 평균)
	 * @param ottServiceId 한 서비스만 보려면 서비스 ID, 전체 순위면 null
	 * @param limit        돌려줄 최대 개수
	 */
	public PopularityRankingView rank(PopularityPeriod period, Long ottServiceId, int limit) {
		// 1. 가장 최근 스냅샷 날짜. 없으면 빈 순위
		LocalDate latestDate = snapshotRepository.findLatestSnapshotDate();
		if (latestDate == null) {
			return new PopularityRankingView(period, ottServiceId, null, 0, NOTICE, new ArrayList<>());
		}

		// 2. 기간의 시작 날짜와, 그 범위에서 스냅샷이 있는 날 수(평균의 분모)
		LocalDate fromDate = latestDate;
		if (period == PopularityPeriod.WEEK) {
			fromDate = latestDate.minusDays(WEEK_DAYS - 1L);
		}
		int daysCounted = snapshotRepository.findSnapshotDatesBetween(fromDate, latestDate).size();

		// 3. 범위의 스냅샷 줄을 읽어 순위 규칙에 넘긴다
		List<PopularitySnapshot> snapshots;
		if (ottServiceId == null) {
			snapshots = snapshotRepository.findBetween(fromDate, latestDate);
		} else {
			snapshots = snapshotRepository.findBetweenForService(fromDate, latestDate, ottServiceId);
		}
		List<DailyPopularity> rows = new ArrayList<>();
		for (PopularitySnapshot snapshot : snapshots) {
			rows.add(new DailyPopularity(snapshot.getSnapshotDate(), snapshot.getMediaType(), snapshot.getTmdbId(),
					snapshot.getTitle(), snapshot.getPosterPath(), snapshot.getReleaseDate(), snapshot.getPopularity()));
		}
		List<RankedPopularity> ranked = PopularityRankingRules.rank(rows, daysCounted, limit);

		// 4. 순위에 오른 작품을 화면용 카드로 바꾼다.
		//    순위 규칙이 돌려준 값은 제목·포스터·인기도뿐이라, 화면이 보여 줄 "서비스 7곳 각각의 제공 상태(있음·없음·모름)"가 없다.
		//    제공 상태는 우리 DB(수집해 둔 작품 정보)에 있으므로, 순위의 (매체, tmdbId)로 한 번에 찾아 카드로 만든다.
		//    인기 순위는 TMDB 인기 목록 기준이라 우리 DB에 아직 없는 작품이 섞일 수 있다.
		//    그때는 기록해 둔 제목·포스터·개봉연도만으로 카드를 만들고 제공 상태는 빈 목록으로 둔다(건너뛰어 순위가 끊기지 않게).
		List<TitleKey> keys = new ArrayList<>();
		for (RankedPopularity item : ranked) {
			keys.add(new TitleKey(item.mediaType(), item.tmdbId()));
		}
		Map<TitleKey, TitleSearchItemView> cards = Map.of();
		if (!keys.isEmpty()) {
			cards = titleCardAssembler.findCardsByKeys(keys);
		}
		List<PopularityRankingView.Item> items = new ArrayList<>();
		int rank = 1;
		for (RankedPopularity item : ranked) {
			TitleSearchItemView card = cards.get(new TitleKey(item.mediaType(), item.tmdbId()));
			if (card == null) {
				card = new TitleSearchItemView(item.mediaType(), item.tmdbId(), item.title(),
						releaseYear(item.releaseDate()), item.posterPath(), List.of());
			}
			items.add(new PopularityRankingView.Item(rank, card));
			rank++;
		}
		return new PopularityRankingView(period, ottServiceId, latestDate, daysCounted, NOTICE, items);
	}

	private Integer releaseYear(LocalDate releaseDate) {
		if (releaseDate == null) {
			return null;
		}
		return releaseDate.getYear();
	}
}
