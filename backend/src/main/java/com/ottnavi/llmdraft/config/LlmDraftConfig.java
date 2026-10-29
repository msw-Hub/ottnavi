package com.ottnavi.llmdraft.config;

import com.ottnavi.llmdraft.domain.rule.DraftTextGuard;
import com.ottnavi.llmdraft.domain.rule.GuardLimits;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 줄거리 번역 초안 설정 등록: 설정 값 바인딩과 자동 검사 규칙 빈 */
@Configuration
@EnableConfigurationProperties(LlmDraftProperties.class)
public class LlmDraftConfig {

    /** 순수 Java 규칙(DraftTextGuard)은 프레임워크를 모르므로 설정 값을 넘겨 여기서 빈으로 만든다 */
    @Bean
    public DraftTextGuard draftTextGuard(LlmDraftProperties properties) {
        LlmDraftProperties.Guard guard = properties.guard();
        return new DraftTextGuard(new GuardLimits(guard.minHangulRatio(), guard.minLengthRatio(),
                guard.maxLengthRatio(), guard.maxLength(), guard.sentenceCountTolerance(),
                guard.minPlainStyleRatio(), guard.lengthRatioLowMargin(), guard.lengthRatioHighMargin()));
    }
}
