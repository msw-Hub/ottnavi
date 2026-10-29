package com.ottnavi.llmdraft.service.model;

import java.time.Instant;
import java.util.List;

/**
 * Redis에 보관하는 번역 초안 한 건(작품당 1개). 반영할 때 원문(original)이 DB의 현재 줄거리와 같은지 다시 확인한다.
 * warnings·attempts·isEdited가 없는 옛 JSON도 읽을 수 있다(없으면 경고 없음, 시도 1회, 수정 안 함으로 본다).
 *
 * @param draftId       초안 ID(무작위 UUID 문자열)
 * @param titleId       작품 ID
 * @param mediaType     작품 구분(movie/tv)
 * @param title         작품 제목(목록에 보여 주려고 함께 저장)
 * @param original      번역한 영어 원문(번역 시점의 DB 줄거리)
 * @param translated    번역문
 * @param isGuardPassed 자동 검사에서 실패가 없는지(경고만 있어도 true)
 * @param guardFailures 걸린 이유 코드들(GuardFailure 이름, 없으면 빈 목록)
 * @param warnings      의심 신호 코드들(GuardWarning 이름, 없으면 빈 목록)
 * @param model         번역에 쓴 모델 이름
 * @param createdAt     만든 시각
 * @param attempts      이 초안을 얻기까지의 총 시도 횟수(단건은 1, 일괄은 재시도를 포함)
 * @param isEdited      관리자가 번역문을 직접 고쳤는지. true면 일괄 번역이 같은 작품을 다시 만들어 덮어쓰지 않는다
 */
public record DraftRecord(String draftId, Long titleId, String mediaType, String title, String original,
                          String translated, boolean isGuardPassed, List<String> guardFailures,
                          List<String> warnings, String model, Instant createdAt, int attempts, boolean isEdited) {

    /** 옛 JSON에는 warnings·attempts가 없어 null·0으로 들어오므로 기본값으로 채운다 */
    public DraftRecord {
        if (guardFailures == null) {
            guardFailures = List.of();
        }
        if (warnings == null) {
            warnings = List.of();
        }
        if (attempts < 1) {
            attempts = 1;
        }
    }

    /** 사람이 수정하지 않은 초안(번역이 막 만든 값)을 만드는 간단한 생성자 */
    public DraftRecord(String draftId, Long titleId, String mediaType, String title, String original,
                       String translated, boolean isGuardPassed, List<String> guardFailures, List<String> warnings,
                       String model, Instant createdAt, int attempts) {
        this(draftId, titleId, mediaType, title, original, translated, isGuardPassed, guardFailures, warnings, model,
                createdAt, attempts, false);
    }

    /** 등급: 실패가 있으면 FAILED, 실패 없이 경고가 있으면 WARNING, 아니면 PASSED */
    public DraftGrade grade() {
        if (!isGuardPassed) {
            return DraftGrade.FAILED;
        }
        if (!warnings.isEmpty()) {
            return DraftGrade.WARNING;
        }
        return DraftGrade.PASSED;
    }

    /** 총 시도 횟수를 바꾼 새 값을 만든다(record는 불변이라 복사해서 쓴다) */
    public DraftRecord withAttempts(int newAttempts) {
        return new DraftRecord(draftId, titleId, mediaType, title, original, translated, isGuardPassed, guardFailures,
                warnings, model, createdAt, newAttempts, isEdited);
    }

    /**
     * 관리자가 번역문을 고친 새 값을 만든다. 번역문과 자동 검사 결과(통과 여부·실패·경고)를 새로 채우고 isEdited를 true로 둔다.
     * 원문·작품·만든 시각은 그대로다.
     */
    public DraftRecord withEditedTranslation(String newTranslated, boolean newGuardPassed, List<String> newFailures,
                                             List<String> newWarnings) {
        return new DraftRecord(draftId, titleId, mediaType, title, original, newTranslated, newGuardPassed,
                newFailures, newWarnings, model, createdAt, attempts, true);
    }
}
