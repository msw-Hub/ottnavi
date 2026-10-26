import { CalendarClockIcon } from 'lucide-react'
import { AdminStatusPill } from '@/features/admin/components/AdminStatusPill'

interface PhaseLaterTagProps {
  phase?: '2단계' | '3단계' // 이 기능이 들어올 개발 단계. 기본 2단계
  label?: string // 태그 글자를 바꿀 때. 기본 "{phase}부터" (예: "2단계부터")
  className?: string // 놓이는 자리에 맞춘 여백 등을 덧붙일 때만 쓴다
}

/**
 * MVP에서 아직 쓰지 않는 기능 자리에 붙이는 "2단계부터" 태그다(Task 029).
 * 쓰는 곳: 번들·광고형 OTT 구독상품, 수집 범위 DAILY/WEEKLY, 재계산 대상 플랜 수, 알림 발송 상태.
 *
 * 자리를 숨기지 않고 태그로 보여 주는 이유: 나중에 무엇이 들어올지 운영자가 화면에서 알 수 있고,
 * 비활성 선택지가 "고장"이 아니라 "아직 제공 전"이라는 것이 글자로 전달된다.
 * 상태가 아니라 안내라서 회색 점선(neutral)의 작은 크기(sm)로 둔다. 시계 아이콘은 "나중에"라는 뜻을 보조한다.
 */
export function PhaseLaterTag({ phase = '2단계', label, className }: PhaseLaterTagProps) {
  return (
    <AdminStatusPill
      icon={CalendarClockIcon}
      label={label ?? `${phase}부터`}
      tone="neutral"
      size="sm"
      className={className}
    />
  )
}
