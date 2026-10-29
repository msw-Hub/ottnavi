package com.ottnavi.infra.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.support.IntegrationTestSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 실제 Gemini를 호출하는 선택 시험. 환경 변수 GEMINI_API_KEY가 있을 때만 돈다(없으면 건너뛴다).
 * 번역문 전체는 출력하지 않고 한글이 들어 있는지만 확인한다. 무료 한도(분당 15회)를 아끼려고 길이가 다른 3건을 각각 두 번 보내 6회 호출한다.
 *
 * 호출마다 "LIVE sample=N attempt=M elapsedMs=... chars=... usage=..." 줄을 System.out으로 출력한다(본문·키는 출력하지 않는다).
 * 같은 샘플을 두 번 보내 첫 호출의 초기화 비용(attempt=1)과 순수 호출 시간(attempt=2)을 가른다.
 * 생각 수준은 환경 변수 GEMINI_THINKING_LEVEL(app.llm.thinking-level)로 바꿔 가며 비교한다.
 * 출력 확인: build/test-results/test/TEST-com.ottnavi.infra.llm.GeminiClientLiveTest.xml 의 system-out 영역,
 * 또는 gradle 실행 때 --info를 붙인다.
 */
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class GeminiClientLiveTest extends IntegrationTestSupport {

    @Autowired
    private GeminiClient geminiClient;

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private LlmProperties properties;

    @Test
    void 실제_Gemini가_영어_줄거리를_한글로_번역한다() {
        // given: 길이가 다른 샘플 3개(짧음, 중간, 긴 편)
        List<String> overviews = List.of(
                "A young woman discovers that her small hometown hides a decades-old secret.",
                "Two rival chefs are forced to run a tiny restaurant together after a rainy-season flood. "
                        + "As the days pass, they learn each other's recipes, fears and ambitions, and the kitchen becomes the only place that feels like home.",
                "A retired detective is pulled back into one last case when his former partner disappears. "
                        + "Following a trail of old files, forgotten informants and a half-burned ledger, he realizes that the people he once trusted most "
                        + "have been hiding the truth about a case he thought was closed years ago. With a young reporter at his side and the department "
                        + "turning against him, he has only a few days before the evidence disappears for good, and every answer he finds raises a darker question.");
        String thinking = properties.thinkingLevel();
        String thinkingLabel = "기본";
        if (thinking != null && !thinking.isBlank()) {
            thinkingLabel = thinking;
        }
        System.out.println("LIVE config model=" + properties.model() + " thinkingLevel=" + thinkingLabel);

        // when, then: 샘플마다 두 번씩 호출한다
        for (int sample = 1; sample <= overviews.size(); sample++) {
            for (int attempt = 1; attempt <= 2; attempt++) {
                Prompt prompt = geminiClient.buildPrompt(overviews.get(sample - 1));
                long startNanos = System.nanoTime();
                ChatResponse response = chatModel.call(prompt);
                long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

                String translated = response.getResult().getOutput().getText();
                System.out.println("LIVE sample=" + sample + " attempt=" + attempt + " elapsedMs=" + elapsedMs
                        + " chars=" + translated.length() + " usage=" + describeUsage(response));
                assertThat(translated).matches("(?s).*[가-힣].*");
                assertThat(translated.length()).isLessThanOrEqualTo(LlmPromptService.MAX_TRANSLATION_CHARS);
            }
        }
    }

    /** 토큰 사용량을 한 줄로 만든다. 읽지 못하는 항목은 "알 수 없음"이다. 생각 토큰은 총합에서 프롬프트·응답을 뺀 값으로 추정한다 */
    private String describeUsage(ChatResponse response) {
        if (response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return "알 수 없음";
        }
        Usage usage = response.getMetadata().getUsage();
        Integer prompt = usage.getPromptTokens();
        Integer completion = usage.getCompletionTokens();
        Integer total = usage.getTotalTokens();
        String thinkingEstimate = "알 수 없음";
        if (prompt != null && completion != null && total != null && total > 0) {
            thinkingEstimate = String.valueOf(total - prompt - completion);
        }
        return "prompt=" + valueOf(prompt) + ",completion=" + valueOf(completion)
                + ",total=" + valueOf(total) + ",thinkingEstimate=" + thinkingEstimate;
    }

    private String valueOf(Integer value) {
        if (value == null) {
            return "알 수 없음";
        }
        return String.valueOf(value);
    }
}
