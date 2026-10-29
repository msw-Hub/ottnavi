package com.ottnavi.infra.llm.exception;

import com.ottnavi.global.error.BusinessException;

/**
 * LLM(Gemini) 연동에서 생기는 예외의 공통 부모. 호출한 서비스가 종류별로 나눠 잡을 수 있게 한다.
 * BusinessException을 상속해 GlobalExceptionHandler가 종류별 errorCode로 바로 응답한다.
 */
public abstract class LlmException extends BusinessException {

    protected LlmException(String message) {
        super(message);
    }

    protected LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
