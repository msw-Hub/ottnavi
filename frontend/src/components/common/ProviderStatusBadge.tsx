import { CheckIcon, createLucideIcon, XIcon, type LucideIcon } from 'lucide-react'
import { cn } from 'cn'
import { AVAILABILITY_LABELS, type AvailabilityStatus } from '@/components/common/displayTypes'

/*
 * "모름" 아이콘: 동그라미 없는 물음표.
 * lucide의 CircleQuestionMarkIcon에서 바깥 원만 뺀 모양이다(경로 값은 lucide circle-question-mark와 같다).
 * 원을 빼는 이유: 배지 자체가 알약 테두리라 원까지 있으면 테두리가 겹쳐 보여, 시안 D2c도 물음표만 썼다.
 * lucide에는 원 없는 물음표 아이콘이 없어 lucide가 제공하는 createLucideIcon으로 같은 규격(24칸, 선 아이콘)으로 만든다.
 */
const QuestionMarkIcon = createLucideIcon('question-mark', [
  // Task 026 검수: 원이 없어 물음표만 작게 보여, 원본 좌표를 글자 중심(12, 11.5) 기준으로 1.4배 키웠다.
  // transform으로 키우지 않고 좌표를 직접 키운 이유: transform은 선 굵기(strokeWidth 3)까지 1.4배로 만들어 뭉개진다.
  // 원본(1배): 'M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3' / 'M12 17h.01'
  // 1.4배: 시작점 (9.09, 9) → (7.93, 8), 호 반지름 3 → 4.2, 끝 변위 (5.83, 1) → (8.16, 1.4), 곡선 변위와 점 위치도 같은 비율
  [
    'path',
    { d: 'M7.93 8a4.2 4.2 0 0 1 8.16 1.4c0 2.8-4.2 4.2-4.2 4.2', key: 'question-mark-curve' },
  ],
  ['path', { d: 'M12 19.2h.01', key: 'question-mark-dot' }],
])

interface ProviderStatusBadgeProps {
  status: AvailabilityStatus // 제공 상태 3가지 중 하나
  className?: string // 놓이는 자리에 맞춘 여백 등을 덧붙일 때만 쓴다(색은 바꾸지 않는다)
}

// 상태별 모양. 아이콘·테두리 모양·색을 함께 바꿔, 색을 구분하지 못해도 모양과 글자로 상태를 알 수 있게 한다
const STATUS_APPEARANCE: Record<AvailabilityStatus, { icon: LucideIcon; className: string }> = {
  // 있음: 초록으로 꽉 채운 알약 + 체크 아이콘
  AVAILABLE: {
    icon: CheckIcon,
    className:
      'border-solid border-status-available-border bg-status-available-bg text-status-available',
  },
  // 없음: 바탕 없는 실선 테두리 + X 아이콘
  NOT_AVAILABLE: {
    icon: XIcon,
    className:
      'border-solid border-status-unavailable-border bg-status-unavailable-bg text-status-unavailable',
  },
  // 모름: 옅은 중립색 바탕 + 점선 테두리 + 물음표 아이콘(노랑에서 차분한 슬레이트 톤으로 바꿈, tokens.css 참고)
  UNKNOWN: {
    icon: QuestionMarkIcon,
    className:
      'border-dashed border-status-unknown-border bg-status-unknown-bg text-status-unknown',
  },
}

/**
 * 작품이 한 서비스에서 구독으로 제공되는지(있음 / 없음 / 모름)를 보여 주는 배지다.
 *
 * 세 상태를 색만으로 나누지 않는 이유: 색을 구분하기 어려운 사람이나 흑백 화면에서도 읽혀야 한다(frontend.md, PRD 5.6).
 * 그래서 ① 글자(있음/없음/모름) ② 아이콘(체크/X/물음표) ③ 테두리 모양(꽉 채움/실선/점선)을 함께 바꾸고 색은 보조로만 쓴다.
 * 아이콘은 글자와 같은 뜻이라 화면 낭독기에는 숨긴다(aria-hidden). 낭독기는 글자만 읽는다.
 * 시안 D2c의 .status 모양(굵은 2px 테두리, 알약 모양)을 그대로 옮겼다.
 */
export function ProviderStatusBadge({ status, className }: ProviderStatusBadgeProps) {
  const { icon: Icon, className: statusClassName } = STATUS_APPEARANCE[status]

  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.25 rounded-full border-2 py-0.75 pr-2.75 pl-2 text-sm leading-5 font-bold whitespace-nowrap',
        statusClassName,
        className,
      )}
    >
      {/* 시안의 굵은 아이콘(선 굵기 3)을 따른다. 선이 굵어야 작은 크기에서도 체크·X·물음표 모양이 뭉개지지 않는다 */}
      <Icon aria-hidden="true" strokeWidth={3} className="size-4.25 shrink-0" />
      <span>{AVAILABILITY_LABELS[status]}</span>
    </span>
  )
}
