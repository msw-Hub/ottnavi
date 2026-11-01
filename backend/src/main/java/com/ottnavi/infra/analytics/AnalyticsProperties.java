package com.ottnavi.infra.analytics;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Google Analytics 4 연동 설정(app.analytics.*). 값은 환경 변수 GA_PROPERTY_ID·GA_CREDENTIALS_BASE64로만 넣는다.
 *
 * <pre>
 * 둘 다 비어 있음 → 꺼짐(방문자 통계 API가 status=DISABLED로 응답)
 * 하나만 있음     → 기동 실패(설정 실수를 켜진 척 넘기지 않는다)
 * 둘 다 있음      → 켜짐(키 내용이 깨졌는지는 첫 호출에서 확인하고, 그때는 기동이 아니라 UNAVAILABLE로 알린다)
 * </pre>
 *
 * 오류 메시지와 toString에는 속성 ID·키 값을 넣지 않는다. 호출 시간 제한은 application.yml의
 * spring.cloud.openfeign.client.config.ga에 있다(TMDB와 같은 방식).
 *
 * @param propertyId        GA4 속성 ID(숫자)
 * @param credentialsBase64 서비스 계정 키 JSON을 Base64로 인코딩한 값
 * @param baseUrl           GA Data API 주소(테스트에서 WireMock으로 바꾼다)
 * @param cacheTtl          성공 결과를 Redis에 두는 시간(기본 15분)
 */
@ConfigurationProperties(prefix = "app.analytics")
public record AnalyticsProperties(
		@DefaultValue("") String propertyId,
		@DefaultValue("") String credentialsBase64,
		@DefaultValue("https://analyticsdata.googleapis.com") String baseUrl,
		@DefaultValue("15m") Duration cacheTtl) {

	public AnalyticsProperties {
		// 1. 하나만 있으면 설정 실수라 기동을 멈춘다(값은 메시지에 넣지 않는다)
		boolean hasPropertyId = propertyId != null && !propertyId.isBlank();
		boolean hasCredentials = credentialsBase64 != null && !credentialsBase64.isBlank();
		if (hasPropertyId != hasCredentials) {
			throw new IllegalStateException(
					"app.analytics의 property-id(GA_PROPERTY_ID)와 credentials-base64(GA_CREDENTIALS_BASE64)는 둘 다 넣거나 둘 다 비워야 합니다.");
		}
		// 2. 캐시 시간이 없거나 0 이하면 Redis가 만료 없음으로 읽을 수 있어 막는다(15분 캐시가 영구 캐시가 되는 실수 방지)
		if (cacheTtl == null || cacheTtl.isZero() || cacheTtl.isNegative()) {
			throw new IllegalStateException("app.analytics.cache-ttl은 0보다 커야 합니다.");
		}
	}

	/** 속성 ID와 키가 모두 있으면 방문자 통계가 켜진 것이다 */
	public boolean isEnabled() {
		return propertyId != null && !propertyId.isBlank() && credentialsBase64 != null && !credentialsBase64.isBlank();
	}

	/** 로그·오류 출력에 속성 ID와 키가 나가지 않게 가린다 */
	@Override
	public String toString() {
		return "AnalyticsProperties[propertyId=****, credentialsBase64=****, baseUrl=" + baseUrl
				+ ", cacheTtl=" + cacheTtl + ", enabled=" + isEnabled() + "]";
	}
}
