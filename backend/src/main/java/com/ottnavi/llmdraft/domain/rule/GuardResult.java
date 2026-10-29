package com.ottnavi.llmdraft.domain.rule;

import java.util.List;

/**
 * 자동 검사 결과. 실패가 있으면 "실패", 실패는 없고 경고만 있으면 "주의", 둘 다 없으면 "성공"이다.
 *
 * @param passed   실패가 없는지(경고만 있어도 true)
 * @param failures 걸린 이유들(없으면 빈 목록)
 * @param warnings 의심 신호들(없으면 빈 목록)
 */
public record GuardResult(boolean passed, List<GuardFailure> failures, List<GuardWarning> warnings) {
}
