package com.ottnavi.infra.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.genai.errors.ClientException;
import com.google.genai.errors.ServerException;
import com.ottnavi.infra.llm.exception.LlmCallFailedException;
import com.ottnavi.infra.llm.exception.LlmDisabledException;
import com.ottnavi.infra.llm.exception.LlmRateLimitedException;
import com.ottnavi.infra.llm.exception.LlmUpstreamBusyException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;
import org.springframework.beans.factory.ObjectProvider;

/** GeminiClient 단위 테스트: 정상 번역, 키 없음, 한도 초과(429) 변환, 그 밖의 실패 변환, 빈 응답, 지표 */
class GeminiClientTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ChatModel chatModel = mock(ChatModel.class);

    @SuppressWarnings("unchecked")
    private GeminiClient clientWith(ChatModel model) {
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(model);
        return new GeminiClient(provider, new LlmPromptService(), meterRegistry, new LlmProperties("test-model", 5, 4096, ""));
    }

    private static ChatResponse responseOf(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private double count(String result) {
        return meterRegistry.counter("llm.call", "operation", "translate_overview", "result", result).count();
    }

    @Test
    void 정상이면_번역문을_앞뒤_공백_없이_돌려주고_success를_센다() {
        // given
        when(chatModel.call(any(Prompt.class))).thenReturn(responseOf("  한 형사가 살인범을 쫓는다.\n"));

        // when
        String result = clientWith(chatModel).translateToKorean("A detective hunts a killer.");

        // then
        assertThat(result).isEqualTo("한 형사가 살인범을 쫓는다.");
        assertThat(count("success")).isEqualTo(1.0);
    }

    @Test
    void 호출에는_원문이_들어간_프롬프트를_보낸다() {
        // given
        when(chatModel.call(any(Prompt.class))).thenReturn(responseOf("번역"));

        // when
        clientWith(chatModel).translateToKorean("A detective hunts a killer.");

        // then
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        assertThat(captor.getValue().getContents()).contains("A detective hunts a killer.");
    }

    @Test
    void 키가_없으면_호출하지_않고_LlmDisabledException을_던진다() {
        // given: ChatModel 빈이 없다
        GeminiClient client = clientWith(null);

        // when, then
        assertThatThrownBy(() -> client.translateToKorean("English")).isInstanceOf(LlmDisabledException.class);
        assertThat(count("disabled")).isEqualTo(1.0);
    }

    @Test
    void 호출한도_초과_429는_LlmRateLimitedException으로_바꾼다() {
        // given
        when(chatModel.call(any(Prompt.class))).thenThrow(new ClientException(429, "RESOURCE_EXHAUSTED", "quota"));
        GeminiClient client = clientWith(chatModel);

        // when, then
        assertThatThrownBy(() -> client.translateToKorean("English"))
                .isInstanceOf(LlmRateLimitedException.class)
                .hasCauseInstanceOf(ClientException.class);
        assertThat(count("rate_limited")).isEqualTo(1.0);
    }

    @Test
    void 다른_예외로_감싸진_429도_한도_초과로_본다() {
        // given: Spring AI가 SDK 예외를 한 겹 감쌌다
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new IllegalStateException("호출 실패", new ClientException(429, "RESOURCE_EXHAUSTED", "quota")));
        GeminiClient client = clientWith(chatModel);

        // when, then
        assertThatThrownBy(() -> client.translateToKorean("English")).isInstanceOf(LlmRateLimitedException.class);
    }

    @Test
    void 한도_초과가_아닌_실패는_LlmCallFailedException으로_바꾼다() {
        // given
        when(chatModel.call(any(Prompt.class))).thenThrow(new ServerException(500, "INTERNAL", "서버 오류"));
        GeminiClient client = clientWith(chatModel);

        // when, then
        assertThatThrownBy(() -> client.translateToKorean("English"))
                .isInstanceOf(LlmCallFailedException.class)
                .hasCauseInstanceOf(ServerException.class);
        assertThat(count("error")).isEqualTo(1.0);
    }

    @Test
    void 구글_서버_혼잡_503은_LlmUpstreamBusyException으로_바꾸고_busy를_센다() {
        // given
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new ServerException(503, "UNAVAILABLE", "This model is currently experiencing high demand."));
        GeminiClient client = clientWith(chatModel);

        // when, then
        assertThatThrownBy(() -> client.translateToKorean("English"))
                .isInstanceOf(LlmUpstreamBusyException.class)
                .hasCauseInstanceOf(ServerException.class);
        assertThat(count("busy")).isEqualTo(1.0);
        assertThat(count("rate_limited")).isEqualTo(0.0);
        assertThat(count("error")).isEqualTo(0.0);
    }

    @Test
    void 다른_예외로_감싸진_high_demand_메시지도_혼잡으로_본다() {
        // given
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new IllegalStateException("호출 실패", new IllegalStateException("model is experiencing high demand")));
        GeminiClient client = clientWith(chatModel);

        // when, then
        assertThatThrownBy(() -> client.translateToKorean("English")).isInstanceOf(LlmUpstreamBusyException.class);
    }

    @Test
    void 응답이_비어_있으면_LlmCallFailedException이다() {
        // given
        when(chatModel.call(any(Prompt.class))).thenReturn(responseOf("   "));
        GeminiClient client = clientWith(chatModel);

        // when, then
        assertThatThrownBy(() -> client.translateToKorean("English")).isInstanceOf(LlmCallFailedException.class);
        assertThat(count("empty_response")).isEqualTo(1.0);
    }

    @Test
    void 번역할_줄거리가_비어_있으면_호출하지_않고_거절한다() {
        // given
        GeminiClient client = clientWith(chatModel);

        // when, then
        assertThatThrownBy(() -> client.translateToKorean("  ")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(chatModel);
    }

    @Test
    void 제한_시간_안에_끝나지_않으면_LlmCallFailedException이고_timeout을_센다() {
        // given: 호출이 제한 시간(1초)보다 오래 걸린다
        when(chatModel.call(any(Prompt.class))).thenAnswer(new Answer<ChatResponse>() {
            @Override
            public ChatResponse answer(InvocationOnMock invocation) throws Throwable {
                Thread.sleep(10_000);
                return responseOf("늦은 응답");
            }
        });
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(chatModel);
        GeminiClient client = new GeminiClient(provider, new LlmPromptService(), meterRegistry,
                new LlmProperties("test-model", 1, 4096, ""));

        // when, then
        assertThatThrownBy(() -> client.translateToKorean("English"))
                .isInstanceOf(LlmCallFailedException.class)
                .hasCauseInstanceOf(TimeoutException.class);
        assertThat(count("timeout")).isEqualTo(1.0);
    }

    private GeminiClient clientWithThinking(String thinkingLevel) {
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(chatModel);
        return new GeminiClient(provider, new LlmPromptService(), meterRegistry,
                new LlmProperties("test-model", 5, 4096, thinkingLevel));
    }

    @Test
    void 생각_수준이_비어_있으면_옵션을_붙이지_않는다() {
        // given, when
        Prompt prompt = clientWithThinking("  ").buildPrompt("A detective hunts a killer.");

        // then
        assertThat(prompt.getOptions()).isNull();
    }

    @Test
    void 생각_수준이_있으면_그_값을_호출_옵션으로_넘긴다() {
        // given, when: 소문자도 받는다
        Prompt prompt = clientWithThinking("minimal").buildPrompt("A detective hunts a killer.");

        // then
        assertThat(prompt.getOptions()).isInstanceOf(GoogleGenAiChatOptions.class);
        assertThat(((GoogleGenAiChatOptions) prompt.getOptions()).getThinkingLevel())
                .isEqualTo(GoogleGenAiThinkingLevel.MINIMAL);
        assertThat(prompt.getContents()).contains("A detective hunts a killer.");
    }

    @Test
    void 알_수_없는_생각_수준이면_호출하지_않고_LlmCallFailedException이다() {
        // given
        GeminiClient client = clientWithThinking("turbo");

        // when, then
        assertThatThrownBy(() -> client.translateToKorean("English"))
                .isInstanceOf(LlmCallFailedException.class)
                .hasMessageContaining("test-model");
        verifyNoInteractions(chatModel);
    }

    @Test
    void 호출하면_소요_시간_타이머에_기록한다() {
        // given
        when(chatModel.call(any(Prompt.class))).thenReturn(responseOf("번역"));

        // when
        clientWith(chatModel).translateToKorean("English");

        // then
        assertThat(meterRegistry.timer("llm.call.duration", "operation", "translate_overview").count()).isEqualTo(1L);
    }

    @Test
    void 키가_있으면_isEnabled가_true이고_없으면_false이다() {
        // given, when, then
        assertThat(clientWith(chatModel).isEnabled()).isTrue();
        assertThat(clientWith(null).isEnabled()).isFalse();
    }
}
