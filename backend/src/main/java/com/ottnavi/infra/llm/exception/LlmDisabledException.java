package com.ottnavi.infra.llm.exception;

import com.ottnavi.global.error.ErrorCode;

/** LLM 키(GEMINI_API_KEY)가 설정되지 않아 호출하지 않았다. 외부 호출은 일어나지 않았다. 503 LLM_DISABLED로 응답한다 */
public class LlmDisabledException extends LlmException {

    public LlmDisabledException(String message) {
        super(message);
    }

    public LlmDisabledException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public ErrorCode getErrorCode() {
        return ErrorCode.LLM_DISABLED;
    }
}
