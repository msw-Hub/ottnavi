package com.ottnavi.infra.tmdb;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ottnavi.infra.tmdb.exception.TmdbNotFoundException;
import com.ottnavi.infra.tmdb.exception.TmdbUnavailableException;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class TmdbMetricsTest extends TmdbTestSupport {

    @Test
    void t17_성공하면_tmdb_call_success가_1_증가한다() {
        wireMock.stubFor(get(urlPathEqualTo(DISCOVER_MOVIE_PATH)).willReturn(okJson("discover-movie.json")));

        tmdbClient.discoverMovies("8", 1);

        assertThat(callCount("discover_movie", "success")).isEqualTo(1.0);
    }

    @Test
    void t17_404는_not_found로_센다() {
        wireMock.stubFor(get(urlPathEqualTo("/3/tv/1")).willReturn(errorResponse(404)));

        assertThatThrownBy(() -> tmdbClient.getTv(1L, "ko-KR", null)).isInstanceOf(TmdbNotFoundException.class);

        assertThat(callCount("tv_detail", "not_found")).isEqualTo(1.0);
        assertThat(callCount("tv_detail", "success")).isZero();
    }

    @Test
    void t17_429_포기는_rate_limited_5xx_포기는_server_error로_센다() {
        wireMock.stubFor(get(urlPathEqualTo("/3/tv/1/season/1")).willReturn(errorResponse(429)));
        wireMock.stubFor(get(urlPathEqualTo("/3/tv/2/season/1")).willReturn(errorResponse(503)));

        assertThatThrownBy(() -> tmdbClient.getTvSeason(1L, 1, "ko-KR")).isInstanceOf(TmdbUnavailableException.class);
        assertThatThrownBy(() -> tmdbClient.getTvSeason(2L, 1, "ko-KR")).isInstanceOf(TmdbUnavailableException.class);

        assertThat(callCount("tv_season", "rate_limited")).isEqualTo(1.0);
        assertThat(callCount("tv_season", "server_error")).isEqualTo(1.0);
    }

    // 태그 값이 닫힌 집합인지: 작품 ID·URL 같은 값이 태그로 새지 않는다
    @Test
    void t17_tmdb_call의_태그는_endpoint와_result_두_개뿐이다() {
        wireMock.stubFor(get(urlPathEqualTo("/3/movie/900001")).willReturn(okJson("movie-detail.json")));
        wireMock.stubFor(get(urlPathEqualTo("/3/movie/900002")).willReturn(okJson("movie-detail.json")));

        tmdbClient.getMovie(900001L, "ko-KR", null);
        tmdbClient.getMovie(900002L, "ko-KR", null);

        assertThat(meterRegistry.find("tmdb.call").meters())
                .hasSize(1);   // 작품 ID가 달라도 시리즈는 늘지 않는다
        Set<String> tagKeys = meterRegistry.find("tmdb.call").meters().stream()
                .map(Meter::getId)
                .flatMap(id -> id.getTags().stream())
                .map(Tag::getKey)
                .collect(Collectors.toSet());
        assertThat(tagKeys).containsExactlyInAnyOrder("endpoint", "result");
    }

    // 로컬 한도 초과(local_limit)는 WireMock으로 만들기 어려워 TmdbApi를 목으로 바꿔 단위로 검사한다
    @Test
    void t17_호출_전_로컬_한도_예외는_local_limit으로_센다() {
        TmdbApi api = mock(TmdbApi.class);
        when(api.discoverMovies(any(), any(), any(), any(), anyInt()))
                .thenThrow(new TmdbUnavailableException("테스트용 로컬 한도 초과"));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TmdbClient client = new TmdbClient(api, registry);

        assertThatThrownBy(() -> client.discoverMovies("8", 1)).isInstanceOf(TmdbUnavailableException.class);

        assertThat(registry.get("tmdb.call").tag("endpoint", "discover_movie").tag("result", "local_limit")
                .counter().count()).isEqualTo(1.0);
    }
}
