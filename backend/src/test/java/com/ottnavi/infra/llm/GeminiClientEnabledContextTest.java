package com.ottnavi.infra.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

/** 키가 있으면 Spring AI Gemini 자동 구성이 켜져 ChatModel 빈이 생기는지 확인한다. 가짜 키라 외부 호출은 하지 않는다(Docker 필요) */
@TestPropertySource(properties = "spring.ai.google.genai.api-key=test-dummy-key")
class GeminiClientEnabledContextTest extends IntegrationTestSupport {

    @Autowired
    private ApplicationContext context;

    @Test
    void 키가_있으면_ChatModel_빈이_하나_생긴다() {
        // given, when
        String[] beanNames = context.getBeanNamesForType(ChatModel.class);

        // then
        assertThat(beanNames).hasSize(1);
    }
}
