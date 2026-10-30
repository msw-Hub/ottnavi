package com.ottnavi.global.config;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.LoggerContext;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

/**
 * 실제 logback-spring.xml을 Boot의 LoggingSystem으로 읽어 Loki 로그 전송을 검증한다.
 * 일반 JoranConfigurator로는 springProfile 요소를 읽지 못해, 아무 빈도 없는 최소 Boot 앱을 띄워 Boot가 설정을 초기화하게 했다.
 * Grafana 대신 WireMock이 /loki/api/v1/push를 받는다. DB·Redis가 없어 Docker가 필요 없다.
 *
 * 로그는 5초 배치로 나가므로, 로그를 찍은 뒤 logback 컨텍스트를 멈춰(종료 시 남은 로그 비우기 설정) 전송을 끝내고 가짜 서버가 받은 요청을 본다.
 */
class LokiAppenderIntegrationTest {

	private static final String PUSH_PATH = "/loki/api/v1/push";
	private static final String FAKE_USER = "fake-user";
	private static final String FAKE_TOKEN = "fake-test-value";   // 명백히 가짜인 테스트용 값

	private WireMockServer wireMock;
	private ConfigurableApplicationContext app;
	private PrintStream originalOut;
	private ByteArrayOutputStream console;

	@Configuration
	static class EmptyApp {
	}

	@BeforeEach
	void setUp() {
		wireMock = new WireMockServer(wireMockConfig().dynamicPort());
		wireMock.start();
		wireMock.stubFor(post(urlPathEqualTo(PUSH_PATH)).willReturn(aResponse().withStatus(204)));

		// logback은 환경 변수·시스템 속성에서 ${GRAFANA_LOKI_*}를 읽는다. 운영의 환경 변수 대신 시스템 속성으로 넣는다
		System.setProperty("GRAFANA_LOKI_URL", wireMock.baseUrl() + PUSH_PATH);
		System.setProperty("GRAFANA_LOKI_USER", FAKE_USER);
		System.setProperty("GRAFANA_LOKI_TOKEN", FAKE_TOKEN);

		// 콘솔 출력을 붙잡는다(ConsoleAppender는 쓸 때마다 System.out을 본다)
		originalOut = System.out;
		console = new ByteArrayOutputStream();
		System.setOut(new PrintStream(console, true));
	}

	@AfterEach
	void tearDown() {
		stopLogging();
		System.setOut(originalOut);
		System.clearProperty("GRAFANA_LOKI_URL");
		System.clearProperty("GRAFANA_LOKI_USER");
		System.clearProperty("GRAFANA_LOKI_TOKEN");
		wireMock.stop();
	}

	/** Boot가 logback-spring.xml을 읽게 하는 최소 앱을 띄운다. withProfile이 true면 observability 프로필을 켠다. */
	private void startApp(boolean withProfile) {
		SpringApplicationBuilder builder = new SpringApplicationBuilder(EmptyApp.class)
				.web(WebApplicationType.NONE)
				.properties("spring.main.banner-mode=off",
						"app.observability.loki-batch-timeout-ms=200");   // 시험이 5초씩 기다리지 않게 배치 대기 시간을 줄인다
		if (withProfile) {
			builder.profiles("observability");
		}
		app = builder.run();
	}

	/** 앱과 logback 컨텍스트를 멈춘다. 컨텍스트를 멈추면 Loki 어펜더가 남은 로그를 모두 보낸 뒤 끝난다. */
	private void stopLogging() {
		if (app != null) {
			app.close();
			app = null;
		}
		LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
		loggerContext.stop();
	}

	private List<LoggedRequest> pushRequests() {
		return wireMock.findAll(postRequestedFor(urlPathEqualTo(PUSH_PATH)));
	}

	/** 받은 모든 전송 요청의 본문을 이어 붙인다(gzip으로 압축돼 있으면 풀어서). */
	private String allBodies() throws Exception {
		StringBuilder all = new StringBuilder();
		for (LoggedRequest request : pushRequests()) {
			byte[] body = request.getBody();
			if ("gzip".equalsIgnoreCase(request.getHeader("Content-Encoding"))) {
				body = new GZIPInputStream(new ByteArrayInputStream(body)).readAllBytes();
			}
			all.append(new String(body, StandardCharsets.UTF_8));
		}
		return all.toString();
	}

