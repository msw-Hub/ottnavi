package com.ottnavi.infra.llm;

import com.google.genai.errors.ApiException;
import com.google.genai.errors.ServerException;
import com.ottnavi.infra.llm.exception.LlmCallFailedException;
import com.ottnavi.infra.llm.exception.LlmDisabledException;
import com.ottnavi.infra.llm.exception.LlmRateLimitedException;
import com.ottnavi.infra.llm.exception.LlmUpstreamBusyException;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 관리자 줄거리 번역이 쓰는 Gemini 호출 창구.
 * 호출 결과를 llm.call 카운터로 세고, 실패를 LlmException 계열로 바꿔 던진다. 서비스는 이 창구 밖에서 지표를 세지 않는다.
 * 외부 호출이므로 트랜잭션 밖에서 부른다(backend.md). 프롬프트·번역문 본문은 로그에 남기지 않는다.
 * 한 번의 호출은 app.llm.timeout-seconds 안에 끝나야 하고, 넘으면 호출 스레드를 중단시키고 실패로 본다.
 */
@Slf4j
@Component
public class GeminiClient {

    // llm.call 카운터: 이름과 태그 값. 값 집합은 닫혀 있다(작품 ID·줄거리 본문은 태그로 쓰지 않는다)
    private static final String METRIC_NAME = "llm.call";
    private static final String DURATION_METRIC_NAME = "llm.call.duration";   // 호출 한 번에 걸린 시간(성공·실패 모두)
    private static final String OP_TRANSLATE_OVERVIEW = "translate_overview";
    private static final String RESULT_SUCCESS = "success";
    private static final String RESULT_DISABLED = "disabled";
    private static final String RESULT_RATE_LIMITED = "rate_limited";
    private static final String RESULT_BUSY = "busy";   // 구글 서버 혼잡(503)
    private static final String RESULT_EMPTY = "empty_response";
    private static final String RESULT_TIMEOUT = "timeout";
    private static final String RESULT_ERROR = "error";
    private static final int CAUSE_DEPTH_LIMIT = 10;   // 원인 사슬을 따라가는 최대 깊이(순환 방지)

    private final ObjectProvider<ChatModel> chatModelProvider;   // 키가 없으면 빈이 없다(LlmConfig)
    private final LlmPromptService promptService;
    private final MeterRegistry meterRegistry;
    private final LlmProperties properties;

    // 시간 제한을 걸려고 호출을 이 실행기의 스레드에서 한다(Spring AI·구글 SDK는 호출 시간 제한 설정을 주지 않는다)
    private final ExecutorService callExecutor = Executors.newCachedThreadPool(new CallThreadFactory());

    public GeminiClient(ObjectProvider<ChatModel> chatModelProvider, LlmPromptService promptService,
                        MeterRegistry meterRegistry, LlmProperties properties) {
        this.chatModelProvider = chatModelProvider;
        this.promptService = promptService;
        this.meterRegistry = meterRegistry;
        this.properties = properties;
    }

    /** 키가 설정돼 호출할 수 있는지. 상한 카운터를 올리기 전에 "키 없음"을 먼저 걸러내는 데 쓴다 */
    public boolean isEnabled() {
        return chatModelProvider.getIfAvailable() != null;
    }

