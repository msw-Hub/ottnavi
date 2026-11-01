package com.ottnavi.infra.analytics;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.ottnavi.infra.analytics.exception.AnalyticsCredentialsException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * GA 클라이언트 테스트 공통 기반: DB·Redis 없이 Feign·Jackson·GA 설정만 올린 가벼운 컨텍스트 + WireMock 서버.
 * 실제 구글(토큰 발급, GA Data API)은 부르지 않는다. 토큰 제공자는 가짜로 바꿔 둔다.
 * 컨텍스트를 테스트마다 직접 만들고 닫는다. @Configuration 클래스를 두지 않는 이유: 테스트 소스의 @Configuration은
 * 다른 통합 테스트의 컴포넌트 스캔에 잡혀 가짜 토큰 제공자가 앱에 섞일 수 있다.
 */
abstract class GaTestSupport {

	protected static final String PROPERTY_ID = "123456789";
	protected static final String RUN_REPORT_PATH = "/v1beta/properties/" + PROPERTY_ID + ":runReport";

	// JVM에서 한 번만 띄운다. 테스트 사이 상태는 @BeforeEach가 지운다
	protected static final WireMockServer wireMock = new WireMockServer(wireMockConfig().dynamicPort());

	static {
		wireMock.start();
		Runtime.getRuntime().addShutdownHook(new Thread(wireMock::stop));
	}

	private AnnotationConfigApplicationContext context;
	protected GaDataClient gaDataClient;
	protected FakeTokenProvider tokenProvider;

	/** 테스트 클래스가 컨텍스트 속성을 더 넣고 싶을 때 덮어쓴다(예: 시간 제한). "키=값" 형태 */
	protected String[] extraProperties() {
		return new String[0];
	}

	@BeforeEach
	void startContext() {
		wireMock.resetAll();
		tokenProvider = new FakeTokenProvider();
		context = new AnnotationConfigApplicationContext();
		TestPropertyValues.of(
				"app.analytics.base-url=" + wireMock.baseUrl(),   // 실제 GA 대신 WireMock
				"app.analytics.property-id=" + PROPERTY_ID,
				"app.analytics.credentials-base64=dGVzdA==").applyTo(context);   // 가짜 토큰 제공자를 쓰므로 읽지 않는다
		TestPropertyValues.of(extraProperties()).applyTo(context);
		context.register(GaTestContext.class);
		context.registerBean(GaAccessTokenProvider.class, () -> tokenProvider);
		context.refresh();
		gaDataClient = context.getBean(GaDataClient.class);
	}

	@AfterEach
	void closeContext() {
		context.close();
	}

	/** 가짜 토큰 제공자: 구글을 부르지 않고 고정 토큰을 돌려주거나 실패를 흉내 낸다 */
	static class FakeTokenProvider implements GaAccessTokenProvider {
		static final String TOKEN = "fake-ga-token";
		volatile boolean failing;

		@Override
		public String getAccessToken() {
			if (failing) {
				throw new AnalyticsCredentialsException("가짜 토큰 발급 실패");
			}
			return TOKEN;
		}
	}

	/** 가벼운 컨텍스트 구성(@Configuration이 아니라 직접 register한다) */
	@EnableFeignClients(clients = GaDataApi.class)
	@Import({AnalyticsConfig.class, GaDataClient.class})
	@ImportAutoConfiguration({
			FeignAutoConfiguration.class,
			JacksonAutoConfiguration.class,
			HttpMessageConvertersAutoConfiguration.class})
	static class GaTestContext {
	}
}
