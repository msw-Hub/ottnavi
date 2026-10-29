/*
 * 관리자 "LLM 줄거리 번역" 화면(/admin/llm)의 순수 규칙·문구 모음이다.
 *
 * 화면(컴포넌트), 목업 서버(mocks/llmHandlers), 테스트가 같은 문구·판정을 써야 해서 따로 뒀다.
 * 실패 이유 코드·중단 사유 코드는 계약(docs/api/openapi.yaml)의 OverviewDraft.guardFailures,
 * BulkDraftRun.stopReason 설명과 같다. 코드가 계약에 새로 생기면 여기 표도 같이 고친다.
 */
import type {
  ApplyOverviewDraftItemReason,
  MediaType,
  BulkDraftRun,
  BulkDraftRunStopReason,
  OverviewDraft,
  OverviewDraftStatus,
  OverviewDraftWarning,
} from '@/api/generated/model'

// 표본으로 뽑는 초안 수(사용자 결정 Q4: 10편 고정)
export const SAMPLE_SIZE = 10

// 목록 한 쪽의 크기(검사 통과·실패 초안, 번역 후보)
export const LIST_PAGE_SIZE = 20

// 일괄 번역 한 번에 요청할 수 있는 편수 범위(계약 createBulkOverviewDrafts count 1~100)
export const BULK_COUNT_MIN = 1
export const BULK_COUNT_MAX = 100
export const BULK_COUNT_DEFAULT = 20

// 진행 중인 일괄 번역을 다시 확인하는 간격(밀리초)
export const BULK_POLL_INTERVAL_MS = 5000

// 초안 검사 실패 이유 코드 → 화면 라벨(짧은 이름)과 설명(긴 문장). 모르는 코드는 getGuardFailureLabel이 처리한다
export const GUARD_FAILURE_LABELS: Record<string, { label: string; description: string }> = {
  EMPTY: { label: '번역문 없음', description: '번역문이 비어 있습니다.' },
  TOO_LONG: { label: '너무 김(1,000자 초과)', description: '번역문이 1,000자를 넘습니다.' },
  LENGTH_RATIO_LOW: {
    label: '원문보다 너무 짧음',
    description: '원문 길이의 30%에 못 미칩니다. 내용이 빠졌을 수 있습니다.',
  },
  LENGTH_RATIO_HIGH: {
    label: '원문보다 너무 김',
    description: '원문 길이의 2배를 넘습니다. 없는 내용이 덧붙었을 수 있습니다.',
  },
  HANGUL_RATIO_LOW: {
    label: '한글 비율 낮음',
    description: '글자 중 한글이 60%에 못 미칩니다. 번역이 덜 됐을 수 있습니다.',
  },
  CONTAINS_URL: { label: '주소 포함', description: '번역문에 인터넷 주소가 들어 있습니다.' },
  CONTAINS_MARKDOWN: {
    label: '서식 기호 포함',
    description: '번역문에 `**`, `#` 같은 서식 기호가 들어 있습니다.',
  },
  CONTAINS_PREAMBLE: {
    label: '머리말 포함',
    description: '"번역:", "Here is" 같은 군더더기 말이 들어 있습니다.',
  },
  NUMBER_MISSING: {
    label: '숫자 빠짐',
    description: '원문의 숫자(연도·나이 등)가 번역문에 없습니다.',
  },
}

/** 실패 이유 코드 하나의 라벨·설명을 돌려준다. 모르는 코드(서버가 먼저 늘린 경우)는 "기타 검사 실패({코드})"다. */
export function getGuardFailureLabel(code: string): { label: string; description: string } {
  const known = Object.hasOwn(GUARD_FAILURE_LABELS, code) ? GUARD_FAILURE_LABELS[code] : undefined
  if (known) return known
  return {
    label: `기타 검사 실패(${code})`,
    description: `알 수 없는 검사 실패 코드입니다: ${code}`,
  }
}

// 일괄 번역이 멈춘 이유 → 안내 문장. dailyLimit은 DAILY_LIMIT 문장에 들어간다
export function getBulkStopMessage(
  reason: NonNullable<BulkDraftRunStopReason>,
  dailyLimit: number | null,
): string {
  switch (reason) {
    case 'DISABLED':
      return '서버에 Gemini 키가 없어 멈췄습니다. 키를 설정한 뒤 다시 시작해 주세요.'
    case 'RATE_LIMITED':
      return '분당 호출 상한에 걸려 멈췄습니다. 1분쯤 뒤에 다시 시작하면 남은 작품부터 이어서 번역합니다.'
    case 'DAILY_LIMIT':
      return `오늘 호출 상한(${dailyLimit ?? '-'}회)을 모두 써서 멈췄습니다. 상한이 초기화된 뒤 다시 시작해 주세요.`
    case 'DRAFT_LIMIT':
      return '대기 중인 초안이 가득 차서(500개) 멈췄습니다. 초안을 반영하거나 삭제한 뒤 다시 시작해 주세요.'
    case 'CALL_FAILED_REPEATED':
      return '번역 호출이 3번 연달아 실패해 멈췄습니다. 잠시 뒤 다시 시작해 주세요. 계속 실패하면 서버 로그를 확인해 주세요.'
    case 'SERVER_RESTARTED':
      return '실행 중에 서버가 다시 시작되어 멈췄습니다. 그때까지 만든 초안은 남아 있습니다. 다시 시작하면 남은 작품을 이어서 번역합니다.'
  }
}

