package com.ottnavi.infra.tmdb;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.ottnavi.global.config.FeignConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * TMDB 테스트 공통 기반: DB·Redis 없이 Feign·Jackson·TMDB 설정만 올린 가벼운 컨텍스트 + WireMock 서버(실제 TMDB는 부르지 않는다).
 * 앱 전체 대신 필요한 설정만 지정(@SpringBootTest(classes))하므로 DataSource·JPA·Redis 자동 구성이 로드되지 않는다 → Docker 불필요.
 */
@SpringBootTest(classes = TmdbTestSupport.TestApp.class)
abstract class TmdbTestSupport {

    protected static final String TOKEN = "test-token";
    protected static final String DISCOVER_MOVIE_PATH = "/3/discover/movie";

    // JVM에서 한 번만 띄운다. 컨텍스트가 캐시돼 여러 테스트 클래스가 같은 base-url(포트)을 재사용하기 때문이다.
    // 종료는 JVM 종료 훅에 맡긴다. 테스트 사이 상태는 @BeforeEach의 resetAll()이 지운다.
    protected static final WireMockServer wireMock = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        wireMock.start();
        Runtime.getRuntime().addShutdownHook(new Thread(wireMock::stop));
    }

    @DynamicPropertySource
    static void tmdbProperties(DynamicPropertyRegistry registry) {
        registry.add("app.tmdb.base-url", () -> wireMock.baseUrl() + "/3");   // 실제 TMDB 대신 WireMock
        registry.add("app.tmdb.api-token", () -> TOKEN);
        registry.add("app.tmdb.retry.initial-backoff", () -> "10ms");         // 테스트가 느려지지 않게 대기를 줄인다
        registry.add("app.tmdb.retry.max-backoff", () -> "40ms");
    }

    @Autowired
    protected TmdbClient tmdbClient;

    @Autowired
    protected TmdbProperties props;

    @Autowired
    protected MeterRegistry meterRegistry;

    @BeforeEach
    void resetWireMockAndMetrics() {
        wireMock.resetAll();       // 스텁·요청 기록·시나리오 상태를 모두 지운다
        meterRegistry.clear();     // tmdb.call 카운트를 테스트마다 0에서 시작. TmdbClient는 호출 때마다 카운터를 조회하므로 안전하다
    }

    /** 가벼운 컨텍스트 구성. 테스트 소스의 @Configuration은 반드시 테스트 클래스 안의 중첩 static 클래스로 둔다(TECH 6절) */
    @Configuration
    @Import({FeignConfig.class, TmdbConfig.class, TmdbClient.class})
    @ImportAutoConfiguration({
            FeignAutoConfiguration.class,
            JacksonAutoConfiguration.class,
            HttpMessageConvertersAutoConfiguration.class})
    static class TestApp {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    /** src/test/resources/tmdb/ 의 샘플 JSON을 읽는다 */
    protected static String json(String fileName) {
        try {
            return new ClassPathResource("tmdb/" + fileName).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("샘플 JSON을 읽지 못했다: " + fileName, e);
        }
    }

    /** 200 + 샘플 JSON 응답 */
    protected static ResponseDefinitionBuilder okJson(String fileName) {
        return aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(json(fileName));
    }

    /** 오류 상태 + 손으로 만든 간단한 오류 본문(TMDB 오류 본문 구조는 확인하지 못했으니 테스트가 본문 형식에 의존하지 않는다, 5.8절 6번) */
    protected static ResponseDefinitionBuilder errorResponse(int status) {
        return aResponse()
                .withStatus(status)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status_code\":7,\"status_message\":\"가짜 오류 본문\",\"success\":false}");
    }

    /** 같은 경로가 처음엔 status 오류, 그다음엔 discover-movie.json 200을 돌려주게 한다. retryAfter가 null이면 Retry-After 헤더를 안 붙인다 */
    protected static void stubFailThenOk(int status, String retryAfter) {
        String scenario = "fail-then-ok-" + status;
        ResponseDefinitionBuilder failure = errorResponse(status);
        if (retryAfter != null) {
            failure.withHeader("Retry-After", retryAfter);
        }
        wireMock.stubFor(get(urlPathEqualTo(DISCOVER_MOVIE_PATH))
                .inScenario(scenario).whenScenarioStateIs(Scenario.STARTED)
                .willReturn(failure)
                .willSetStateTo("recovered"));
        wireMock.stubFor(get(urlPathEqualTo(DISCOVER_MOVIE_PATH))
                .inScenario(scenario).whenScenarioStateIs("recovered")
                .willReturn(okJson("discover-movie.json")));
    }

    /** tmdb.call{endpoint, result} 카운터의 현재 값. 아직 한 번도 안 셌으면 0 */
    protected double callCount(String endpoint, String result) {
        Counter counter = meterRegistry.find("tmdb.call").tag("endpoint", endpoint).tag("result", result).counter();
        return counter == null ? 0.0 : counter.count();
    }
}