    /**
     * 영어 줄거리를 한국어로 번역해 돌려준다(초안 문자열, 앞뒤 공백 제거).
     * 키가 없으면 LlmDisabledException, 한도 초과(429)면 LlmRateLimitedException, 구글 서버 혼잡(503)이면 LlmUpstreamBusyException,
     * 그 밖의 실패·시간 초과·빈 응답이면 LlmCallFailedException을 던진다.
     */
    public String translateToKorean(String englishOverview) {
        // 1. 입력 확인
        if (englishOverview == null || englishOverview.isBlank()) {
            throw new IllegalArgumentException("번역할 줄거리가 비어 있다");
        }
        // 2. 키가 없으면 호출하지 않는다
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            count(RESULT_DISABLED);
            throw new LlmDisabledException("Gemini 키(GEMINI_API_KEY)가 설정되지 않아 번역을 호출하지 않았다");
        }
        // 3. 시간 제한을 걸어 호출하고 실패를 종류별 예외로 바꾼다
        Prompt prompt = buildPrompt(englishOverview);
        long startNanos = System.nanoTime();
        ChatResponse response;
        try {
            response = callWithTimeout(chatModel, prompt);
        } finally {
            meterRegistry.timer(DURATION_METRIC_NAME, "operation", OP_TRANSLATE_OVERVIEW)
                    .record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
        }
        // 4. 응답에서 번역문을 꺼낸다. 비어 있으면 실패로 본다
        String text = extractText(response);
        if (text == null || text.isBlank()) {
            count(RESULT_EMPTY);
            log.warn("Gemini가 빈 응답을 돌려줬다");
            throw new LlmCallFailedException("Gemini가 빈 응답을 돌려줬다");
        }
        count(RESULT_SUCCESS);
        return text.strip();
    }

    /**
     * 번역 프롬프트를 만든다. app.llm.thinking-level이 비어 있으면 옵션을 붙이지 않아 모델 기본값을 쓰고,
     * 값이 있으면 그 생각 수준만 이 호출 옵션으로 넘긴다(모델·최대 토큰은 기본 옵션과 합쳐진다. 온도는 구글 권고에 따라 지정하지 않는다).
     * 알려진 수준(MINIMAL·LOW·MEDIUM·HIGH)이 아니면 LlmCallFailedException이다.
     * 이 모델이 그 수준을 지원하는지는 Spring AI가 호출 시점에 검사한다.
     */
    Prompt buildPrompt(String englishOverview) {
        // 1. 기본 프롬프트
        Prompt prompt = promptService.translateOverviewToKorean(englishOverview);
        String level = properties.thinkingLevel();
        if (level == null || level.isBlank()) {
            return prompt;
        }
        // 2. 생각 수준 문자열을 옵션 값으로 바꾼다. 모르는 값은 호출하지 않고 실패로 드러낸다
        GoogleGenAiThinkingLevel thinkingLevel;
        try {
            thinkingLevel = GoogleGenAiThinkingLevel.valueOf(level.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            count(RESULT_ERROR);
            throw new LlmCallFailedException("생각 수준 설정(app.llm.thinking-level)이 올바르지 않다: model="
                    + properties.model() + ", value=" + level, e);
        }
        // 3. 이 호출에만 생각 수준 옵션을 붙인다
        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder().thinkingLevel(thinkingLevel).build();
        return new Prompt(prompt.getInstructions(), options);
    }

    /** 호출을 별도 스레드에서 실행하고 제한 시간 안에 결과를 받는다. 실패는 종류별 LlmException으로 바꾼다 */
    private ChatResponse callWithTimeout(ChatModel chatModel, Prompt prompt) {
        Future<ChatResponse> future = callExecutor.submit(new ChatCall(chatModel, prompt));
        try {
            return future.get(properties.timeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            count(RESULT_TIMEOUT);
            log.warn("Gemini 호출이 제한 시간을 넘겼다: timeoutSeconds={}", properties.timeoutSeconds());
            throw new LlmCallFailedException("Gemini 호출이 " + properties.timeoutSeconds() + "초 안에 끝나지 않았다", e);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            count(RESULT_ERROR);
            throw new LlmCallFailedException("Gemini 호출을 기다리다 중단됐다", e);
        } catch (ExecutionException e) {
            Throwable error = e.getCause();
            if (isRateLimited(error)) {
                count(RESULT_RATE_LIMITED);
                log.warn("Gemini 호출 한도를 초과했다: exception={}", error.getClass().getSimpleName());
                throw new LlmRateLimitedException("Gemini 호출 한도를 초과했다. 잠시 뒤 다시 시도한다", error);
            }
            if (isUpstreamBusy(error)) {
                count(RESULT_BUSY);
                log.warn("구글 서버가 혼잡해 Gemini 호출이 거절됐다: exception={}", error.getClass().getSimpleName());
                throw new LlmUpstreamBusyException("구글 서버가 혼잡해 Gemini 호출이 거절됐다. 잠시 뒤 다시 시도한다", error);
            }
            count(RESULT_ERROR);
            log.warn("Gemini 호출이 실패했다: exception={}", error.getClass().getSimpleName());
            throw new LlmCallFailedException("Gemini 호출에 실패했다", error);
        }
    }

    private String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }

    /**
     * 원인 사슬에 HTTP 429(구글 SDK ApiException 코드 또는 RESOURCE_EXHAUSTED 문구)가 있으면 한도 초과로 본다.
     * Spring AI가 SDK 예외를 다시 감싸는 경우가 있어 원인을 따라간다.
     */
    private boolean isRateLimited(Throwable error) {
        Throwable current = error;
        for (int depth = 0; depth < CAUSE_DEPTH_LIMIT && current != null; depth++) {
            if (current instanceof ApiException apiException && apiException.code() == 429) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && (message.contains("RESOURCE_EXHAUSTED") || message.contains("429"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 원인 사슬에 구글 서버 혼잡이 있으면 true다: ServerException(HTTP 503), 또는 메시지에 "high demand"·UNAVAILABLE.
     * 429(한도 초과)는 isRateLimited가 먼저 걸러내므로 여기서는 다루지 않는다.
     */
    private boolean isUpstreamBusy(Throwable error) {
        Throwable current = error;
        for (int depth = 0; depth < CAUSE_DEPTH_LIMIT && current != null; depth++) {
            if (current instanceof ServerException serverException && serverException.code() == 503) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && (message.contains("high demand") || message.contains("UNAVAILABLE"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void count(String result) {
        meterRegistry.counter(METRIC_NAME, "operation", OP_TRANSLATE_OVERVIEW, "result", result).increment();
    }

    @PreDestroy
    void shutdown() {
        callExecutor.shutdownNow();
    }

    /** ChatModel 호출 한 번을 실행기에 맡기는 작업 */
    private static class ChatCall implements Callable<ChatResponse> {

        private final ChatModel chatModel;
        private final Prompt prompt;

        ChatCall(ChatModel chatModel, Prompt prompt) {
            this.chatModel = chatModel;
            this.prompt = prompt;
        }

        @Override
        public ChatResponse call() {
            return chatModel.call(prompt);
        }
    }

    /** 호출 스레드에 알아보기 쉬운 이름을 붙이고, 서버 종료를 막지 않게 데몬으로 만든다 */
    private static class CallThreadFactory implements ThreadFactory {

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "gemini-call");
            thread.setDaemon(true);
            return thread;
        }
    }
}
