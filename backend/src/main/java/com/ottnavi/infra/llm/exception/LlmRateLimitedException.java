package com.ottnavi.infra.llm.exception;

import com.ottnavi.global.error.ErrorCode;

/** Gemini가 호출 한도 초과(429, RESOURCE_EXHAUSTED)로 거절했다. 잠시 뒤 다시 시도하면 될 수 있다. 429 LLM_RATE_LIMITED로 응답한다 */
public class LlmRateLimitedException extends LlmException {

    public LlmRateLimitedException(String message) {
        super(message);
    }

    public LlmRateLimitedException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public ErrorCode getErrorCode() {
        return ErrorCode.LLM_RATE_LIMITED;
    }
}
