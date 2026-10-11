package com.ottnavi.infra.tmdb;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ottnavi.infra.tmdb.exception.TmdbUnavailableException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "spring.cloud.openfeign.client.config.tmdb.readTimeout=300")
class TmdbTimeoutTest extends TmdbTestSupport {

    // T9: 응답이 읽기 타임아웃(300ms)보다 늦으면 io-max-attempts번 시도한 뒤 TmdbUnavailableException으로 포기한다
    @Test
    void t9_읽기_타임아웃이면_io_max_attempts번_요청_후_포기한다() {
        wireMock.stubFor(get(urlPathEqualTo(DISCOVER_MOVIE_PATH))
                .willReturn(okJson("discover-movie.json").withFixedDelay(1000)));   // 300ms 타임아웃보다 늦게 응답

        long start = System.nanoTime();
        assertThatThrownBy(() -> tmdbClient.discoverMovies("8", 1)).isInstanceOf(TmdbUnavailableException.class);
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

        wireMock.verify(props.retry().ioMaxAttempts(), getRequestedFor(urlPathEqualTo(DISCOVER_MOVIE_PATH)));
        assertThat(elapsedMs).isLessThan(5000);   // 대략 300ms × 횟수 근처. 정상 yml(5초)로 돌았다면 10초 이상 걸렸을 것
        assertThat(callCount("discover_movie", "io_error")).isEqualTo(1.0);
    }
}