// 반영 때 건너뛴 이유 → 라벨
export const APPLY_SKIP_REASON_LABELS: Record<NonNullable<ApplyOverviewDraftItemReason>, string> = {
  DRAFT_NOT_FOUND: '초안이 없음(보관 기간이 지났거나 이미 처리됨)',
  GUARD_FAILED: '검사 실패 초안',
  ORIGINAL_CHANGED: '번역한 뒤 원문이 바뀌었거나 이미 한국어임',
}

// 구분(mediaType) 화면 글자
export const MEDIA_TYPE_LABELS: Record<MediaType, string> = {
  movie: '영화',
  tv: '드라마',
}

/**
 * 일괄 번역 진행률을 계산한다.
 * 처리한 수 = 초안을 만든 수 + 만들지 못한 수(guardFailedCount는 doneCount 안에 이미 들어 있어 더하지 않는다).
 * 막대 너비 비율(0~1)은 total이 0이면 0이다.
 */
export function getBulkProgress(
  bulkRun: Pick<BulkDraftRun, 'total' | 'doneCount' | 'failedCount'>,
): {
  processed: number
  ratio: number
} {
  const processed = bulkRun.doneCount + bulkRun.failedCount
  const ratio = bulkRun.total > 0 ? Math.min(1, processed / bulkRun.total) : 0
  return { processed, ratio }
}

// 하루 사용량 단계: 정상 / 곧 상한(80% 이상) / 도달(100% 이상)
export type UsageLevel = 'normal' | 'nearLimit' | 'reached'

/** 오늘 사용량이 상한에 얼마나 가까운지 단계로 알려 준다. 79%는 정상, 80%는 "곧 상한", 100%는 "도달"이다. */
export function getUsageLevel(usedToday: number, dailyLimit: number): UsageLevel {
  if (dailyLimit <= 0) return 'normal'
  if (usedToday >= dailyLimit) return 'reached'
  // 곱셈으로 비교해 80% 경계에서 소수 계산 오차가 나지 않게 한다(usedToday / dailyLimit >= 0.8과 같다)
  if (usedToday * 100 >= dailyLimit * 80) return 'nearLimit'
  return 'normal'
}

/** 오늘 남은 호출 수(0 미만으로 내려가지 않는다). */
export function getRemainingCalls(usedToday: number, dailyLimit: number): number {
  return Math.max(0, dailyLimit - usedToday)
}

// ===== v2: 검사 등급(성공 / 주의 / 실패), 주의 라벨, 대량 되돌리기 규칙 =====

/**
 * 초안의 검사 등급을 응답 필드로 계산한다. 응답에는 등급 필드가 없다(계약 OverviewDraft 설명).
 * 1. 실패: isGuardPassed가 false(걸린 이유는 guardFailures). 실패는 경고가 있어도 실패다.
 * 2. 주의: 실패는 없고 warnings가 하나라도 있음. 사람이 읽고 승인해야 반영된다.
 * 3. 성공: 실패도 경고도 없음.
 */
export function getDraftGrade(
  draft: Pick<OverviewDraft, 'isGuardPassed' | 'guardFailures' | 'warnings'>,
): OverviewDraftStatus {
  // 1. 실패 판정. guardFailures가 있으면 isGuardPassed가 true여도 실패로 본다(어긋난 응답이 와도 반영 가능으로 보이지 않게)
  if (!draft.isGuardPassed || draft.guardFailures.length > 0) return 'FAILED'
  // 2. 주의 판정
  if (draft.warnings.length > 0) return 'WARNING'
  // 3. 나머지는 성공
  return 'PASSED'
}

