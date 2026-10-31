package com.ottnavi.infra.analytics;

import com.ottnavi.infra.analytics.exception.AnalyticsUnavailableException;
import feign.Response;
import feign.codec.ErrorDecoder;

/**
 * GA 오류 응답(2xx 밖)을 AnalyticsUnavailableException으로 바꾼다.
 * 응답 본문과 URL(속성 ID가 들어 있다)은 메시지에 넣지 않고 상태 코드만 남긴다.
 */
public class GaErrorDecoder implements ErrorDecoder {

	@Override
	public Exception decode(String methodKey, Response response) {
		int status = response.status();
		String reason;
		if (status == 401) {
			reason = "인증 실패";
		} else if (status == 403) {
			reason = "권한 없음(서비스 계정이 속성에 추가됐는지 확인)";
		} else if (status == 429) {
			reason = "호출 한도 초과";
		} else if (status >= 500) {
			reason = "서버 오류";
		} else {
			reason = "요청 거절";
		}
		return new AnalyticsUnavailableException("GA가 오류로 응답했다: 상태 " + status + ", " + reason);
	}
}
