/*
 * 일괄 번역 버튼 옆에 붙는 "오늘 사용량" 한 줄이다. 값(usedToday·dailyLimit)은 번역 현황 응답(summary)에서 그대로 넘긴다.
 */
import { CircleCheckIcon, OctagonXIcon, TriangleAlertIcon } from 'lucide-react'
import { cn } from 'cn'
import { getRemainingCalls, getUsageLevel } from '@/features/admin/overviewRules'

interface OverviewUsageMeterProps {
  usedToday: number // 오늘(태평양 시간 날짜 기준) 호출한 수
  dailyLimit: number // 하루 호출 상한(현재 400)
  className?: string // 놓이는 자리에 맞춘 여백
}

// 단계별 모양. 색만으로 알리지 않도록 아이콘과 글자(상태 말)가 단계마다 다르다
const LEVEL_VIEW = {
  normal: { icon: CircleCheckIcon, word: '여유', className: 'border-border bg-background' },
  // 80% 이상: 노랑 채움(관리자 화면의 주의 모양과 같다)
  nearLimit: {
    icon: TriangleAlertIcon,
    word: '곧 상한',
    className: 'border-attention-border bg-attention-bg text-attention-fg',
  },
  // 100%: 빨간 실선 + 팔각형 X(멈춤 표지). 일괄 번역을 시작해도 바로 멈춘다는 뜻이라 주의보다 강하게 보인다
  reached: {
    icon: OctagonXIcon,
    word: '오늘 상한 도달',
    className: 'border-destructive bg-transparent text-destructive',
  },
} as const

/**
 * "오늘 120 / 400회 · 남은 280회"를 보이는 작은 상자다. 일괄 번역 버튼 바로 옆(좁은 화면은 바로 아래)에 둔다.
 *
 * 왜 버튼 옆인가: 일괄 번역 한 편이 호출 1회 이상(자동 재시도 포함)을 쓰므로, 몇 편을 넣을지 정하는 순간 남은 횟수가 눈에 있어야 한다.
 * 단계 판정은 overviewRules의 getUsageLevel(79% 여유 / 80% 곧 상한 / 100% 도달)을 그대로 쓴다.
 * 숫자는 tabular-nums(고정폭 숫자)라 5초 폴링으로 값이 바뀌어도 글자가 좌우로 흔들리지 않는다.
 */
export function OverviewUsageMeter({ usedToday, dailyLimit, className }: OverviewUsageMeterProps) {
  const level = getUsageLevel(usedToday, dailyLimit)
  const remaining = getRemainingCalls(usedToday, dailyLimit)
  const view = LEVEL_VIEW[level]
  const Icon = view.icon

  return (
    <div className={cn('grid gap-1', className)}>
      <p
        className={cn(
          'inline-flex w-fit flex-wrap items-center gap-x-1.5 gap-y-0.5 rounded-lg border px-2.5 py-1 text-sm tabular-nums',
          view.className,
        )}
      >
        <Icon aria-hidden="true" strokeWidth={2.5} className="size-4 shrink-0" />
        <span className="font-semibold">{view.word}</span>
        <span aria-hidden="true">·</span>
        <span>
          오늘 {usedToday.toLocaleString('ko-KR')} / {dailyLimit.toLocaleString('ko-KR')}회
        </span>
        <span aria-hidden="true">·</span>
        <span className="font-semibold">남은 {remaining.toLocaleString('ko-KR')}회</span>
      </p>
      <p className="text-xs break-keep text-muted-foreground">
        사용량은 태평양 시간으로 날짜가 바뀔 때(한국 시간 오후 4시 또는 5시) 0으로 돌아갑니다.
      </p>
    </div>
  )
}
