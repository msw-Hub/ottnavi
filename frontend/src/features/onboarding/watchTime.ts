/*
 * 월 시청 시간 계산 도우미다(SCR-07, PRD 5.4 "월 시청 가능 시간").
 *
 * 사용자는 시청 시간을 세 가지로 말할 수 있다: 프리셋(가볍게·보통·많이) / "주 N회 × 회당 M시간" 환산 / 직접 입력(분).
 * 계산 엔진은 마지막에 "월 몇 분"이라는 숫자 하나만 받으므로, 어떤 방식이든 분으로 바꾸는 순수 함수가 필요하다.
 * 순수 함수(같은 입력이면 항상 같은 출력)로 둔 이유: 화면 없이 테스트할 수 있고, 폼 검증(schema.ts)과 저장 요청 만들기가 같은 계산을 쓴다.
 * 시간 표시는 src/lib/format.ts의 formatMinutes만 거친다(frontend.md 화면 표시 규칙).
 */
import { formatMinutes } from '@/lib/format'
import type { WatchPreset } from '@/features/onboarding/mockTypes'

// 한 달을 몇 주로 볼지. 365일 / 12개월 / 7일 ≈ 4.33 (PRD 5.4)
export const WEEKS_PER_MONTH = 4.33

// 월 시청 시간의 최소값(분). 이보다 작으면 긴 시즌은 시작도 못 하고 영화 한 편 보기도 어렵다(PRD FR-10, 2026-10-09 결정)
export const MIN_MONTHLY_WATCH_MINUTES = 120

// 직접 입력·환산 결과의 상한(분). 한 달은 최대 44,640분(31일)이라 현실적인 입력 실수를 막는 선이다
export const MAX_MONTHLY_WATCH_MINUTES = 44_640

// 프리셋 값(분). 가볍게 8시간 / 보통 15시간 / 많이 30시간 (PRD 5.4)
export const WATCH_TIME_PRESETS = {
  LIGHT: 8 * 60,
  NORMAL: 15 * 60,
  HEAVY: 30 * 60,
} as const

// 프리셋으로 고를 수 있는 값들(CUSTOM 제외). 화면의 선택지 순서이기도 하다
export type SelectablePreset = keyof typeof WATCH_TIME_PRESETS

// 프리셋별 화면 글자
export const WATCH_PRESET_LABELS: Record<WatchPreset, string> = {
  LIGHT: '가볍게',
  NORMAL: '보통',
  HEAVY: '많이',
  CUSTOM: '직접 입력',
}

/**
 * "주 N회 × 회당 M시간"을 월 시청 시간(분)으로 바꾼다: N × M × 60 × 4.33, 분 단위로 반올림.
 *
 * 0 이하이거나 숫자가 아닌 값(NaN, Infinity)이 들어오면 0을 돌려준다.
 * 폼 입력 중에는 칸이 비어 NaN이 되는 순간이 있는데, 그때 "NaN분"이 화면에 보이지 않게 하기 위함이다.
 * 0은 최소값(120분) 검사에서 걸러지므로 잘못된 값이 저장되지는 않는다.
 */
export function convertWeeklyToMonthlyMinutes(timesPerWeek: number, hoursPerTime: number): number {
  if (!Number.isFinite(timesPerWeek) || !Number.isFinite(hoursPerTime)) return 0
  if (timesPerWeek <= 0 || hoursPerTime <= 0) return 0
  return Math.round(timesPerWeek * hoursPerTime * 60 * WEEKS_PER_MONTH)
}

/**
 * 환산 결과를 사용자에게 보여 줄 한 줄로 만든다. 예: 주 3회 × 회당 2시간 → "월 약 26시간"
 * 계산 과정을 보여 주는 이유: 환산이 어림값이라는 것(4.33주 기준)을 사용자가 알고 직접 고칠 수 있게 하기 위함이다.
 */
export function describeWeeklyConversion(timesPerWeek: number, hoursPerTime: number): string {
  const minutes = convertWeeklyToMonthlyMinutes(timesPerWeek, hoursPerTime)
  return `월 약 ${formatMinutes(minutes)}`
}

// 폼에서 시청 시간을 입력받는 값(schema.ts의 watchTime과 같은 모양). 방식(mode)마다 쓰는 칸이 다르다
export interface WatchTimeInput {
  mode: 'preset' | 'weekly' | 'direct'
  preset?: SelectablePreset | null // mode가 preset일 때(아직 고르지 않은 라디오는 폼 값이 null이다)
  timesPerWeek?: number // mode가 weekly일 때
  hoursPerTime?: number // mode가 weekly일 때
  directMinutes?: number // mode가 direct일 때
}

/**
 * 폼의 시청 시간 입력을 월 시청 시간(분)으로 바꾼다. 필요한 칸이 비어 있으면 null이다(검증 오류는 schema.ts가 낸다).
 */
export function resolveMonthlyWatchMinutes(input: WatchTimeInput): number | null {
  switch (input.mode) {
    case 'preset':
      return input.preset ? WATCH_TIME_PRESETS[input.preset] : null
    case 'weekly': {
      if (input.timesPerWeek === undefined || input.hoursPerTime === undefined) return null
      const minutes = convertWeeklyToMonthlyMinutes(input.timesPerWeek, input.hoursPerTime)
      return minutes > 0 ? minutes : null
    }
    case 'direct':
      return input.directMinutes !== undefined && Number.isFinite(input.directMinutes)
        ? input.directMinutes
        : null
  }
}

/**
 * 저장 요청에 넣을 프리셋 값을 정한다. 프리셋을 골랐으면 그 값이고, 환산·직접 입력은 CUSTOM이다(PRD 5.4, 11절 18번 추천안).
 */
export function resolveWatchPreset(input: WatchTimeInput): WatchPreset {
  return input.mode === 'preset' && input.preset ? input.preset : 'CUSTOM'
}
