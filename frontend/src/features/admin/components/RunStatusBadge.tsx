import {
  CheckIcon,
  CirclePauseIcon,
  CircleXIcon,
  LoaderCircleIcon,
  type LucideIcon,
} from 'lucide-react'
import { AdminStatusPill, type AdminPillTone } from '@/features/admin/components/AdminStatusPill'
import type { RunDisplayStatus } from '@/features/admin/types'

// 상태별 글자·아이콘·모양. 네 상태가 글자, 아이콘, 채움/테두리 모양 세 가지 모두에서 서로 다르다
const RUN_STATUS_APPEARANCE: Record<
  RunDisplayStatus,
  { label: string; icon: LucideIcon; tone: AdminPillTone; iconClassName?: string }
> = {
  // 실행 중: 옅은 하늘색 + 회전하는 원 아이콘.
  // motion-reduce:animate-none: 운영체제에서 "움직임 줄이기"를 켠 사람에게는 회전을 멈춘다(prefers-reduced-motion).
  // 멈춰도 아이콘 모양과 "실행 중" 글자로 상태가 읽힌다
  RUNNING: {
    label: '실행 중',
    icon: LoaderCircleIcon,
    tone: 'progress',
    iconClassName: 'animate-spin motion-reduce:animate-none',
  },
  COMPLETED: { label: '성공', icon: CheckIcon, tone: 'success' }, // 꽉 채운 초록 + 체크
  FAILED: { label: '실패', icon: CircleXIcon, tone: 'danger' }, // 빨간 실선 + X
  STOPPED: { label: '중단됨', icon: CirclePauseIcon, tone: 'neutral' }, // 회색 점선 + 일시정지
}

interface RunStatusBadgeProps {
  status: RunDisplayStatus // 화면 표시용 실행 상태(API 값에서 바꾸는 일은 훅이 맡는다)
  className?: string // 놓이는 자리에 맞춘 여백 등을 덧붙일 때만 쓴다
}

/**
 * 수집 실행 이력(SCR-16)의 실행 상태 배지다(실행 중 / 성공 / 실패 / 중단됨).
 *
 * 색만으로 상태를 나누지 않는다: ① 글자 ② 아이콘(회전 원 / 체크 / X / 일시정지) ③ 모양(옅은 채움 / 꽉 채움 / 빈 실선 / 점선)을
 * 함께 바꾼다. 흑백 화면에서도 네 상태가 구분된다.
 * "중단됨"은 서버가 멈춰 기록만 실행 중으로 남은 실행이다. 이 배지 옆에 [복구] 버튼을 두는 것은 화면 조립 쪽이 정한다.
 */
export function RunStatusBadge({ status, className }: RunStatusBadgeProps) {
  const { label, icon, tone, iconClassName } = RUN_STATUS_APPEARANCE[status]
  return (
    <AdminStatusPill
      icon={icon}
      label={label}
      tone={tone}
      iconClassName={iconClassName}
      className={className}
    />
  )
}
