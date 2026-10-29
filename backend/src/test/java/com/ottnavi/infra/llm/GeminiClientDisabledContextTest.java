package com.ottnavi.infra.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ottnavi.infra.llm.exception.LlmDisabledException;
import com.ottnavi.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

/** 키가 비어 있어도 컨텍스트가 기동하고, ChatModel 빈 없이 GeminiClient가 비활성 예외를 던지는지 확인한다(Docker 필요) */
@TestPropertySource(properties = "spring.ai.google.genai.api-key=")
class GeminiClientDisabledContextTest extends IntegrationTestSupport {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private GeminiClient geminiClient;

    @Autowired
    private LlmProperties llmProperties;

    @Test
    void 키가_비어_있으면_ChatModel_빈이_없고_번역은_LlmDisabledException이다() {
        // given, when, then
        assertThat(context.getBeanNamesForType(ChatModel.class)).isEmpty();
        assertThatThrownBy(() -> geminiClient.translateToKorean("English")).isInstanceOf(LlmDisabledException.class);
    }

    @Test
    void 모델_이름_기본값은_gemini_3_5_flash_lite이다() {
        assertThat(llmProperties.model()).isEqualTo("gemini-3.5-flash-lite");
    }
}
