package com.ottnavi.infra.tmdb.exception;

import com.ottnavi.global.error.BusinessException;
import com.ottnavi.global.error.ErrorCode;

/**
 * TMDB 호출 실패의 부모 예외. 하위 예외는 모두 EXTERNAL_API_ERROR(502)로 응답한다.
 * TMDB가 준 429도 여기로 모인다. RATE_LIMIT_EXCEEDED(429)는 우리 서비스의 공개 API 요청 제한이라 다른 개념이다(8절 6번).
 * 메시지는 응답 detail로 나갈 수 있으므로 고정 문구만 쓰고 URL·본문·토큰을 넣지 않는다. 원인은 cause로 남긴다.
 */
public abstract class TmdbException extends BusinessException {

    protected TmdbException(String message) {
        super(message);
    }

    protected TmdbException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public ErrorCode getErrorCode() {
        return ErrorCode.EXTERNAL_API_ERROR;
    }
}
