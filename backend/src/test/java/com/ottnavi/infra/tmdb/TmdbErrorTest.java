package com.ottnavi.infra.tmdb;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ottnavi.infra.tmdb.exception.TmdbNotFoundException;
import com.ottnavi.infra.tmdb.exception.TmdbRequestException;
import com.ottnavi.infra.tmdb.exception.TmdbUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TmdbErrorTest extends TmdbTestSupport {

    // T11: 401은 코드·설정 문제라 재시도하지 않는다
    @Test
    void t11_401은_TmdbRequestException이고_한_번만_요청한다() {
        wireMock.stubFor(get(urlPathEqualTo("/3/movie/1")).willReturn(errorResponse(401)));

        assertThatThrownBy(() -> tmdbClient.getMovie(1L, "ko-KR", null)).isInstanceOf(TmdbRequestException.class);

        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/3/movie/1")));
        assertThat(callCount("movie_detail", "client_error")).isEqualTo(1.0);
    }

    // T12: 404는 TmdbNotFoundException (D3). 재시도하지 않는다
    @Test
    void t12_404는_TmdbNotFoundException이고_한_번만_요청한다() {
        wireMock.stubFor(get(urlPathEqualTo("/3/movie/1")).willReturn(errorResponse(404)));

        assertThatThrownBy(() -> tmdbClient.getMovie(1L, "ko-KR", null)).isInstanceOf(TmdbNotFoundException.class);

        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/3/movie/1")));
        assertThat(callCount("movie_detail", "not_found")).isEqualTo(1.0);
    }

    // T13: 그 밖의 4xx(400·422)도 재시도하지 않는다
    @ParameterizedTest
    @ValueSource(ints = {400, 422})
    void t13_그_밖의_4xx는_TmdbRequestException이고_한_번만_요청한다(int status) {
        wireMock.stubFor(get(urlPathEqualTo("/3/movie/1")).willReturn(errorResponse(status)));

        assertThatThrownBy(() -> tmdbClient.getMovie(1L, "ko-KR", null)).isInstanceOf(TmdbRequestException.class);

        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/3/movie/1")));
    }

    // T13 보강: 4xx에 Retry-After가 붙어 있어도 재시도하지 않는다 (ErrorDecoder.Default는 헤더만 보고 감싸므로 TmdbErrorDecoder가 거른다)
    @Test
    void t13_400에_Retry_After가_붙어_있어도_한_번만_요청한다() {
        wireMock.stubFor(get(urlPathEqualTo("/3/movie/1")).willReturn(errorResponse(400).withHeader("Retry-After", "1")));

        assertThatThrownBy(() -> tmdbClient.getMovie(1L, "ko-KR", null)).isInstanceOf(TmdbRequestException.class);

        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/3/movie/1")));
    }

    // T24: 200인데 JSON을 DTO로 못 바꾸면 TmdbUnavailableException(decode_error). 재시도하지 않는다
    @Test
    void t24_응답을_해석하지_못하면_TmdbUnavailableException이고_한_번만_요청한다() {
        wireMock.stubFor(get(urlPathEqualTo(DISCOVER_MOVIE_PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("이건 JSON이 아니다")));

        assertThatThrownBy(() -> tmdbClient.discoverMovies("8", 1)).isInstanceOf(TmdbUnavailableException.class);

        wireMock.verify(1, getRequestedFor(urlPathEqualTo(DISCOVER_MOVIE_PATH)));
        assertThat(callCount("discover_movie", "decode_error")).isEqualTo(1.0);
    }
}
