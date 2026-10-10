/*
 * 플랜 결과 화면(SCR-10)의 화면 글자와 표시용 계산을 모은 파일이다.
 *
 * 컴포넌트 파일에서 문구 표(Record)를 export하면 개발 서버의 빠른 새로고침 규칙(react-refresh)에 걸려 분리했다.
 * 여기의 함수는 모두 "보여 주기 위한 정리"(달 이름 만들기, 나눠 보는 시즌 묶기)이며 계산 결과를 바꾸지 않는다.
 */
import {
  BanIcon,
  CircleQuestionMarkIcon,
  ClockIcon,
  WalletIcon,
  type LucideIcon,
} from 'lucide-react'
import type {
  PlanAssignment,
  PlanDraft,
  PlanType,
  UnscheduledReason,
} from '@/features/plan/mockTypes'

// 플랜 유형 이름(PRD 11절 35번)
export const PLAN_TYPE_LABELS: Record<PlanType, string> = {
  RECOMMENDED: '추천형',
  SAVER: '절약형',
  SIMPLE: '간편형',
}

// 플랜 유형별 한 줄 설명. 기준이 무엇인지 알아야 고를 수 있어 카드에 항상 보인다.
// 내부 계산 기준(점수 하한 70% 등)은 소비자가 고를 때 쓸 정보가 아니라서 화면 문구에는 쓰지 않는다
// 본문(text)과 보조 문구(note)를 나눠 둔 이유: 절약형은 한 줄로 이으면 너무 길어서, 보조 문구를 다음 줄에 약한 글자색으로 따로 보인다
export const PLAN_TYPE_DESCRIPTIONS: Record<PlanType, { text: string; note?: string }> = {
  RECOMMENDED: { text: '꼭 볼 작품을 먼저, 가장 많이 볼 수 있는 조합이에요' },
  SAVER: {
    text: '꼭 볼 작품은 지키면서 가장 저렴한 조합이에요',
    note: '(보고 싶은 작품 일부는 못 볼 수 있어요)',
  },
  SIMPLE: { text: '새로 가입하는 횟수가 가장 적은 조합이에요' },
}

// 남은 작품의 이유별 글자·보조 설명·아이콘. 아이콘만으로 뜻을 전하지 않도록 글자를 항상 함께 보인다
export const UNSCHEDULED_REASON_DISPLAY: Record<
  UnscheduledReason,
  { label: string; hint: string; icon: LucideIcon }
> = {
  BUDGET: { label: '예산 부족', hint: '월 예산 안에서는 담지 못했어요', icon: WalletIcon },
  TIME: { label: '시청 시간 부족', hint: '3개월 동안 볼 시간이 모자랐어요', icon: ClockIcon },
  UNKNOWN: {
    label: '제공처 정보 없음',
    hint: '어디서 볼 수 있는지 확인된 정보가 없어요(모름)',
    icon: CircleQuestionMarkIcon,
  },
  NO_PROVIDER: {
    label: '국내 서비스에 없음',
    hint: '확인된 범위의 국내 OTT 7곳에서 찾지 못했어요',
    icon: BanIcon,
  },
}

/**
 * 'YYYY-MM'을 "10월"로 바꾼다. 3개월 안에서는 월만으로 충분히 구분되지만, 연도가 바뀌는 구간(12월 → 1월)을 위해
 * 호출부가 첫 달과 연도가 다르면 withYear를 켜서 "2027년 1월"로 보이게 한다. 해석할 수 없으면 입력을 그대로 돌려준다.
 */
export function formatMonthLabel(month: string, withYear = false): string {
  const matched = /^(\d{4})-(\d{2})$/.exec(month)
  if (!matched) return month
  const monthNumber = Number(matched[2])
  return withYear ? `${matched[1]}년 ${monthNumber}월` : `${monthNumber}월`
}

// 나눠 보는 시즌 한 건: 같은 작품(planItemId)의 조각들을 달 순서대로 모은 것
export interface SplitSeasonSummary {
  planItemId: number // 플랜 항목 ID
  title: string // 작품 제목
  seasonNumber: number | null // 시즌 번호
  segments: { monthIndex: number; month: string; ottServiceId: number; segmentIndex: number }[] // 달 순서대로의 조각
}

/**
 * 조각이 둘 이상(segmentCount > 1)인 작품을 찾아 달 순서대로 묶는다 → "나눠 보는 시즌" 요약 박스의 재료.
 * 단순히 모든 배정을 훑으면 같은 작품이 달마다 따로 나오는데, 사용자는 "이 시즌이 몇 월에 어디서 이어지는지"를 한눈에 보고 싶어 한다.
 */
export function collectSplitSeasons(draft: PlanDraft): SplitSeasonSummary[] {
  const byItem = new Map<number, SplitSeasonSummary>()
  for (const month of draft.months) {
    for (const assignment of month.assignments) {
      if (assignment.segmentCount <= 1) continue
      const summary = byItem.get(assignment.planItemId) ?? {
        planItemId: assignment.planItemId,
        title: assignment.title,
        seasonNumber: assignment.seasonNumber,
        segments: [],
      }
      summary.segments.push({
        monthIndex: month.monthIndex,
        month: month.month,
        ottServiceId: assignment.ottServiceId,
        segmentIndex: assignment.segmentIndex,
      })
      byItem.set(assignment.planItemId, summary)
    }
  }
  return [...byItem.values()].map((summary) => ({
    ...summary,
    segments: [...summary.segments].sort((a, b) => a.monthIndex - b.monthIndex),
  }))
}

// 작품 이름(+시즌). 영화나 시즌 없는 작품은 제목만, 시즌이 있으면 "제목 시즌 N"이다
export function formatAssignmentTitle(item: {
  title: string
  seasonNumber: number | null
}): string {
  return item.seasonNumber === null ? item.title : `${item.title} 시즌 ${item.seasonNumber}`
}

// 우선순위 정렬 순서(꼭 볼 작품이 먼저). 달 카드 안의 작품 순서에 쓴다
const PRIORITY_ORDER: Record<PlanAssignment['priority'], number> = { MUST: 0, WANT: 1, MAYBE: 2 }

export function comparePriority(
  a: { priority: PlanAssignment['priority'] },
  b: { priority: PlanAssignment['priority'] },
) {
  return PRIORITY_ORDER[a.priority] - PRIORITY_ORDER[b.priority]
}