	/**
	 * 끝 표식 로그를 하나 더 찍고, 그 로그가 가짜 서버에 도착할 때까지 기다린다.
	 * 표식은 시험 로그보다 나중에 들어가고 전송은 순서대로라, 표식이 도착했으면 앞서 찍은 로그의 전송도 끝난 것이다.
	 * "전송되지 않았다"를 확인하는 시험도 이 방식으로 고정된 시간을 기다리지 않고 판단한다.
	 */
	private void awaitSentinel() throws Exception {
		String sentinel = "sentinel-끝표식";
		LoggerFactory.getLogger("com.ottnavi.test.Sentinel").info(sentinel);
		long deadline = System.currentTimeMillis() + 10_000;
		while (System.currentTimeMillis() < deadline) {
			if (allBodies().contains(sentinel)) {
				return;
			}
			Thread.sleep(50);
		}
		throw new AssertionError("끝 표식 로그가 10초 안에 가짜 Loki에 도착하지 않았다");
	}

	private String consoleText() {
		// Boot 콘솔 어펜더는 file.encoding(Windows 기본 cp949)으로 출력하므로 같은 문자셋으로 읽는다
		return console.toString(Charset.defaultCharset());
	}

	@Test
	void 프로필을_켜면_가짜_Loki로_전송_요청이_오고_Basic_인증_헤더가_붙는다() throws Exception {
		// given
		startApp(true);
		Logger log = LoggerFactory.getLogger("com.ottnavi.test.Probe");

		// when
		log.info("전송 확인용 로그");
		awaitSentinel();

		// then
		List<LoggedRequest> requests = pushRequests();
		String expected = "Basic " + Base64.getEncoder()
				.encodeToString((FAKE_USER + ":" + FAKE_TOKEN).getBytes(StandardCharsets.UTF_8));
		assertThat(requests).isNotEmpty();
		assertThat(requests.get(0).getHeader("Authorization")).isEqualTo(expected);
	}

	@Test
	void 프로필을_끄면_전송_요청이_한_건도_가지_않는다() {
		// given
		startApp(false);
		Logger log = LoggerFactory.getLogger("com.ottnavi.test.Probe");

		// when
		log.info("전송되면 안 되는 로그");
		log.error("전송되면 안 되는 오류 로그");
		stopLogging();

		// then
		assertThat(pushRequests()).isEmpty();
	}

	@Test
	void 프로필을_꺼도_콘솔에는_Boot_기본_형식으로_출력된다() {
		// given
		startApp(false);
		Logger log = LoggerFactory.getLogger("com.ottnavi.test.Probe");

		// when
		log.info("콘솔 형식 확인용 로그");
		stopLogging();

		// then
		// Boot 기본 형식: 2026-10-30T20:00:00.000+09:00  INFO 1234 --- [main] c.o.global.config.XXX : 메시지
		assertThat(consoleText()).containsPattern(
				"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}[+-Z].*\\sINFO\\s+\\d+ --- .*com\\.ottnavi\\.test\\.Probe\\s+: 콘솔 형식 확인용 로그");
	}

	@Test
	void 프로필을_켜도_콘솔에는_이메일과_스택_트레이스가_그대로_남는다() {
		// given
		startApp(true);
		Logger log = LoggerFactory.getLogger("com.ottnavi.test.Probe");

		// when
		log.error("콘솔 확인 hong@example.com", new IllegalStateException("예외 문장입니다"));
		stopLogging();

		// then
		String text = consoleText();
		assertThat(text).contains("hong@example.com");
		assertThat(text).contains("예외 문장입니다");
		assertThat(text).contains("at com.ottnavi.global.config.LokiAppenderIntegrationTest");
	}

	@Test
	void 전송_본문에서_이메일_모양_문자열이_가려진다() throws Exception {
		// given
		startApp(true);
		Logger log = LoggerFactory.getLogger("com.ottnavi.test.Probe");

		// when
		log.warn("발송 실패: hong@example.com 에게 보내지 못했다");
		awaitSentinel();

		// then
		String body = allBodies();
		assertThat(body).contains("발송 실패: *** 에게 보내지 못했다");
		assertThat(body).doesNotContain("hong@example.com");
	}

	@Test
	void 예외가_붙은_로그는_근본원인_클래스_이름만_가고_예외_메시지는_가지_않는다() throws Exception {
		// given
		startApp(true);
		Logger log = LoggerFactory.getLogger("com.ottnavi.test.Probe");
		Throwable cause = new java.net.SocketTimeoutException("내부 접속 정보가 든 예외 문장");
		Throwable wrapped = new RuntimeException("바깥 예외 문장 hong@example.com", cause);

		// when
		log.error("메일 발송 실패", wrapped);
		awaitSentinel();

		// then
		String body = allBodies();
		assertThat(body).contains("메일 발송 실패 rootCause=java.net.SocketTimeoutException");
		assertThat(body).doesNotContain("예외 문장");
		assertThat(body).doesNotContain("hong@example.com");
	}

