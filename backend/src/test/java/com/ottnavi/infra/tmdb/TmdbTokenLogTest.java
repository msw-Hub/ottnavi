package com.ottnavi.infra.tmdb;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ottnavi.infra.tmdb.exception.TmdbUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = "logging.level.com.ottnavi.infra.tmdb=DEBUG")   // loggerLevel: basic이어도 Feign 로그는 해당 로거가 DEBUG여야 나온다
class TmdbTokenLogTest extends TmdbTestSupport {

    // T15: 재시도 성공(T4) 흐름과 포기(T6) 흐름의 로그에 토큰이 없다
    @Test
    void t15_DEBUG_로그에도_토큰이_나오지_않는다(CapturedOutput output) {
        stubFailThenOk(429, null);
        tmdbClient.discoverMovies("8", 1);

        wireMock.stubFor(get(urlPathEqualTo("/3/movie/1")).willReturn(errorResponse(429)));
        assertThatThrownBy(() -> tmdbClient.getMovie(1L, "ko-KR", null)).isInstanceOf(TmdbUnavailableException.class);

        assertThat(output.getAll()).contains("TmdbApi#discoverMovies");   // 요청 로그가 실제로 켜졌다는 증거. 이게 없으면 아래 검사는 의미가 없다
        assertThat(output.getAll()).doesNotContain(TOKEN);
    }
}
