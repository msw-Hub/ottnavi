import type { ReactNode } from 'react'
import { CircleXIcon, InfoIcon, TriangleAlertIcon } from 'lucide-react'
import { cn } from 'cn'

// 띠 종류별 모양. 색 값은 직접 쓰지 않고 토큰(tokens.css) 이름만 쓴다
const TONE_CLASS = {
  info: 'border-info bg-info-bg text-info',
  attention: 'border-attention-border bg-attention-bg text-attention-fg',
  danger: 'border-destructive bg-transparent text-destructive',
} as const

const TONE_ICON = {
  info: InfoIcon,
  attention: TriangleAlertIcon,
  danger: CircleXIcon,
} as const

interface OverviewNoticeBandProps {
  tone: keyof typeof TONE_CLASS // info 안내 / attention 주의 / danger 오류
  title?: string // 굵은 제목(없으면 본문만)
  children: ReactNode // 본문
  role?: 'status' | 'alert' | 'note' // 낭독기에 알리는 방식. 바뀌는 안내는 status, 급한 오류는 alert, 늘 보이는 보충 설명은 note
  className?: string // 놓이는 자리에 맞춘 여백
}

/**
 * LLM 번역 화면의 안내·주의·오류 띠다. 수집 화면의 안내 띠(CollectRunForm)와 같은 모양에 주의·오류 색을 더했다.
 * 색만으로 구분하지 않도록 종류마다 아이콘이 다르고, 제목이 있으면 글자로도 알린다.
 */
export function OverviewNoticeBand({
  tone,
  title,
  children,
  role,
  className,
}: OverviewNoticeBandProps) {
  const Icon = TONE_ICON[tone]
  return (
    <div
      role={role}
      className={cn(
        'flex items-start gap-2 rounded-lg border px-3 py-2.5 text-sm',
        TONE_CLASS[tone],
        className,
      )}
    >
      <Icon aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
      <div className="min-w-0 flex-1 break-keep">
        {title && <p className="font-semibold">{title}</p>}
        <div>{children}</div>
      </div>
    </div>
  )
}
