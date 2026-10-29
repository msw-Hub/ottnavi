package com.ottnavi.infra.llm.exception;

import com.ottnavi.global.error.ErrorCode;

/** Gemini 호출이 한도 초과 외의 이유로 실패했거나 응답이 비어 있었다(연결·인증·서버 오류·시간 초과·빈 응답 등). 원인은 cause로 남긴다. 502 LLM_CALL_FAILED로 응답한다 */
public class LlmCallFailedException extends LlmException {

    public LlmCallFailedException(String message) {
        super(message);
    }

    public LlmCallFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public ErrorCode getErrorCode() {
        return ErrorCode.LLM_CALL_FAILED;
    }
}
