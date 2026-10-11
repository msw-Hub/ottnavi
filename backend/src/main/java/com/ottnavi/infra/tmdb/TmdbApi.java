package com.ottnavi.infra.tmdb;

import com.ottnavi.infra.tmdb.dto.TmdbMovieDetail;
import com.ottnavi.infra.tmdb.dto.TmdbMovieSummary;
import com.ottnavi.infra.tmdb.dto.TmdbPage;
import com.ottnavi.infra.tmdb.dto.TmdbProviderListResponse;
import com.ottnavi.infra.tmdb.dto.TmdbTvDetail;
import com.ottnavi.infra.tmdb.dto.TmdbTvSeasonDetail;
import com.ottnavi.infra.tmdb.dto.TmdbTvSummary;
import com.ottnavi.infra.tmdb.dto.TmdbWatchProvidersResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * TMDB v3 API 호출 목록. 파라미터는 TMDB 이름 그대로 모두 명시한다(고정값 채우기는 TmdbClient가 한다).
 * name = "tmdb"는 application.yml의 spring.cloud.openfeign.client.config.tmdb 키와 같아야 타임아웃·로그 수준이 적용된다.
 * 인증·속도 제한·오류 해석·재시도는 TmdbFeignConfig가 붙인다.
 */
@FeignClient(
        name = "tmdb",
        url = "${app.tmdb.base-url}",
        configuration = TmdbFeignConfig.class
)
public interface TmdbApi {

    // 1. 영화 후보 검색 (서비스별 KR 구독 후보 수집)
    @GetMapping("/discover/movie")
    TmdbPage<TmdbMovieSummary> discoverMovies(
            @RequestParam("with_watch_providers") String withWatchProviders,
            @RequestParam("watch_region") String watchRegion,
            @RequestParam("with_watch_monetization_types") String monetizationTypes,
            @RequestParam("language") String language,
            @RequestParam("page") int page
    );

    // 2. 드라마 후보 검색
    @GetMapping("/discover/tv")
    TmdbPage<TmdbTvSummary> discoverTvs(
            @RequestParam("with_watch_providers") String withWatchProviders,
            @RequestParam("watch_region") String watchRegion,
            @RequestParam("with_watch_monetization_types") String monetizationTypes,
            @RequestParam("language") String language,
            @RequestParam("page") int page
    );

    // 3. 영화 검색 (사용자 입력 query는 로그·지표 태그에 쓰지 않는다)
    @GetMapping("/search/movie")
    TmdbPage<TmdbMovieSummary> searchMovies(
            @RequestParam("query") String query,
            @RequestParam("language") String language,
            @RequestParam("page") int page,
            @RequestParam("include_adult") boolean includeAdult
    );

    // 4. 드라마 검색
    @GetMapping("/search/tv")
    TmdbPage<TmdbTvSummary> searchTvs(
            @RequestParam("query") String query,
            @RequestParam("language") String language,
            @RequestParam("page") int page,
            @RequestParam(value = "first_air_date_year", required = false) Integer firstAirDateYear
    );

    // 5. 영화 상세 조회 (append_to_response=credits로 출연진까지 한 번에)
    @GetMapping("/movie/{movieId}")
    TmdbMovieDetail getMovie(
            @PathVariable("movieId") long movieId,
            @RequestParam("language") String language,
            @RequestParam(value = "append_to_response", required = false) String appendToResponse
    );

    // 6. 드라마 상세 조회 (시즌 목록 포함)
    @GetMapping("/tv/{tvId}")
    TmdbTvDetail getTv(
            @PathVariable("tvId") long tvId,
            @RequestParam("language") String language,
            @RequestParam(value = "append_to_response", required = false) String appendToResponse
    );

    // 7. 드라마 시즌 상세(회차별 러닝타임)
    @GetMapping("/tv/{tvId}/season/{seasonNumber}")
    TmdbTvSeasonDetail getTvSeason(
            @PathVariable("tvId") long tvId,
            @PathVariable("seasonNumber") int seasonNumber,
            @RequestParam("language") String language
    );

    // 8. 영화 시청 제공처 (나라 코드 맵)
    @GetMapping("/movie/{movieId}/watch/providers")
    TmdbWatchProvidersResponse getMovieWatchProviders(
            @PathVariable("movieId") long movieId
    );

    // 9. 드라마 시즌별 시청 제공처
    @GetMapping("/tv/{tvId}/season/{seasonNumber}/watch/providers")
    TmdbWatchProvidersResponse getTvSeasonWatchProviders(
            @PathVariable("tvId") long tvId,
            @PathVariable("seasonNumber") int seasonNumber
    );

    // 10. 제공처 전체 목록 (mediaType은 "movie" 또는 "tv"). 7곳 외 제공처 발견용이라 거르지 않고 받는다
    @GetMapping("/watch/providers/{mediaType}")
    TmdbProviderListResponse listProviders(
            @PathVariable("mediaType") String mediaType,
            @RequestParam("watch_region") String watchRegion,
            @RequestParam("language") String language
    );
}