	@Test
	void 예외가_붙은_로그의_전송_본문에는_스택_줄이_없다() throws Exception {
		// given
		startApp(true);
		Logger log = LoggerFactory.getLogger("com.ottnavi.test.Probe");

		// when
		log.error("스택 확인", new IllegalStateException("x"));
		awaitSentinel();

		// then
		String body = allBodies();
		assertThat(body).contains("스택 확인");
		assertThat(body).doesNotContain("at com.ottnavi");
		assertThat(body).doesNotContain("LokiAppenderIntegrationTest");
	}

	@Test
	void 라벨은_app_env_level_3개만_가고_구조화_메타데이터는_가지_않는다() throws Exception {
		// given
		startApp(true);
		Logger log = LoggerFactory.getLogger("com.ottnavi.test.Probe");

		// when
		log.info("라벨 확인");
		awaitSentinel();

		// then
		String body = allBodies();
		assertThat(body).contains("\"stream\":{\"app\":\"ottnavi\",\"env\":\"local\",\"level\":\"INFO\"}");
		assertThat(body).doesNotContain("thread=");
		assertThat(body).doesNotContain("\"logger\"");
	}

	@Test
	void 우리_코드의_INFO_로그는_전송된다() throws Exception {
		// given
		startApp(true);
		Logger log = LoggerFactory.getLogger("com.ottnavi.plan.Probe");

		// when
		log.info("우리코드-info-표식");
		awaitSentinel();

		// then
		assertThat(allBodies()).contains("우리코드-info-표식");
	}

	@Test
	void 라이브러리의_INFO_로그는_전송되지_않는다() throws Exception {
		// given
		startApp(true);
		Logger log = LoggerFactory.getLogger("org.fake.library.Probe");

		// when
		log.info("라이브러리-info-표식");
		awaitSentinel();

		// then
		assertThat(allBodies()).doesNotContain("라이브러리-info-표식");
	}

	@Test
	void 라이브러리의_WARN_로그는_전송된다() throws Exception {
		// given
		startApp(true);
		Logger log = LoggerFactory.getLogger("org.fake.library.Probe");

		// when
		log.warn("라이브러리-warn-표식");
		awaitSentinel();

		// then
		assertThat(allBodies()).contains("라이브러리-warn-표식");
	}

	@Test
	void 로그_전송_라이브러리_자신의_로그는_전송되지_않는다() throws Exception {
		// given
		startApp(true);
		Logger log = LoggerFactory.getLogger("com.github.loki4j.fake.Probe");

		// when
		log.error("loki4j-자체로그-표식");
		awaitSentinel();

		// then
		assertThat(allBodies()).doesNotContain("loki4j-자체로그-표식");
	}

	@Test
	void Loki가_느려도_로그를_찍는_스레드는_막히지_않는다() {
		// given
		wireMock.stubFor(post(urlPathEqualTo(PUSH_PATH)).willReturn(aResponse().withStatus(204).withFixedDelay(3000)));
		startApp(true);
		Logger log = LoggerFactory.getLogger("com.ottnavi.test.Probe");

		// when
		long start = System.nanoTime();
		for (int i = 0; i < 300; i++) {
			log.info("느린 Loki 시험 로그 {}", i);
		}
		long elapsedMs = (System.nanoTime() - start) / 1_000_000;

		// then
		assertThat(elapsedMs).isLessThan(1000);
	}

	@Test
	void Loki가_500을_돌려줘도_로그를_찍는_스레드는_막히지_않고_예외도_나오지_않는다() throws Exception {
		// given
		wireMock.stubFor(post(urlPathEqualTo(PUSH_PATH)).willReturn(aResponse().withStatus(500)));
		startApp(true);
		Logger log = LoggerFactory.getLogger("com.ottnavi.test.Probe");

		// when
		long start = System.nanoTime();
		for (int i = 0; i < 300; i++) {
			log.warn("오류 응답 시험 로그 {}", i);
		}
		long elapsedMs = (System.nanoTime() - start) / 1_000_000;
		awaitSentinel();
		stopLogging();   // 종료 중에도 예외가 밖으로 나오지 않아야 한다

		// then
		assertThat(elapsedMs).isLessThan(1000);
		assertThat(pushRequests()).isNotEmpty();
	}
}
