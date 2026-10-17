package com.ottnavi.home.controller;

import com.ottnavi.catalog.service.model.GenreInfo;
import com.ottnavi.global.common.CommonResponse;
import com.ottnavi.global.config.HomeCacheProperties;
import com.ottnavi.home.domain.enums.PopularityPeriod;
import com.ottnavi.home.dto.response.GenreResponse;
import com.ottnavi.home.dto.response.NewlyAvailableTitleListResponse;
import com.ottnavi.home.dto.response.PopularityRankingResponse;
import com.ottnavi.home.dto.response.TitleExplorePageResponse;
import com.ottnavi.home.dto.response.WishlistedTitleListResponse;
import com.ottnavi.home.service.HomeQueryService;
import com.ottnavi.home.service.PopularityRankingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 홈·탐색 공개 API(Task 107). 개인화 없이 모두에게 같은 목록이며, 비로그인으로 쓰고 요청 제한이 적용된다.
 * 출연자별 작품을 뺀 5개는 브라우저 캐시 헤더가 붙는다(출연자 이름마다 응답이 달라 캐시하지 않는다).
 */
@Tag(name = "catalog")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/public")
public class HomeController {

	private final PopularityRankingService popularityRankingService;
	private final HomeQueryService homeQueryService;
	private final HomeCacheProperties cacheProperties;

	/**
	 * 응답에 브라우저 캐시 헤더(Cache-Control: public, max-age=N)를 붙인다. N은 app.home.cache.browser-seconds다.
	 * 컨트롤러가 직접 넣은 Cache-Control은 Spring Security가 덮어쓰지 않으므로(기본은 no-store) 이 응답에만 적용된다.
	 * 오류 응답은 이 메서드를 거치지 않아 캐시 헤더가 붙지 않는다. Vercel 엣지 캐시용 s-maxage는 확인 전이라 넣지 않는다.
	 */
	private <T> ResponseEntity<CommonResponse<T>> browserCached(CommonResponse<T> body) {
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "public, max-age=" + cacheProperties.browserSeconds())
				.body(body);
	}

	/** 서비스별(또는 전체) 인기 순위를 오늘 또는 최근 7일 평균으로 돌려준다 */
	@Operation(operationId = "listPopularityRankings", summary = "서비스별 인기 순위 조회")
	@GetMapping("/popularity-rankings")
	public ResponseEntity<CommonResponse<PopularityRankingResponse>> listPopularityRankings(
			@RequestParam(required = false) PopularityPeriod period,
			@RequestParam(required = false) @Min(value = 1, message = "1 이상이어야 합니다.") Long ottServiceId,
			@RequestParam(defaultValue = "20")
			@Min(value = 1, message = "1 이상이어야 합니다.")
			@Max(value = 20, message = "20 이하여야 합니다.") int limit) {
		// period를 생략하면 TODAY다(기본값을 계약 쪽 $ref 열거형에 둘 수 없어 코드에서 채운다)
		PopularityPeriod effectivePeriod = period;
		if (effectivePeriod == null) {
			effectivePeriod = PopularityPeriod.TODAY;
		}
		return browserCached(CommonResponse.success(PopularityRankingResponse.from(
				popularityRankingService.rank(effectivePeriod, ottServiceId, limit))));
	}

	/** 찜 사용자 수가 많은 작품을 돌려준다(최소 인원 미만은 뺀다) */
	@Operation(operationId = "listWishlistedTitles", summary = "찜 많은 작품 조회")
	@GetMapping("/wishlisted-titles")
	public ResponseEntity<CommonResponse<WishlistedTitleListResponse>> listWishlistedTitles(
			@RequestParam(defaultValue = "10")
			@Min(value = 1, message = "1 이상이어야 합니다.")
			@Max(value = 20, message = "20 이하여야 합니다.") int limit) {
		return browserCached(CommonResponse.success(WishlistedTitleListResponse.from(homeQueryService.listWishlistedTitles(limit))));
	}

	/** 최근 서비스에서 새로 볼 수 있게 된 작품을 최신순으로 돌려준다 */
	@Operation(operationId = "listNewlyAvailableTitles", summary = "새로 볼 수 있게 된 작품 조회")
	@GetMapping("/newly-available-titles")
	public ResponseEntity<CommonResponse<NewlyAvailableTitleListResponse>> listNewlyAvailableTitles(
			@RequestParam(required = false) @Min(value = 1, message = "1 이상이어야 합니다.") Long ottServiceId,
			@RequestParam(defaultValue = "20")
			@Min(value = 1, message = "1 이상이어야 합니다.")
			@Max(value = 50, message = "50 이하여야 합니다.") int limit) {
		return browserCached(CommonResponse.success(NewlyAvailableTitleListResponse.from(
				homeQueryService.listNewlyAvailableTitles(ottServiceId, limit))));
	}

	/** 작품이 연결된 장르 목록을 돌려준다 */
	@Operation(operationId = "listGenres", summary = "장르 목록 조회")
	@GetMapping("/genres")
	public ResponseEntity<CommonResponse<List<GenreResponse>>> listGenres() {
		List<GenreResponse> genres = new ArrayList<>();
		for (GenreInfo genre : homeQueryService.listGenres()) {
			genres.add(GenreResponse.from(genre));
		}
		return browserCached(CommonResponse.success(genres));
	}

	/** 장르별 작품을 인기도 순으로 한 페이지 돌려준다 */
	@Operation(operationId = "listGenreTitles", summary = "장르별 작품 조회")
	@GetMapping("/genres/{genreId}/titles")
	public ResponseEntity<CommonResponse<TitleExplorePageResponse>> listGenreTitles(
			@PathVariable @Min(value = 1, message = "1 이상이어야 합니다.") Integer genreId,
			@RequestParam(defaultValue = "0") @Min(value = 0, message = "0 이상이어야 합니다.") int page,
			@RequestParam(defaultValue = "20")
			@Min(value = 1, message = "1 이상이어야 합니다.")
			@Max(value = 100, message = "100 이하여야 합니다.") int size) {
		return browserCached(CommonResponse.success(TitleExplorePageResponse.from(homeQueryService.listTitlesByGenre(genreId, page, size))));
	}

	/** 출연자 이름이 같은 작품을 인기도 순으로 한 페이지 돌려준다 */
	@Operation(operationId = "listPersonTitles", summary = "인물(출연자)별 작품 조회")
	@GetMapping("/people/titles")
	public CommonResponse<TitleExplorePageResponse> listPersonTitles(
			@RequestParam String name,
			@RequestParam(defaultValue = "0") @Min(value = 0, message = "0 이상이어야 합니다.") int page,
			@RequestParam(defaultValue = "20")
			@Min(value = 1, message = "1 이상이어야 합니다.")
			@Max(value = 100, message = "100 이하여야 합니다.") int size) {
		return CommonResponse.success(TitleExplorePageResponse.from(homeQueryService.listTitlesByCastName(name, page, size)));
	}
}
