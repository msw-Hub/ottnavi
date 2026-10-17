package com.ottnavi.home.service;

import com.ottnavi.catalog.service.NewlyAvailableQueryService;
import com.ottnavi.catalog.service.TitleCardAssembler;
import com.ottnavi.catalog.service.TitleExploreService;
import com.ottnavi.catalog.service.model.GenreInfo;
import com.ottnavi.catalog.service.model.NewlyAvailableResult;
import com.ottnavi.catalog.service.model.TitleCardsById;
import com.ottnavi.catalog.service.model.TitleSearchItemView;
import com.ottnavi.catalog.service.model.TitleSearchResult;
import com.ottnavi.global.config.RedisConfig;
import com.ottnavi.home.service.model.WishlistedTitleListView;
import com.ottnavi.wishlist.service.WishlistService;
import com.ottnavi.wishlist.service.model.TitleWishlistCount;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 홈·탐색의 공개 조회(Task 107). 찜 많은 작품, 새로 볼 수 있게 된 작품, 장르·인물 탐색을 각 도메인 서비스에서 받아 돌려준다.
 * 기준 수치(최소 찜 인원, 입고 14일, 공개 30일)는 HomeProperties(app.home.*)에서 읽는다.
 * 모두에게 같은 목록이라 출연자별 작품을 뺀 조회는 Redis에 캐시한다(이름·TTL은 RedisConfig, 값은 app.home.cache.*).
 * 캐시 키에는 사용자 정보가 들어가지 않는다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class HomeQueryService {

	private final HomeProperties properties;
	private final WishlistService wishlistService;
	private final TitleCardAssembler titleCardAssembler;
	private final NewlyAvailableQueryService newlyAvailableQueryService;
	private final TitleExploreService titleExploreService;

	/**
	 * 찜한 사용자 수가 많은 작품을 많은 순으로 돌려준다. 사용자가 minWishlistUsers명보다 적은 작품은 뺀다(결과가 없으면 빈 목록).
	 * 사용자 수는 "다 봤음"을 제외하고 작품 단위로 센다.
	 */
	@Cacheable(cacheNames = RedisConfig.HOME_WISHLISTED, key = "#limit")
	public WishlistedTitleListView listWishlistedTitles(int limit) {
		// 1. 작품별 찜 사용자 수(최소 인원 이상, 많은 순)
		List<TitleWishlistCount> counts = wishlistService.listMostWishlistedTitles(properties.minWishlistUsers(), limit);

		// 2. 작품을 카드로 만든다
		List<Long> titleIds = new ArrayList<>();
		for (TitleWishlistCount count : counts) {
			titleIds.add(count.titleId());
		}
		TitleCardsById cards = titleCardAssembler.assembleByIds(titleIds);

		// 3. 사용자 수가 많은 순서를 지키며 카드와 짝짓는다(DB에 없는 작품은 건너뛴다)
		List<WishlistedTitleListView.Item> items = new ArrayList<>();
		for (TitleWishlistCount count : counts) {
			TitleSearchItemView card = cards.byTitleId().get(count.titleId());
			if (card != null) {
				items.add(new WishlistedTitleListView.Item(card, count.userCount()));
			}
		}
		return new WishlistedTitleListView(properties.minWishlistUsers(), cards.dataAsOf(), items);
	}

	/** 새로 볼 수 있게 된 작품을 최신순으로 돌려준다. ottServiceId가 null이면 전체 서비스다 */
	@Cacheable(cacheNames = RedisConfig.HOME_NEW_ARRIVALS, key = "(#ottServiceId ?: 'all') + ':' + #limit")
	public NewlyAvailableResult listNewlyAvailableTitles(Long ottServiceId, int limit) {
		return newlyAvailableQueryService.find(ottServiceId, properties.arrivalDays(), properties.releaseDays(), limit);
	}

	/** 작품이 연결된 장르 목록 */
	@Cacheable(cacheNames = RedisConfig.HOME_GENRES, key = "'all'")
	public List<GenreInfo> listGenres() {
		return titleExploreService.listGenres();
	}

	/** 장르별 작품 한 페이지 */
	@Cacheable(cacheNames = RedisConfig.HOME_GENRE_TITLES, key = "#genreId + ':' + #page + ':' + #size")
	public TitleSearchResult listTitlesByGenre(int genreId, int page, int size) {
		return titleExploreService.listTitlesByGenre(genreId, page, size);
	}

	/** 출연자 이름별 작품 한 페이지. 검색어마다 캐시 키가 생겨 캐시가 불어나므로 캐시하지 않는다 */
	public TitleSearchResult listTitlesByCastName(String name, int page, int size) {
		return titleExploreService.listTitlesByCastName(name, page, size);
	}
}
