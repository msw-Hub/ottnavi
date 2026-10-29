package com.ottnavi.infra.llm;

import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * LLM 설정 등록.
 * Spring AI의 Gemini 자동 구성은 키가 비어 있으면 기동 자체가 실패한다(IllegalStateException).
 * 그래서 application.yml에서 자동 구성을 제외하고(spring.autoconfigure.exclude), 키가 있을 때만 여기서 직접 가져온다.
 * 키가 없으면 ChatModel 빈이 없고, GeminiClient가 LlmDisabledException을 던진다.
 */
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmConfig {

    /** 키(spring.ai.google.genai.api-key)가 비어 있지 않을 때만 Spring AI Gemini 자동 구성을 켠다 */
    @Configuration
    @ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${spring.ai.google.genai.api-key:}')")
    @Import(GoogleGenAiChatAutoConfiguration.class)
    static class GeminiEnabledConfig {
    }
}
