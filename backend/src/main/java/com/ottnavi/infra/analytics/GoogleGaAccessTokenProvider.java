package com.ottnavi.infra.analytics;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.ottnavi.infra.analytics.exception.AnalyticsCredentialsException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 서비스 계정 키(Base64 JSON)로 읽기 전용 토큰을 받아 오는 실제 구현(google-auth-library).
 * 키는 첫 호출 때 읽는다. 키가 깨져 있어도 서버 기동은 막지 않고, 호출할 때 AnalyticsCredentialsException으로 알린다.
 * 토큰은 만료 전이면 재사용하고 만료가 가까우면 라이브러리가 갱신한다. 예외에는 원인 예외를 붙이지 않는다(메시지에 키 조각이 섞일 수 있다).
 */
@Component
@RequiredArgsConstructor
public class GoogleGaAccessTokenProvider implements GaAccessTokenProvider {

	private static final List<String> SCOPES = List.of("https://www.googleapis.com/auth/analytics.readonly"); // 읽기 전용

	private final AnalyticsProperties props;
	private GoogleCredentials credentials; // 첫 호출 때 만든다

	@Override
	public synchronized String getAccessToken() {
		try {
			// 1. 첫 호출이면 Base64 키를 읽어 읽기 전용 범위의 자격 증명을 만든다
			if (credentials == null) {
				credentials = createCredentials();
			}
			// 2. 토큰이 없거나 만료가 가까우면 갱신한다
			credentials.refreshIfExpired();
			AccessToken token = credentials.getAccessToken();
			if (token == null) {
				credentials.refresh();
				token = credentials.getAccessToken();
			}
			// 3. 그래도 없으면 실패로 본다
			if (token == null) {
				throw new AnalyticsCredentialsException("GA 액세스 토큰을 받지 못했다");
			}
			return token.getTokenValue();
		} catch (AnalyticsCredentialsException e) {
			throw e;
		} catch (IOException | RuntimeException e) {
			throw new AnalyticsCredentialsException(
					"GA 인증 정보를 읽거나 토큰을 받지 못했다. GA_CREDENTIALS_BASE64 값을 확인한다(원인 종류: "
							+ e.getClass().getSimpleName() + ")");
		}
	}

	private GoogleCredentials createCredentials() throws IOException {
		String compact = props.credentialsBase64().replaceAll("\\s", "");
		byte[] json = Base64.getDecoder().decode(compact);
		try (ByteArrayInputStream in = new ByteArrayInputStream(json)) {
			return GoogleCredentials.fromStream(in).createScoped(SCOPES);
		}
	}
}
