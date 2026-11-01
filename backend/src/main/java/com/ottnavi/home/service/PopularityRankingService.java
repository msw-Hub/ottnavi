package com.ottnavi.home.service;

import com.ottnavi.catalog.service.TitleCardAssembler;
import com.ottnavi.catalog.service.model.TitleKey;
import com.ottnavi.catalog.service.model.TitleSearchItemView;
import com.ottnavi.global.config.RedisConfig;
import com.ottnavi.home.domain.entity.PopularitySnapshot;
import com.ottnavi.home.domain.enums.PopularityPeriod;
import com.ottnavi.home.domain.repository.PopularitySnapshotRepository;
import com.ottnavi.home.domain.rule.DailyPopularity;
import com.ottnavi.home.domain.rule.PopularFillTarget;
import com.ottnavi.home.domain.rule.PopularityRankingRules;
import com.ottnavi.home.domain.rule.RankedPopularity;
import com.ottnavi.home.service.model.PopularityRankingView;
import com.ottnavi.provider.domain.entity.OttService;
import com.ottnavi.provider.service.OttServiceQueryService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
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

	public static final String NOTICE = "최근 2년 안에 공개된 한국 작품 중 TMDB 인기도 순(OTT 공식 순위 아님)"; // 화면 표기용 기준 안내 문구
	public static final int WEEK_DAYS = 7; // WEEK는 가장 최근 스냅샷 날짜를 끝으로 이 일수 범위를 본다

	private final PopularitySnapshotRepository snapshotRepository;
	private final TitleCardAssembler titleCardAssembler;
	private final OttServiceQueryService ottServiceQueryService; // 서비스 7곳 목록(채우기 후보의 범위)

	/**
	 * 인기 순위를 돌려준다. 스냅샷이 하나도 없으면 빈 목록(snapshotDate=null, daysCounted=0)이다.
	 * 결과는 Redis에 60분(app.home.cache.popularity-ttl) 캐시하고, 인기 수집·수집·정리 Job이 끝나면 비운다.
	 *
	 * @param period       TODAY(가장 최근 스냅샷 하루) 또는 WEEK(그 날짜까지 최근 7일의 평균)
	 * @param ottServiceId 한 서비스만 보려면 서비스 ID, 전체 순위면 null
	 * @param limit        돌려줄 최대 개수
	 */
	@Cacheable(cacheNames = RedisConfig.HOME_POPULARITY,
			key = "#period.name() + ':' + (#ottServiceId ?: 'all') + ':' + #limit") // 로그인 정보는 키에 넣지 않는다(모두에게 같은 목록)
	public PopularityRankingView rank(PopularityPeriod period, Long ottServiceId, int limit) {
		// 같은 클래스 안의 호출은 프록시를 거치지 않아 @Cacheable이 적용되지 않는다. 캐시 적용은 바깥(컨트롤러)의 rank 호출에서만 일어난다
		return computeRank(period, ottServiceId, limit);
	}

	/**
	 * 캐시를 거치지 않고 DB 스냅샷으로 순위를 계산해 화면용 결과로 만든다. rank의 본문이다.
	 */
	private PopularityRankingView computeRank(PopularityPeriod period, Long ottServiceId, int limit) {
		// 1. 스냅샷으로 순위를 계산한다(카드 없이)
		RankedResult result = loadRanked(period, ottServiceId, limit);
		LocalDate latestDate = result.latestDate();
		int daysCounted = result.daysCounted();
		List<RankedPopularity> ranked = result.ranked();
		if (latestDate == null) {
			return new PopularityRankingView(period, ottServiceId, null, 0, NOTICE, new ArrayList<>());
		}

		// 2. 순위에 오른 작품을 화면용 카드로 바꾼다.
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

	/** 순위 계산 결과(카드를 붙이기 전). 스냅샷이 없으면 latestDate=null, daysCounted=0이고 ranked는 빈 목록이다 */
	private record RankedResult(LocalDate latestDate, int daysCounted, List<RankedPopularity> ranked) {
	}

	/**
	 * 스냅샷으로 순위만 계산한다(작품 카드·제공 상태를 읽지 않는다). computeRank와 findFillCandidates가 함께 쓴다.
	 * 순위 계산 규칙을 두 곳에 두지 않으려고 뺐다.
	 */
	private RankedResult loadRanked(PopularityPeriod period, Long ottServiceId, int limit) {
		// 1. 가장 최근 기록 날짜. 없으면 빈 순위
		//    서비스를 지정하면 그 서비스의 기록만 본다. 전체 기준으로 날짜를 구하면, 그 서비스만 TMDB 호출이 실패한 날
		//    "가장 최근 날"에 그 서비스 기록이 없어 오늘 순위가 비어 버리기 때문이다.
		LocalDate latestDate;
		if (ottServiceId == null) {
			latestDate = snapshotRepository.findLatestSnapshotDate();
		} else {
			latestDate = snapshotRepository.findLatestSnapshotDateForService(ottServiceId);
		}
		if (latestDate == null) {
			return new RankedResult(null, 0, new ArrayList<>());
		}

		// 2. 기간의 시작 날짜와, 그 범위에서 스냅샷이 있는 날 수(평균의 분모)
		LocalDate fromDate = latestDate;
		if (period == PopularityPeriod.WEEK) {
			fromDate = latestDate.minusDays(WEEK_DAYS - 1L);
		}
		//    서비스를 지정하면 그 서비스의 기록이 있는 날만 센다(다른 서비스만 기록된 날이 분모를 부풀려 평균이 낮아지지 않게)
		int daysCounted;
		if (ottServiceId == null) {
			daysCounted = snapshotRepository.findSnapshotDatesBetween(fromDate, latestDate).size();
		} else {
			daysCounted = snapshotRepository.findSnapshotDatesBetweenForService(fromDate, latestDate, ottServiceId).size();
		}

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
		return new RankedResult(latestDate, daysCounted, ranked);
	}

	/**
	 * 인기 수집 직후 "title 표에 없는 작품의 상세를 채울" 후보를 순위별로 돌려준다(캐시를 거치지 않고 방금 쌓은 스냅샷으로 계산).
	 * 순위 16개(기간 TODAY·WEEK x 범위 전체 + 서비스 7곳)의 상위 topN개를 각각 한 목록으로 담고, 목록 순서가 곧 우선순위다:
	 * 전체 TODAY, 전체 WEEK, 서비스1 TODAY, 서비스1 WEEK, 서비스2 TODAY ... 순이다(눈에 띄는 전체 순위부터 채우려는 것).
	 * 같은 작품이 여러 목록에 나올 수 있다. 중복 제거·이미 있는 작품 제외·상한은 PopularityFillTargets가 한다.
	 * 왜 rank를 부르지 않나: rank는 Redis 캐시(60분)가 걸려 있어 다른 빈이 부르면 방금 수집 전의 옛 순위를 받을 수 있다.
	 *
	 * @param topN 순위마다 보는 상위 작품 수
	 */
	public List<List<PopularFillTarget>> findFillCandidates(int topN) {
		// 1. 범위 목록: 전체(null)를 맨 앞에, 그다음 서비스를 화면 순서대로
		List<Long> scopes = new ArrayList<>();
		scopes.add(null);
		for (OttService service : ottServiceQueryService.listOttServices()) {
			scopes.add(service.getId());
		}

		// 2. 범위마다 TODAY, WEEK 순위를 계산해 후보 목록으로 바꾼다
		List<List<PopularFillTarget>> rankings = new ArrayList<>();
		for (Long ottServiceId : scopes) {
			for (PopularityPeriod period : PopularityPeriod.values()) {
				RankedResult result = loadRanked(period, ottServiceId, topN);
				List<PopularFillTarget> targets = new ArrayList<>();
				for (RankedPopularity item : result.ranked()) {
					targets.add(new PopularFillTarget(item.mediaType(), item.tmdbId(), item.title()));
				}
				rankings.add(targets);
			}
		}
		return rankings;
	}

	private Integer releaseYear(LocalDate releaseDate) {
		if (releaseDate == null) {
			return null;
		}
		return releaseDate.getYear();
	}
}