// 주의 이유 코드 → 화면 라벨(짧은 이름)과 설명(긴 문장). 계약 OverviewDraftWarning 설명을 쉬운 말로 옮겼다.
// 키를 계약 타입으로 묶어, 계약에 코드가 늘면 여기서 컴파일 오류가 나서 라벨을 빠뜨리지 않는다
export const WARNING_LABELS: Record<OverviewDraftWarning, { label: string; description: string }> =
  {
    SENTENCE_COUNT_MISMATCH: {
      label: '문장 수 차이',
      description: '원문과 문장 수가 2개 이상 다릅니다. 빠지거나 합쳐진 문장이 없는지 보세요.',
    },
    STYLE_NOT_PLAIN: {
      label: '말투 섞임',
      description:
        '"~다." 꼴이 아닌 문장이 많습니다(예: ~합니다, ~해요). 줄거리 말투로 맞춰 주세요.',
    },
    SUSPICIOUS_SPACING: {
      label: '띄어쓰기 의심',
      description:
        '한 글자 낱말 뒤가 어색하게 떨어져 있습니다(예: "그 는"). 띄어쓰기를 확인하세요.',
    },
    LENGTH_RATIO_NEAR_LIMIT: {
      label: '길이 경계',
      description:
        '원문 대비 길이가 실패 기준에 가깝습니다. 내용이 빠지거나 덧붙지 않았는지 보세요.',
    },
  }

/** 주의 이유 코드 하나의 라벨·설명을 돌려준다. 모르는 코드(서버가 먼저 늘린 경우)는 "기타 주의({코드})"다. */
export function getWarningLabel(code: string): { label: string; description: string } {
  const known = Object.hasOwn(WARNING_LABELS, code)
    ? WARNING_LABELS[code as OverviewDraftWarning]
    : undefined
  if (known) return known
  return { label: `기타 주의(${code})`, description: `알 수 없는 주의 코드입니다: ${code}` }
}

// 단건 번역(다시 번역·번역 후보 만들기) 결과를 알리는 토스트 한 건
export interface DraftGradeToast {
  level: 'success' | 'warning'
  text: string
}

/**
 * 번역 초안을 만든 결과를 알리는 토스트 문장을 만든다. verb는 "다시 번역했습니다" 같은 서술이다.
 * 성공은 초록 토스트, 주의·실패는 노랑 토스트로 이유 라벨과 가야 할 탭 이름을 함께 알린다(색만으로 알리지 않는다).
 */
export function getDraftGradeToast(draft: OverviewDraft, verb: string): DraftGradeToast {
  const grade = getDraftGrade(draft)
  const head = `「${draft.title}」을 ${verb}.`
  if (grade === 'PASSED') return { level: 'success', text: `${head} 검사 결과: 성공` }
  if (grade === 'WARNING') {
    const labels = draft.warnings.map((code) => getWarningLabel(code).label).join(', ')
    return {
      level: 'warning',
      text: `${head} 검사 결과: 주의(${labels}). [승인 대기] 탭에서 확인하세요.`,
    }
  }
  const labels = draft.guardFailures.map((code) => getGuardFailureLabel(code).label).join(', ')
  return {
    level: 'warning',
    text: `${head} 검사 결과: 실패(${labels}). [실패] 탭에서 확인하세요.`,
  }
}

/** 일괄 번역에서 만든 초안 중 성공 수 = 만든 수 − 주의 − 실패(0 미만으로 내려가지 않는다). */
export function getBulkSuccessCount(
  bulkRun: Pick<BulkDraftRun, 'doneCount' | 'warningCount' | 'guardFailedCount'>,
): number {
  return Math.max(0, bulkRun.doneCount - bulkRun.warningCount - bulkRun.guardFailedCount)
}

// 대량 되돌리기: "몇 개월 지난 번역" 입력 범위(계약 제한은 아니고 화면 정책이다). 기본 5개월은 6개월 보관 기준보다 한 달 앞서 정리한다
export const REVERT_MONTHS_MIN = 1
export const REVERT_MONTHS_MAX = 24
export const REVERT_MONTHS_DEFAULT = 5

/** 입력 문자열을 개월 수로 바꾼다. 1~24의 정수가 아니면 null이다. */
export function parseRevertMonths(text: string): number | null {
  if (text.trim() === '') return null
  const months = Number(text)
  if (!Number.isInteger(months)) return null
  if (months < REVERT_MONTHS_MIN || months > REVERT_MONTHS_MAX) return null
  return months
}

/**
 * 기준 시각(ISO 문자열)을 계산한다: now에서 months개월 앞선 시각. 이 시각보다 먼저 반영한 번역이 대상이다.
 * 달 수만 빼면 3월 31일 − 1개월이 "2월 31일"이 되어 3월 초로 넘어가므로, 날짜를 그 달의 마지막 날로 맞춘다.
 * 미리보기와 확정 요청이 같은 값을 보내도록, 호출하는 쪽이 이 값을 한 번 만들어 둘 다에 쓴다.
 */
export function getRevertCutoff(months: number, now: Date): string {
  // 1. 목표 달의 1일로 옮긴다(날짜가 넘치지 않게)
  const target = new Date(now.getTime())
  target.setDate(1)
  target.setMonth(target.getMonth() - months)
  // 2. 원래 날짜와 목표 달의 마지막 날 중 작은 값으로 날짜를 맞춘다
  const lastDay = new Date(target.getFullYear(), target.getMonth() + 1, 0).getDate()
  target.setDate(Math.min(now.getDate(), lastDay))
  return target.toISOString()
}
