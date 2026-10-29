package com.ottnavi.llmdraft.dto.response;

import com.ottnavi.llmdraft.service.model.CandidatePageView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 번역 화면 상단 요약(계약 OverviewTranslateSummary).
 *
 * @param pendingTotal   번역 대기 중인 영어 줄거리 작품의 총수
 * @param usedToday      오늘(태평양 시간 날짜 기준) Gemini를 호출한 수
 * @param dailyLimit     하루 호출 상한
 * @param perMinuteLimit 분당 호출 상한
 * @param model          사용 중인 모델 이름
 * @param isEnabled      번역 기능을 쓸 수 있는지(서버에 Gemini 키가 설정돼 있는지)
 * @param translatedCount    관리자가 반영한 번역 줄거리 작품 수
 * @param oldestTranslatedAt 가장 오래된 번역의 반영 시각(KST). 번역이 없으면 null
 */
public record LlmSummaryResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2512") long pendingTotal,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "37") long usedToday,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "400") int dailyLimit,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "12") int perMinuteLimit,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "gemini-3.5-flash-lite") String model,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "true") boolean isEnabled,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "37") long translatedCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "2026-10-29T10:00:00+09:00") OffsetDateTime oldestTranslatedAt) {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	/** 서비스 계층 값을 응답으로 바꾼다 */
	public static LlmSummaryResponse from(CandidatePageView view) {
		OffsetDateTime oldestTranslatedAt = null;
		if (view.translation().oldestTranslatedAt() != null) {
			oldestTranslatedAt = view.translation().oldestTranslatedAt().atZone(KST).toOffsetDateTime();
		}
		return new LlmSummaryResponse(view.pendingTotal(), view.usage().usedToday(), view.usage().dailyLimit(),
				view.usage().perMinuteLimit(), view.model(), view.isEnabled(), view.translation().translatedCount(),
				oldestTranslatedAt);
	}
}
