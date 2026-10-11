import { CheckIcon, MinusIcon, TriangleAlertIcon, type LucideIcon } from 'lucide-react'
import { AdminStatusPill, type AdminPillTone } from '@/features/admin/components/AdminStatusPill'
import type { MappingDisplayState } from '@/features/admin/types'

// 상태별 글자·아이콘·모양
const MAPPING_STATE_APPEARANCE: Record<
  MappingDisplayState,
  { label: string; icon: LucideIcon; tone: AdminPillTone }
> = {
  // 미매핑: 꽉 채운 노랑 + 경고 아이콘. 운영자가 먼저 봐야 하는 행이라 가장 눈에 띄게 한다
  UNMAPPED: { label: '미매핑', icon: TriangleAlertIcon, tone: 'attention' },
  // 매핑됨: 바탕 없는 실선 + 체크. 정상 상태라 차분하게 둔다
  MAPPED: { label: '매핑됨', icon: CheckIcon, tone: 'outline' },
  // 관련 없음: 회색 점선 + 가로줄. 확인을 끝내고 쓰지 않기로 한 제공처라 가장 흐리게 둔다
  IGNORED: { label: '관련 없음', icon: MinusIcon, tone: 'neutral' },
}

interface MappingStateBadgeProps {
  state: MappingDisplayState // 화면 표시용 매핑 상태
  className?: string // 놓이는 자리에 맞춘 여백 등을 덧붙일 때만 쓴다
}

/**
 * 제공처 매핑 화면(SCR-15)의 매핑 상태 배지다(미매핑 / 매핑됨 / 관련 없음).
 *
 * "미매핑" 강조는 이 배지 하나에만 맡기지 않는다. 화면 조립 쪽에서 행 왼쪽 막대(border-attention-border)와
 * "미매핑 먼저" 정렬을 함께 쓴다. 막대는 색이지만, 이 배지가 아이콘과 글자로 같은 뜻을 전하므로 색만으로 전달하지 않는다.
 * "관련 없음"을 따로 둔 이유: 확인을 마친 제공처까지 계속 미매핑으로 강조되면 새로 발견된 제공처가 묻힌다(사용자 결정 2026-10-11).
 */
export function MappingStateBadge({ state, className }: MappingStateBadgeProps) {
  const { label, icon, tone } = MAPPING_STATE_APPEARANCE[state]
  return <AdminStatusPill icon={icon} label={label} tone={tone} className={className} />
}
