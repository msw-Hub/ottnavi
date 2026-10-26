package com.ottnavi.infra.tmdb;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.infra.tmdb.dto.TmdbMovieDetail;
import com.ottnavi.infra.tmdb.dto.TmdbMovieSummary;
import com.ottnavi.infra.tmdb.dto.TmdbPage;
import com.ottnavi.infra.tmdb.dto.TmdbProviderListResponse;
import com.ottnavi.infra.tmdb.dto.TmdbRegionProviders;
import com.ottnavi.infra.tmdb.dto.TmdbTvDetail;
import com.ottnavi.infra.tmdb.dto.TmdbTvSeasonDetail;
import com.ottnavi.infra.tmdb.dto.TmdbTvSummary;
import com.ottnavi.infra.tmdb.dto.TmdbWatchProvidersResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

class TmdbClientTest extends TmdbTestSupport {

    @Autowired
    private Environment environment;

    // T1: 정상 discover. 헤더·쿼리·경로가 맞고 JSON이 record로 매핑된다 (Jackson 3 + Feign 디코더가 실제로 동작한다는 증거)
    @Test
    void t1_discoverMovies_정상_응답이_DTO로_매핑된다() {
        wireMock.stubFor(get(urlPathEqualTo(DISCOVER_MOVIE_PATH))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                .withQueryParam("with_watch_providers", equalTo("8"))
                .withQueryParam("watch_region", equalTo("KR"))
                .withQueryParam("with_watch_monetization_types", equalTo("flatrate"))
                .withQueryParam("language", equalTo("ko-KR"))
                .withQueryParam("page", equalTo("1"))
                .willReturn(okJson("discover-movie.json")));

        TmdbPage<TmdbMovieSummary> page = tmdbClient.discoverMovies("8", 1);

        assertThat(page.page()).isEqualTo(1);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.totalResults()).isEqualTo(41);
        assertThat(page.results()).hasSize(2);
        TmdbMovieSummary first = page.results().get(0);
        assertThat(first.id()).isEqualTo(900001L);
        assertThat(first.title()).isEqualTo("테스트 영화 1");
        assertThat(first.originalTitle()).isEqualTo("테스트 원제 1");
        assertThat(first.releaseDate()).isEqualTo("2024-05-01");
        assertThat(first.genreIds()).containsExactly(18, 53);
        assertThat(first.popularity()).isEqualTo(87.5);
        wireMock.verify(1, getRequestedFor(urlPathEqualTo(DISCOVER_MOVIE_PATH))
                .withoutQueryParam("api_key"));   // 토큰을 쿼리로 보내지 않는다 (Task 004: api_key 쿼리는 401)
    }

    // Task 107: 인기 순위 스냅샷용 discover. 후보 수집과 같은 경로에 sort_by=popularity.desc가 붙는다
    @Test
    void 인기순_discover는_영화와_드라마_모두_sort_by를_보내고_DTO로_매핑된다() {
        wireMock.stubFor(get(urlPathEqualTo(DISCOVER_MOVIE_PATH))
                .withQueryParam("with_watch_providers", equalTo("8|1796"))
                .withQueryParam("watch_region", equalTo("KR"))
                .withQueryParam("with_watch_monetization_types", equalTo("flatrate"))
                .withQueryParam("language", equalTo("ko-KR"))
                .withQueryParam("sort_by", equalTo("popularity.desc"))
                .withQueryParam("page", equalTo("1"))
                .willReturn(okJson("discover-movie.json")));
        wireMock.stubFor(get(urlPathEqualTo("/3/discover/tv"))
                .withQueryParam("with_watch_providers", equalTo("8|1796"))
                .withQueryParam("sort_by", equalTo("popularity.desc"))
                .willReturn(okJson("discover-tv.json")));

        TmdbPage<TmdbMovieSummary> movies = tmdbClient.discoverPopularMovies("8|1796", 1);
        TmdbPage<TmdbTvSummary> tvs = tmdbClient.discoverPopularTvs("8|1796", 1);

        assertThat(movies.results().get(0).popularity()).isEqualTo(87.5);
        assertThat(tvs.results().get(0).name()).isEqualTo("테스트 드라마 1");
        assertThat(callCount("discover_popular_movie", "success")).isEqualTo(1.0);
        assertThat(callCount("discover_popular_tv", "success")).isEqualTo(1.0);
    }

    // T2: 나머지 호출을 하나씩. 경로와 쿼리 이름이 맞는지 스텁을 엄격하게 쓴다
    @Test
    void t2_discoverTvs_정상_응답이_DTO로_매핑된다() {
        wireMock.stubFor(get(urlPathEqualTo("/3/discover/tv"))
                .withQueryParam("with_watch_providers", equalTo("8|337"))
                .withQueryParam("watch_region", equalTo("KR"))
                .withQueryParam("with_watch_monetization_types", equalTo("flatrate"))
                .withQueryParam("language", equalTo("ko-KR"))
                .withQueryParam("page", equalTo("2"))
                .willReturn(okJson("discover-tv.json")));

        TmdbPage<TmdbTvSummary> page = tmdbClient.discoverTvs("8|337", 2);

        assertThat(page.results()).hasSize(2);
        assertThat(page.results().get(0).name()).isEqualTo("테스트 드라마 1");
        assertThat(page.results().get(0).originCountry()).containsExactly("KR");
        assertThat(page.results().get(1).firstAirDate()).isEmpty();   // 빈 문자열이 그대로 온다
    }

    @Test
    void t2_searchMovies_정상_응답이_DTO로_매핑된다() {
        wireMock.stubFor(get(urlPathEqualTo("/3/search/movie"))
                .withQueryParam("query", equalTo("테스트"))
                .withQueryParam("language", equalTo("ko-KR"))
                .withQueryParam("page", equalTo("1"))
                .withQueryParam("include_adult", equalTo("false"))
                .willReturn(okJson("discover-movie.json")));

        TmdbPage<TmdbMovieSummary> page = tmdbClient.searchMovies("테스트", 1);

        assertThat(page.results()).hasSize(2);
    }

    @Test
    void t2_searchTvs_정상_응답이_DTO로_매핑되고_연도_파라미터는_빠진다() {
        wireMock.stubFor(get(urlPathEqualTo("/3/search/tv"))
                .withQueryParam("query", equalTo("테스트"))
                .withQueryParam("language", equalTo("ko-KR"))
                .withQueryParam("page", equalTo("1"))
                .willReturn(okJson("discover-tv.json")));

        TmdbPage<TmdbTvSummary> page = tmdbClient.searchTvs("테스트", 1);

        assertThat(page.results()).hasSize(2);
        // null인 @RequestParam은 쿼리에서 빠진다 (확인 필요였던 항목)
        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/3/search/tv")).withoutQueryParam("first_air_date_year"));
    }

    @Test
    void t2_getMovie_상세와_출연진이_매핑된다() {
        wireMock.stubFor(get(urlPathEqualTo("/3/movie/900001"))
                .withQueryParam("language", equalTo("ko-KR"))
                .withQueryParam("append_to_response", equalTo("credits"))
                .willReturn(okJson("movie-detail.json")));

        TmdbMovieDetail movie = tmdbClient.getMovie(900001L, "ko-KR", "credits");

        assertThat(movie.id()).isEqualTo(900001L);
        assertThat(movie.runtime()).isEqualTo(118);
        assertThat(movie.genres()).extracting("name").containsExactly("드라마", "스릴러");
        assertThat(movie.credits().cast()).hasSize(2);
        assertThat(movie.credits().cast().get(0).name()).isEqualTo("테스트 배우 1");
        assertThat(movie.credits().cast().get(1).profilePath()).isNull();
    }

    @Test
    void t2_getMovie_appendToResponse가_null이면_쿼리에서_빠진다() {
        wireMock.stubFor(get(urlPathEqualTo("/3/movie/900001")).willReturn(okJson("movie-detail.json")));

        tmdbClient.getMovie(900001L, "en-US", null);

        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/3/movie/900001"))
                .withQueryParam("language", equalTo("en-US"))
                .withoutQueryParam("append_to_response"));
    }

    @Test
    void t2_getTv_상세와_시즌_목록이_매핑된다() {
        wireMock.stubFor(get(urlPathEqualTo("/3/tv/800001"))
                .withQueryParam("language", equalTo("ko-KR"))
                .withQueryParam("append_to_response", equalTo("credits"))
                .willReturn(okJson("tv-detail.json")));

        TmdbTvDetail tv = tmdbClient.getTv(800001L, "ko-KR", "credits");

        assertThat(tv.name()).isEqualTo("테스트 드라마 1");
        assertThat(tv.numberOfSeasons()).isEqualTo(2);
        assertThat(tv.episodeRunTime()).containsExactly(60);
        assertThat(tv.seasons()).extracting("seasonNumber").containsExactly(0, 1, 2);   // 시즌 0도 그대로 온다(거르는 건 042)
        assertThat(tv.seasons().get(1).id()).isEqualTo(810001L);
        assertThat(tv.credits().cast()).hasSize(1);
    }

    @Test
    void t2_getTvSeason_회차와_null_러닝타임이_매핑된다() {
        wireMock.stubFor(get(urlPathEqualTo("/3/tv/800001/season/1"))
                .withQueryParam("language", equalTo("ko-KR"))
                .willReturn(okJson("tv-season.json")));

        TmdbTvSeasonDetail season = tmdbClient.getTvSeason(800001L, 1, "ko-KR");

        assertThat(season.seasonNumber()).isEqualTo(1);
        assertThat(season.episodes()).hasSize(4);
        assertThat(season.episodes().get(0).runtime()).isEqualTo(62);
        assertThat(season.episodes().get(2).runtime()).isNull();
        assertThat(season.episodes().get(3).airDate()).isEmpty();
    }

    @Test
    void t2_getMovieWatchProviders_나라별_제공처_맵이_매핑된다() {
        wireMock.stubFor(get(urlPathEqualTo("/3/movie/900001/watch/providers"))
                .willReturn(okJson("watch-providers.json")));

        TmdbWatchProvidersResponse response = tmdbClient.getMovieWatchProviders(900001L);

        TmdbRegionProviders kr = response.results().get("KR");
        assertThat(kr.flatrate()).extracting("providerId").containsExactly(8, 337);
        assertThat(kr.rent()).hasSize(1);
        assertThat(kr.buy()).isNull();   // 키가 없으면 null (소비 쪽이 빈 목록으로 본다)
        assertThat(response.results()).containsKey("US");
    }

    @Test
    void t2_getTvSeasonWatchProviders_정상_응답이_매핑된다() {
        wireMock.stubFor(get(urlPathEqualTo("/3/tv/800001/season/1/watch/providers"))
                .willReturn(okJson("watch-providers.json")));

        TmdbWatchProvidersResponse response = tmdbClient.getTvSeasonWatchProviders(800001L, 1);

        assertThat(response.results().get("KR").flatrate()).hasSize(2);
    }

    // T19: 제공처 전체 목록은 7곳 밖 제공처도 거르지 않고 전부 돌려준다 (거르는 건 Task 043)
    @Test
    void t19_listProviders_영화와_드라마_모두_거르지_않고_전부_돌려준다() {
        for (String type : new String[] {"movie", "tv"}) {
            wireMock.stubFor(get(urlPathEqualTo("/3/watch/providers/" + type))
                    .withQueryParam("watch_region", equalTo("KR"))
                    .withQueryParam("language", equalTo("ko-KR"))
                    .willReturn(okJson("provider-list.json")));
        }

        TmdbProviderListResponse movie = tmdbClient.listProviders(TmdbMediaType.MOVIE);
        TmdbProviderListResponse tv = tmdbClient.listProviders(TmdbMediaType.TV);

        assertThat(movie.results()).extracting("providerId").containsExactly(8, 337, 9999);   // 9999는 7곳 밖
        assertThat(tv.results()).hasSize(3);
        assertThat(movie.results().get(0).displayPriorities()).containsEntry("KR", 3);
    }

    // T3: 쓰지 않는 필드·빈 날짜·null이 있어도 매핑이 실패하지 않는다
    @Test
    void t3_모르는_필드와_빈_날짜와_null이_있어도_매핑에_성공한다() {
        wireMock.stubFor(get(urlPathEqualTo("/3/movie/1")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"id": 1, "title": "x", "overview": null, "release_date": "", "runtime": null,
                         "genres": [], "credits": null,
                         "unknown_object": {"a": 1}, "unknown_list": [1, 2, 3]}
                        """)));

        TmdbMovieDetail movie = tmdbClient.getMovie(1L, "ko-KR", null);

        assertThat(movie.id()).isEqualTo(1L);
        assertThat(movie.overview()).isNull();
        assertThat(movie.releaseDate()).isEmpty();
        assertThat(movie.runtime()).isNull();
        assertThat(movie.credits()).isNull();
    }

    // T3 보강: discover 샘플의 두 번째 항목(빈 날짜, null 포스터, 쓰지 않는 필드 adult·video·vote_count)
    @Test
    void t3_discover_샘플의_빈_날짜와_null_포스터도_매핑된다() {
        wireMock.stubFor(get(urlPathEqualTo(DISCOVER_MOVIE_PATH)).willReturn(okJson("discover-movie.json")));

        TmdbMovieSummary second = tmdbClient.discoverMovies("8", 1).results().get(1);

        assertThat(second.releaseDate()).isEmpty();
        assertThat(second.posterPath()).isNull();
        assertThat(second.overview()).isEmpty();
    }

    // T10: 별도 오버라이드 없이 본 yml의 타임아웃이 이 클라이언트에 연결돼 있다 (Task 024 설정 확인)
    @Test
    void t10_본_yml의_Feign_타임아웃_설정값이_그대로_보인다() {
        assertThat(environment.getProperty("spring.cloud.openfeign.client.config.tmdb.readTimeout")).isEqualTo("5000");
        assertThat(environment.getProperty("spring.cloud.openfeign.client.config.tmdb.connectTimeout")).isEqualTo("3000");
    }

    // 5.9의 3번: 테스트는 실제 TMDB가 아니라 WireMock을 본다
    @Test
    void base_url이_WireMock_주소로_바뀌어_있다() {
        assertThat(props.baseUrl()).startsWith("http://localhost");
    }

    // T16-e: capacity + 1초 동안 채워지는 토큰이 TMDB 한도(약 40)보다 작다 (D1 확정: 10 + 25 = 35)
    @Test
    void t16_설정된_버킷의_순간_최대_호출_수가_40_미만이다() {
        TmdbProperties.RateLimit limit = props.rateLimit();
        double refillPerSecond = limit.refillTokens() * 1000.0 / limit.refillPeriod().toMillis();

        assertThat(limit.capacity() + refillPerSecond).isLessThan(40.0);
    }

}
