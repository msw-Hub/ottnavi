package com.ottnavi.llmdraft.dto.response;

import com.ottnavi.llmdraft.domain.rule.GuardWarning;
import com.ottnavi.llmdraft.service.model.DraftRecord;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 번역 초안 한 건(계약 OverviewDraft).
 *
 * @param draftId       초안 ID
 * @param titleId       작품 ID
 * @param mediaType     작품 구분(movie/tv)
 * @param title         작품 제목
 * @param original      번역한 영어 원문
 * @param translated    번역문
 * @param isGuardPassed 자동 검사에서 실패가 없는지(경고만 있어도 true). 실패한 초안(false)은 반영할 수 없다
 * @param guardFailures 검사에서 걸린 이유 코드들(통과면 빈 배열). EMPTY, TOO_LONG, LENGTH_RATIO_LOW, LENGTH_RATIO_HIGH,
 *                      HANGUL_RATIO_LOW, CONTAINS_URL, CONTAINS_MARKDOWN, CONTAINS_PREAMBLE, NUMBER_MISSING
 * @param warnings      의심 신호 코드들(없으면 빈 배열). 실패가 없고 경고만 있으면 "주의"다. SENTENCE_COUNT_MISMATCH, STYLE_NOT_PLAIN,
 *                      SUSPICIOUS_SPACING, LENGTH_RATIO_NEAR_LIMIT
 * @param model         번역에 쓴 모델 이름
 * @param createdAt     만든 시각(KST)
 * @param attempts      이 초안을 얻기까지의 총 시도 횟수(단건은 1, 일괄은 자동 재시도를 포함)
 * @param isEdited      관리자가 번역문을 직접 고쳤는지(고친 초안은 일괄 번역이 덮어쓰지 않는다)
 */
public record OverviewDraftResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "3f2b8c1e-5d4a-4c6b-9a7e-1b2c3d4e5f60") String draftId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1024") Long titleId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"movie", "tv"}, example = "movie") String mediaType,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "기생충") String title,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "All unemployed, Ki-taek's family takes peculiar interest in the wealthy Park family.") String original,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "전원 백수인 기택의 가족은 부유한 박 사장네 가족에게 남다른 관심을 갖게 된다.") String translated,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "true") boolean isGuardPassed,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> guardFailures,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<GuardWarning> warnings,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "gemini-3.5-flash-lite") String model,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-29T10:15:00+09:00") OffsetDateTime createdAt,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1") int attempts,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "false") boolean isEdited) {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	/** 서비스 계층 값을 응답으로 바꾼다 */
	public static OverviewDraftResponse from(DraftRecord draft) {
		return new OverviewDraftResponse(draft.draftId(), draft.titleId(), draft.mediaType(), draft.title(),
				draft.original(), draft.translated(), draft.isGuardPassed(), draft.guardFailures(), toWarnings(draft.warnings()), draft.model(),
				draft.createdAt().atZone(KST).toOffsetDateTime(), draft.attempts(), draft.isEdited());
	}

	/** 초안에 문자열로 남긴 경고 이름을 응답용 enum으로 바꾼다. 지금은 없는 이름(옛 버전이 남긴 값)은 건너뛴다 */
	private static List<GuardWarning> toWarnings(List<String> names) {
		List<GuardWarning> warnings = new ArrayList<>();
		for (String name : names) {
			try {
				warnings.add(GuardWarning.valueOf(name));
			} catch (IllegalArgumentException e) {
				// 알 수 없는 경고 이름은 응답에서 뺀다
			}
		}
		return warnings;
	}
}
