package com.ottnavi.infra.llm.exception;

import com.ottnavi.global.error.ErrorCode;

/**
 * 구글 서버가 혼잡해 거절했다(HTTP 503, "high demand", UNAVAILABLE). 한도 초과(429)와 달리 잠시 뒤 같은 요청을 다시 보내면 될 수 있다.
 * 502 LLM_UPSTREAM_BUSY로 응답한다
 */
public class LlmUpstreamBusyException extends LlmException {

    public LlmUpstreamBusyException(String message) {
        super(message);
    }

    public LlmUpstreamBusyException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public ErrorCode getErrorCode() {
        return ErrorCode.LLM_UPSTREAM_BUSY;
    }
}
