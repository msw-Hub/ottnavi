import { useId, type ReactNode } from 'react'
import type { LucideIcon } from 'lucide-react'
import { cn } from 'cn'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDateTime } from '@/lib/format'

interface StatsCardFrameProps {
  title: string // 카드 제목(예: "회원 현황")
  icon: LucideIcon // 제목 앞 장식 아이콘. 제목 글자와 같은 뜻이라 낭독기에는 숨긴다
  description?: ReactNode // 제목 아래 "무엇을 세는지" 안내(예: 관리자·데모 계정 제외). 숫자를 오해하지 않게 늘 보인다
  measuredAt?: string | null // 기준 시각(ISO-8601). 있으면 제목 오른쪽에 "기준 2026-11-01 10:30"으로 작게 보인다
  footnote?: ReactNode // 카드 맨 아래 보조 문구(예: 7일·30일은 중복 제거 값)
  className?: string // 격자에서 칸을 넓힐 때만 쓴다(예: md:col-span-2)
  children: ReactNode // 본문(숫자·상태·로딩·오류)
}

/**
 * 모니터링 "운영 현황" 카드 3개가 함께 쓰는 상자 모양이다.
 *
 * 다른 관리자 카드(MonitoringLinksCard, 수집 화면의 알림 발송 상태)와 같은 상자(rounded-xl border bg-card p-4)를 써서
 * 한 화면 안의 상자 모양을 한 가지로 맞춘다. 위에서 아래로: 제목(+기준 시각) → 무엇을 세는지 → 본문 → 보조 문구.
 * 기준 시각을 제목 줄에 둔 이유: 숫자가 "언제 기준인지"는 숫자와 함께 읽혀야 하지만 숫자보다 눈에 띌 필요는 없다.
 */
export function StatsCardFrame({
  title,
  icon: Icon,
  description,
  measuredAt,
  footnote,
  className,
  children,
}: StatsCardFrameProps) {
  // useId: 카드가 여러 개여도 aria-labelledby용 id가 겹치지 않게 한다
  const headingId = useId()
  const measuredText = formatDateTime(measuredAt)

  return (
    <section
      aria-labelledby={headingId}
      className={cn(
        'grid content-start gap-3 rounded-xl border border-border bg-card p-4 text-sm',
        className,
      )}
    >
      <div className="grid gap-1">
        {/* 제목과 기준 시각. 좁은 화면에서 자리가 모자라면 기준 시각이 제목 아래 줄로 내려간다 */}
        <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-0.5">
          {/* h3: 화면 안에서 "운영 현황"(h2) 아래에 놓이는 카드라 한 단계 낮춘다 */}
          <h3 id={headingId} className="flex items-center gap-2 text-lg font-semibold">
            <Icon aria-hidden="true" className="size-5 shrink-0 text-muted-foreground" />
            {title}
          </h3>
          {measuredText && measuredAt && (
            <p className="text-xs text-muted-foreground tabular-nums">
              기준 <time dateTime={measuredAt}>{measuredText}</time>
            </p>
          )}
        </div>
        {description && <p className="break-keep text-muted-foreground">{description}</p>}
      </div>

      {children}

      {footnote && <p className="text-xs break-keep text-muted-foreground">{footnote}</p>}
    </section>
  )
}

interface StatsCardSkeletonProps {
  label: string // 낭독기에 읽힐 "무엇을 불러오는 중인지" 문장
  tileCount: number // 회색 칸 개수. 실제 숫자 칸 수와 맞춰 불러온 뒤 화면이 크게 출렁이지 않게 한다
}

// 로딩 중 자리표시. 회색 칸은 장식이라 낭독기에는 숨기고 상태 문장만 읽힌다(수집 화면과 같은 방식)
export function StatsCardSkeleton({ label, tileCount }: StatsCardSkeletonProps) {
  return (
    <div role="status" className="grid gap-2">
      <span className="sr-only">{label}</span>
      <div aria-hidden="true" className="grid grid-cols-2 gap-2">
        {Array.from({ length: tileCount }, (_, i) => (
          <Skeleton key={i} className="h-16 w-full" />
        ))}
      </div>
    </div>
  )
}

interface StatTileProps {
  label: ReactNode // 무엇의 숫자인지(예: "오늘 신규"). 태그(예: 잠정)를 함께 넣을 수 있다
  value: string // 이미 표기 함수(formatCountWithUnit)를 거친 글자(예: "3명", "-")
}

/**
 * 작은 숫자 칸 하나. 이름(작은 글자) 위, 숫자(큰 글자) 아래.
 * dl/dt/dd로 묶어 낭독기에 "오늘 신규, 3명"처럼 이름과 값이 짝지어 읽히게 한다. 바깥 <dl>은 쓰는 쪽이 감싼다.
 */
export function StatTile({ label, value }: StatTileProps) {
  return (
    <div className="grid content-start gap-0.5 rounded-lg border border-border p-3">
      <dt className="flex flex-wrap items-center gap-1.5 text-xs break-keep text-muted-foreground">
        {label}
      </dt>
      {/* tabular-nums: 숫자 자리 폭을 고정해 칸끼리 훑어 비교하기 쉽게 한다 */}
      <dd className="text-xl font-semibold tabular-nums">{value}</dd>
    </div>
  )
}
