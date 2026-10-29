package com.ottnavi.infra.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * LLM 연동 설정(app.llm.*). 키와 모델의 실제 값은 Spring AI 표준 키(spring.ai.google.genai.*)에 있다.
 * 사용 여부는 키가 있는지로 정한다(별도 enabled 설정을 두지 않는다).
 *
 * @param model           사용 중인 모델 이름. 화면·로그에 보여 주는 용도(GEMINI_MODEL, 기본 gemini-3.5-flash-lite)
 * @param timeoutSeconds  Gemini 한 번 호출을 기다리는 최대 시간(초). 넘으면 LlmCallFailedException이다
 * @param maxOutputTokens 응답 최대 토큰 수. application.yml이 spring.ai.google.genai.chat.options.max-output-tokens에 같은 값을 넘긴다.
 *                        Gemini 3.x의 생각(thinking) 토큰이 이 한도를 쓸 수 있어 번역문(최대 1000자)보다 넉넉하게 둔다
 * @param thinkingLevel   생각(thinking) 수준(GEMINI_THINKING_LEVEL). 빈 값이면 아무것도 전달하지 않아 모델 기본값을 쓴다.
 *                        값이 있으면 MINIMAL, LOW, MEDIUM, HIGH 중 하나(대소문자 무관)이고, 지원 값은 모델마다 다르다
 */
@ConfigurationProperties(prefix = "app.llm")
public record LlmProperties(
        String model,
        @DefaultValue("90") int timeoutSeconds,
        @DefaultValue("4096") int maxOutputTokens,
        @DefaultValue("") String thinkingLevel) {
}
