/*
 * 플랜 변경 내역 화면(SCR-11)의 화면 글자·아이콘과 표시용 계산을 모은 파일이다.
 *
 * 컴포넌트 파일에서 문구 표(Record)를 export하면 개발 서버의 빠른 새로고침 규칙(react-refresh)에 걸려 planDisplay.ts처럼 분리했다.
 * 이유·변화 종류는 색이 아니라 아이콘 + 글자로 구분한다(frontend.md "색만으로 구분하지 않는다"). 그래서 표마다 label과 icon을 함께 둔다.
 */
import {
  ArrowLeftRightIcon,
  BadgeDollarSignIcon,
  CalendarRangeIcon,
  CircleCheckBigIcon,
  LogInIcon,
  LogOutIcon,
  MinusIcon,
  PlusIcon,
  type LucideIcon,
} from 'lucide-react'
import type { PlanChangeReasonType, PlanTitleChangeType } from '@/api/generated/model'

// 바뀐 이유 4종의 글자·아이콘. 접힌 항목의 이유 칩과 펼친 "바뀐 이유" 목록이 같은 표를 쓴다
export const CHANGE_REASON_DISPLAY: Record<
  PlanChangeReasonType,
  { label: string; icon: LucideIcon }
> = {
  PRICE_CHANGED: { label: '가격 변경', icon: BadgeDollarSignIcon },
  TITLE_LEFT_SERVICE: { label: '서비스에서 빠짐', icon: LogOutIcon },
  TITLE_JOINED_SERVICE: { label: '새로 들어옴', icon: LogInIcon },
  MARKED_WATCHED: { label: '다 봤음 체크', icon: CircleCheckBigIcon },
}

// 작품 변화 4종의 글자·아이콘
export const TITLE_CHANGE_DISPLAY: Record<
  PlanTitleChangeType,
  { label: string; icon: LucideIcon }
> = {
  ADDED: { label: '추가', icon: PlusIcon },
  REMOVED: { label: '제외', icon: MinusIcon },
  MOVED_MONTH: { label: '달 이동', icon: CalendarRangeIcon },
  CHANGED_SERVICE: { label: '서비스 변경', icon: ArrowLeftRightIcon },
}

/**
 * 이전 → 이후 금액 차이를 방향(줄었음/늘었음/같음)과 크기로 나눈다.
 * 화면은 방향을 아이콘 + "줄었어요/늘었어요" 글자로 보인다. 빨강·초록으로 좋고 나쁨을 칠하지 않는 이유:
 * 결과를 단정적으로 강조하지 않고("확인된 범위" 원칙), 색 없이도 뜻이 전해지게 하기 위함이다.
 */
export function getAmountDelta(
  previous: number,
  current: number,
): { direction: 'down' | 'up' | 'same'; amount: number } {
  const diff = current - previous
  if (diff === 0) return { direction: 'same', amount: 0 }
  return { direction: diff < 0 ? 'down' : 'up', amount: Math.abs(diff) }
}
