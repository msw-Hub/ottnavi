package com.ottnavi.infra.tmdb;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.ottnavi.infra.tmdb.dto.TmdbMovieSummary;
import com.ottnavi.infra.tmdb.dto.TmdbPage;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ottnavi.global.error.ErrorCode;
import com.ottnavi.infra.tmdb.exception.TmdbUnavailableException;


class TmdbRetryTest extends TmdbTestSupport {

    // T4: 429(Retry-After 없음) → 200. 지수 백오프로 재시도해 성공하고 요청은 총 2회
    @Test
    void t4_429_후_재시도로_성공한다() {
        stubFailThenOk(429, null);

        TmdbPage<TmdbMovieSummary> page = tmdbClient.discoverMovies("8", 1);

        assertThat(page.results()).hasSize(2);
        wireMock.verify(2, getRequestedFor(urlPathEqualTo(DISCOVER_MOVIE_PATH)));
    }

    // T5: 429 + Retry-After: 1 → 서버가 알려 준 시각까지 기다린 뒤 재시도. 소요 시간이 약 1초 이상 (여유 있게 800ms)
    @Test
    void t5_Retry_After_1이면_약_1초_기다린_뒤_성공한다() {
        stubFailThenOk(429, "1");

        long start = System.nanoTime();
        TmdbPage<TmdbMovieSummary> page = tmdbClient.discoverMovies("8", 1);
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertThat(page.results()).hasSize(2);
        assertThat(elapsedMs).isGreaterThanOrEqualTo(800);
        wireMock.verify(2, getRequestedFor(urlPathEqualTo(DISCOVER_MOVIE_PATH)));
    }

    // T14: 인증 헤더가 시도마다 정확히 한 번(값 하나)만 붙는다
    @Test
    void t14_인증_헤더가_시도마다_한_번만_붙는다() {
        stubFailThenOk(429, null);

        tmdbClient.discoverMovies("8", 1);

        List<LoggedRequest> requests = wireMock.findAll(getRequestedFor(urlPathEqualTo(DISCOVER_MOVIE_PATH)));
        assertThat(requests).hasSize(2);
        for (LoggedRequest request : requests) {
            assertThat(request.getHeaders().getHeader("Authorization").values())
                    .containsExactly("Bearer " + TOKEN);
        }
    }

    // T6: 429가 계속되면 정확히 max-attempts번 요청한 뒤 포기한다. 502(EXTERNAL_API_ERROR)이고 RATE_LIMIT_EXCEEDED(429)가 아니다 (8절 6번)
    @Test
    void t6_429가_계속되면_max_attempts번_요청_후_TmdbUnavailableException으로_포기한다() {
        wireMock.stubFor(get(urlPathEqualTo(DISCOVER_MOVIE_PATH)).willReturn(errorResponse(429)));

        assertThatThrownBy(() -> tmdbClient.discoverMovies("8", 1))
                .isInstanceOfSatisfying(TmdbUnavailableException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
                    assertThat(e.getErrorCode().getStatus().value()).isEqualTo(502);
                    assertThat(e.getErrorCode()).isNotEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);
                });

        wireMock.verify(props.retry().maxAttempts(), getRequestedFor(urlPathEqualTo(DISCOVER_MOVIE_PATH)));
        assertThat(callCount("discover_movie", "rate_limited")).isEqualTo(1.0);
    }

    // T7: Retry-After가 max-retry-after(5초)보다 크면 기다리지 않고 요청 1회로 포기한다
    @Test
    void t7_Retry_After가_상한보다_크면_요청_1회로_포기한다() {
        wireMock.stubFor(get(urlPathEqualTo(DISCOVER_MOVIE_PATH))
                .willReturn(errorResponse(429).withHeader("Retry-After", "60")));

        assertThatThrownBy(() -> tmdbClient.discoverMovies("8", 1)).isInstanceOf(TmdbUnavailableException.class);

        wireMock.verify(1, getRequestedFor(urlPathEqualTo(DISCOVER_MOVIE_PATH)));
        assertThat(callCount("discover_movie", "rate_limited")).isEqualTo(1.0);
    }

    // T8-a: 503 → 200이면 재시도로 성공한다
    @Test
    void t8_503_후_재시도로_성공한다() {
        stubFailThenOk(503, null);

        TmdbPage<TmdbMovieSummary> page = tmdbClient.discoverMovies("8", 1);

        assertThat(page.results()).hasSize(2);
        wireMock.verify(2, getRequestedFor(urlPathEqualTo(DISCOVER_MOVIE_PATH)));
        assertThat(callCount("discover_movie", "success")).isEqualTo(1.0);
    }

    // T8-b: 500이 계속되면 max-attempts번 요청 후 TmdbUnavailableException
    @Test
    void t8_500이_계속되면_max_attempts번_요청_후_포기한다() {
        wireMock.stubFor(get(urlPathEqualTo(DISCOVER_MOVIE_PATH)).willReturn(errorResponse(500)));

        assertThatThrownBy(() -> tmdbClient.discoverMovies("8", 1)).isInstanceOf(TmdbUnavailableException.class);

        wireMock.verify(props.retry().maxAttempts(), getRequestedFor(urlPathEqualTo(DISCOVER_MOVIE_PATH)));
        assertThat(callCount("discover_movie", "server_error")).isEqualTo(1.0);
    }

}
