package com.ottnavi.llmdraft.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 줄거리 번역 초안 설정(app.llm.limits·draft·bulk·guard). 모델·타임아웃·최대 토큰은 infra의 LlmProperties가 읽는다.
 *
 * @param limits 서버 호출 상한
 * @param draft  초안 보관
 * @param bulk   일괄 번역
 * @param guard  자동 검사 기준
 */
@ConfigurationProperties(prefix = "app.llm")
public record LlmDraftProperties(
        @DefaultValue Limits limits,
        @DefaultValue Draft draft,
        @DefaultValue Bulk bulk,
        @DefaultValue Guard guard) {

    /**
     * @param requestsPerMinute  분당 호출 상한(고정 1분 창)
     * @param requestsPerDay     하루 호출 상한(태평양 시간 날짜 기준)
     * @param titleCooldownSeconds 같은 작품을 다시 번역하기까지 기다리는 시간(초)
     */
    public record Limits(@DefaultValue("12") int requestsPerMinute, @DefaultValue("400") int requestsPerDay,
                         @DefaultValue("60") int titleCooldownSeconds) {
    }

    /**
     * @param ttl        초안 보관 기간
     * @param maxPending 대기 초안 총량 상한
     */
    public record Draft(@DefaultValue("7d") Duration ttl, @DefaultValue("500") int maxPending) {
    }

    /**
     * @param maxSize               한 번에 요청할 수 있는 작품 수
     * @param callInterval          호출 사이 대기
     * @param busyRetryWait         구글 서버 혼잡(503)으로 실패했을 때 같은 작품을 한 번 다시 호출하기 전 대기
     * @param maxConsecutiveFailures 호출 실패가 이만큼 연속되면 멈춘다
     * @param failRetryMax          자동 검사 실패(FAILED)로 끝난 작품을 다시 번역하는 최대 횟수(첫 시도 제외)
     * @param warningRetryMax       주의(WARNING)로 끝난 작품을 다시 번역하는 최대 횟수(첫 시도 제외)
     */
    public record Bulk(@DefaultValue("100") int maxSize, @DefaultValue("2s") Duration callInterval,
                        @DefaultValue("30s") Duration busyRetryWait, @DefaultValue("3") int maxConsecutiveFailures,
                        @DefaultValue("2") int failRetryMax, @DefaultValue("1") int warningRetryMax) {
    }

    /**
     * @param minHangulRatio 한글 비율 하한
     * @param minLengthRatio 길이 비율 하한
     * @param maxLengthRatio 길이 비율 상한
     * @param maxLength      번역문 최대 글자 수
     * @param sentenceCountTolerance 문장 수 차이 허용치(넘으면 경고)
     * @param minPlainStyleRatio     "~다." 꼴 문장 비율 하한(미만이면 경고)
     * @param lengthRatioLowMargin   길이 비율 하한 위 이만큼까지 경고
     * @param lengthRatioHighMargin  길이 비율 상한 아래 이만큼부터 경고
     */
    public record Guard(@DefaultValue("0.6") double minHangulRatio, @DefaultValue("0.3") double minLengthRatio,
                        @DefaultValue("2.0") double maxLengthRatio, @DefaultValue("1000") int maxLength,
                        @DefaultValue("1") int sentenceCountTolerance, @DefaultValue("0.8") double minPlainStyleRatio,
                        @DefaultValue("0.1") double lengthRatioLowMargin, @DefaultValue("0.4") double lengthRatioHighMargin) {
    }
}
