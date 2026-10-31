package com.ottnavi.infra.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ottnavi.infra.analytics.exception.AnalyticsCredentialsException;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * GA 설정 검사: 둘 다 없음 → 꺼짐, 하나만 있음 → 기동 실패, 둘 다 있음 → 기동 성공(토큰은 호출 때 읽는다).
 * 막으려는 사고: 설정 실수를 켜진 척 넘기는 일, 깨진 키 때문에 서버 전체가 안 뜨는 일, 값이 메시지·로그에 나가는 일.
 */
class AnalyticsPropertiesTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(AnalyticsConfig.class, GoogleGaAccessTokenProvider.class);

	@Test
	void 둘_다_비어_있으면_꺼진다() {
		// when
		AnalyticsProperties props = new AnalyticsProperties("", "", "http://x", Duration.ofMinutes(15));

		// then
		assertThat(props.isEnabled()).isFalse();
	}

	@Test
	void 속성_ID만_있으면_기동_실패이고_메시지에_값이_없다() {
		// when & then
		assertThatThrownBy(() -> new AnalyticsProperties("987654321", "", "http://x", Duration.ofMinutes(15)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("GA_PROPERTY_ID")
				.hasMessageNotContaining("987654321");
	}

	@Test
	void 키만_있으면_기동_실패이다() {
		// when & then
		assertThatThrownBy(() -> new AnalyticsProperties("", "c2VjcmV0LWtleQ==", "http://x", Duration.ofMinutes(15)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageNotContaining("c2VjcmV0LWtleQ==");
	}

	@Test
	void toString에_속성_ID와_키가_나오지_않는다() {
		// given
		AnalyticsProperties props = new AnalyticsProperties("987654321", "c2VjcmV0LWtleQ==", "http://x", Duration.ofMinutes(15));

		// when
		String text = props.toString();

		// then
		assertThat(text).doesNotContain("987654321").doesNotContain("c2VjcmV0LWtleQ==");
	}

	@Test
	void 설정이_없으면_컨텍스트가_뜨고_꺼져_있다() {
		runner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(AnalyticsProperties.class).isEnabled()).isFalse();
			assertThat(context.getBean(AnalyticsProperties.class).cacheTtl()).isEqualTo(Duration.ofMinutes(15));
		});
	}

	@Test
	void 하나만_넣으면_컨텍스트가_뜨지_않는다() {
		runner.withPropertyValues("app.analytics.property-id=987654321")
				.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void 둘_다_넣으면_토큰_호출_없이_컨텍스트가_뜬다() {
		// 키 내용이 깨져 있어도 기동은 막지 않는다(서버 전체가 방문자 카드 때문에 안 뜨면 안 된다)
		runner.withPropertyValues("app.analytics.property-id=987654321", "app.analytics.credentials-base64=깨진값")
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context.getBean(AnalyticsProperties.class).isEnabled()).isTrue();
				});
	}

	@Test
	void 키가_깨져_있으면_첫_호출에서_인증_예외이고_메시지에_키가_없다() {
		// given: Base64로는 읽히지만 JSON이 아닌 값
		String brokenKey = Base64.getEncoder().encodeToString("이것은 JSON이 아니다".getBytes());
		AnalyticsProperties props = new AnalyticsProperties("987654321", brokenKey, "http://x", Duration.ofMinutes(15));
		GoogleGaAccessTokenProvider provider = new GoogleGaAccessTokenProvider(props);

		// when & then
		assertThatThrownBy(provider::getAccessToken)
				.isInstanceOf(AnalyticsCredentialsException.class)
				.hasMessageNotContaining(brokenKey);
	}

	@Test
	void 키가_Base64가_아니면_첫_호출에서_인증_예외이다() {
		// given
		AnalyticsProperties props = new AnalyticsProperties("987654321", "깨진값", "http://x", Duration.ofMinutes(15));
		GoogleGaAccessTokenProvider provider = new GoogleGaAccessTokenProvider(props);

		// when & then
		assertThatThrownBy(provider::getAccessToken).isInstanceOf(AnalyticsCredentialsException.class);
	}
}
