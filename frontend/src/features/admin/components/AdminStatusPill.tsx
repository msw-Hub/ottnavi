import type { LucideIcon } from 'lucide-react'
import { cn } from 'cn'

/**
 * 알약 모양의 색 묶음(tone) 6가지. 각 tone은 색만이 아니라 "바탕을 채우는지"와 "테두리가 실선인지 점선인지"도 함께 정한다.
 * 그래서 흑백으로 봐도 tone끼리 모양이 다르다(색만으로 정보를 전달하지 않는다).
 */
export type AdminPillTone =
  | 'success' // 꽉 채운 초록 + 짙은 글자. 예: 수집 "성공"
  | 'progress' // 옅게 채운 하늘색 + 실선. 예: 수집 "실행 중"
  | 'danger' // 바탕 없음 + 빨간 실선. 예: 수집 "실패"
  | 'neutral' // 회색으로 옅게 채움 + 점선. 예: 수집 "중단됨", 제공처 "관련 없음"
  | 'attention' // 꽉 채운 노랑 + 짙은 글자. 예: 제공처 "미매핑", "요금 확인일 오래됨"
  | 'outline' // 바탕 없음 + 입력창 테두리색 실선. 예: 제공처 "매핑됨"

// tone별 바탕·글자·테두리 모양. 색 값은 직접 쓰지 않고 토큰(tokens.css, index.css) 이름만 쓴다
const TONE_CLASS: Record<AdminPillTone, string> = {
  success: 'border-solid border-success-bg bg-success-bg text-success',
  progress: 'border-solid border-info bg-info-bg text-info',
  // text-destructive(밝은 빨강)는 어두운 카드 바탕 위에 직접 쓰는 오류 글자용이다(index.css 주석 참고)
  danger: 'border-solid border-destructive bg-transparent text-destructive',
  // muted 바탕 위 muted-foreground 글자는 대비 약 8.28:1(index.css 주석). 테두리는 카드 위 3:1 이상인 --input
  neutral: 'border-dashed border-input bg-muted text-muted-foreground',
  attention: 'border-solid border-attention-border bg-attention-bg text-attention-fg',
  outline: 'border-solid border-input bg-transparent text-foreground',
}

// 크기 2가지. md는 표 안의 상태 배지(ProviderStatusBadge와 같은 크기), sm은 이름 옆에 붙는 작은 태그용이다
const SIZE_CLASS = {
  md: 'gap-1.25 border-2 py-0.75 pr-2.75 pl-2 text-sm leading-5',
  sm: 'gap-1 border py-0.5 pr-2 pl-1.5 text-xs leading-4',
} as const

const ICON_SIZE_CLASS = {
  md: 'size-4.25',
  sm: 'size-3.5',
} as const

interface AdminStatusPillProps {
  icon: LucideIcon // 상태를 모양으로 알리는 아이콘. 글자와 같은 뜻이라 화면 낭독기에는 숨긴다
  label: string // 화면에 보이는 상태 글자(예: "성공"). 낭독기도 이 글자를 읽는다
  tone: AdminPillTone // 색·채움·테두리 모양 묶음
  size?: 'md' | 'sm' // 기본 md
  iconClassName?: string // 아이콘에만 덧붙일 클래스(예: 실행 중 아이콘의 회전 애니메이션)
  className?: string // 놓이는 자리에 맞춘 여백 등을 덧붙일 때만 쓴다(색은 바꾸지 않는다)
}

/**
 * 관리자 화면의 상태 배지·태그가 함께 쓰는 알약 모양이다(Task 029).
 *
 * 왜 공통 모양을 따로 두나: 수집 상태(RunStatusBadge), 매핑 상태(MappingStateBadge), 주의 태그(StaleVerifiedTag)가
 * 모두 "아이콘 + 글자 + 모양이 다른 테두리" 규칙을 지켜야 한다. 각자 만들면 크기·굵기가 조금씩 달라지기 쉬워 한곳에 모았다.
 * 모양은 작품 상세의 제공 상태 배지(components/common/ProviderStatusBadge)와 맞췄다(굵은 2px 테두리, 알약 모양, 굵은 아이콘).
 * 관리자 화면 전용이라 components/common이 아니라 features/admin에 둔다.
 */
export function AdminStatusPill({
  icon: Icon,
  label,
  tone,
  size = 'md',
  iconClassName,
  className,
}: AdminStatusPillProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full font-bold whitespace-nowrap',
        SIZE_CLASS[size],
        TONE_CLASS[tone],
        className,
      )}
    >
      {/* 선 굵기 3: 작은 크기에서도 체크·X·경고 모양이 뭉개지지 않게 한다(ProviderStatusBadge와 같은 값) */}
      <Icon
        aria-hidden="true"
        strokeWidth={3}
        className={cn('shrink-0', ICON_SIZE_CLASS[size], iconClassName)}
      />
      <span>{label}</span>
    </span>
  )
}
