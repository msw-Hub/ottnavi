package com.ottnavi.infra.tmdb;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.ottnavi.infra.tmdb.dto.TmdbMovieSummary;
import com.ottnavi.infra.tmdb.dto.TmdbPage;
import com.ottnavi.support.IntegrationTestSupport;
import feign.RequestInterceptor;
import feign.Retryer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * T20: 앱 전체 컨텍스트에서 TMDB 연동이 조립되는지 본다(Docker 필요).
 * 확인 항목: ① TMDB_API_KEY 없이 기동 ② 설정 바인딩과 빈 조립, TmdbFeignConfig가 앱 전체로 퍼지지 않음 ③ 본 yml 타임아웃 ④ 실제 앱 Jackson 설정으로 DTO 매핑.
 * 실제 TMDB는 부르지 않는다: base-url만 WireMock으로 돌린다.
 */
class TmdbClientIntegrationTest extends IntegrationTestSupport {

    // 이 클래스만 쓰는 서버. 이 클래스의 컨텍스트는 다른 테스트와 공유되지 않으므로(base-url이 달라 캐시 키가 다르다) 끝나면 멈춘다
    private static final WireMockServer wireMock = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        wireMock.start();
    }

    @DynamicPropertySource
    static void tmdbProperties(DynamicPropertyRegistry registry) {
        registry.add("app.tmdb.base-url", () -> wireMock.baseUrl() + "/3");
        // api-token은 일부러 건드리지 않는다: 환경 변수 TMDB_API_KEY가 없는 운영과 같은 조건으로 기동되는지 본다
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @Autowired
    private ApplicationContext context;

    @Autowired
    private TmdbClient tmdbClient;

    @Autowired
    private Environment environment;

    @Test
    void t20_토큰_없이_기동되고_TMDB_빈이_조립된다() {
        // 여기까지 왔다면 TMDB_API_KEY 없이 컨텍스트가 떴다는 뜻이다 (기본값 ${TMDB_API_KEY:}, 함정 5)
        assertThat(context.getBeansOfType(TmdbProperties.class)).hasSize(1);
        assertThat(context.getBeansOfType(TmdbApi.class)).hasSize(1);
        assertThat(context.getBeansOfType(TmdbClient.class)).hasSize(1);
        assertThat(context.getBeansOfType(TmdbRateLimiter.class)).hasSize(1);
    }

    @Test
    void t20_TmdbFeignConfig는_앱_전체_컨텍스트로_퍼지지_않는다() {
        // 인증·버킷 인터셉터와 Retryer는 TMDB 전용 Feign 하위 컨텍스트에만 있어야 한다 (함정 3).
        // 확인 필요(실행 시 확인): 이후 다른 Feign 클라이언트나 라이브러리가 이 타입의 빈을 앱 전체에 등록하면 이 단언을 조정한다
        assertThat(context.getBeansOfType(TmdbFeignConfig.class)).isEmpty();
        assertThat(context.getBeansOfType(RequestInterceptor.class)).isEmpty();
        assertThat(context.getBeansOfType(Retryer.class)).isEmpty();
    }

    @Test
    void t20_설정_바인딩과_본_yml_타임아웃이_그대로다() {
        TmdbProperties props = context.getBean(TmdbProperties.class);

        assertThat(props.rateLimit().capacity()).isEqualTo(10);
        assertThat(props.rateLimit().refillTokens()).isEqualTo(25);
        assertThat(props.retry().maxAttempts()).isEqualTo(3);
        assertThat(props.retry().ioMaxAttempts()).isEqualTo(2);
        assertThat(props.toString()).contains("apiToken=***");
        assertThat(environment.getProperty("spring.cloud.openfeign.client.config.tmdb.readTimeout")).isEqualTo("5000");
        assertThat(environment.getProperty("spring.cloud.openfeign.client.config.tmdb.connectTimeout")).isEqualTo("3000");
    }

    @Test
    void t20_대표_호출이_앱의_Jackson_설정으로_DTO에_매핑된다() {
        wireMock.stubFor(get(urlPathEqualTo("/3/discover/movie")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"page": 1, "total_pages": 1, "total_results": 1,
                         "results": [{"id": 900001, "title": "테스트 영화 1", "release_date": "", "adult": false}]}
                        """)));

        TmdbPage<TmdbMovieSummary> page = tmdbClient.discoverMovies("8", 1);

        assertThat(page.results()).hasSize(1);
        assertThat(page.results().get(0).title()).isEqualTo("테스트 영화 1");
        assertThat(page.results().get(0).releaseDate()).isEmpty();
    }
}
