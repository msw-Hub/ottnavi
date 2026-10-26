import { useId, type ReactNode } from 'react'
import { ChevronRightIcon } from 'lucide-react'
import { Link } from 'react-router'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { formatDataAsOf } from '@/lib/format'
import { withJosa } from '@/lib/josa'

// JustWatch 공식 사이트 주소(출처 링크. SourceAttribution과 같은 주소)
const JUSTWATCH_URL = 'https://www.justwatch.com/'

// 목록 하나의 불러오기 상태(API 응답에 따라 홈 화면이 정한다)
export type RailStatus = 'ready' | 'loading' | 'error' | 'empty'

interface RailSectionProps {
  heading: string // 목록 제목(h2)
  description?: ReactNode // 제목 아래 한 줄: 무엇을 기준으로 뽑았는지(예: "TMDB 인기도 기준")
  moreTo?: string // "더보기"로 갈 주소. 없으면 더보기를 숨긴다
  dataAsOf: string | null // 이 목록의 데이터 기준일('YYYY-MM-DD'). 아직 모르면(불러오는 중·스냅샷 없음) null → "정보 없음"
  status?: RailStatus // 불러오기 상태(기본 ready)
  emptyTitle?: string // 빈 상태 제목
  emptyDescription?: string // 빈 상태 안내
  error?: unknown // 오류 상태일 때 실패 원인(ApiError면 안내 문장을 거기서 꺼낸다. 429면 "잠시 후 다시" 문구가 된다)
  onRetry?: () => void // 오류 상태의 "다시 시도"
  children: ReactNode // 목록(보통 ServiceTabs + ScrollRail). status가 ready일 때만 그린다
}

/**
 * 홈의 목록 묶음 하나(제목 · 기준 설명 · 더보기 · 목록 · 출처 한 줄)다.
 *
 * 제목 줄 오른쪽 "더보기"는 랭킹·탐색·독점 화면으로 가는 링크다. 낭독기가 여러 "더보기"를 구분하도록 제목을 숨은 글자로 덧붙인다.
 * 목록 아래 출처 한 줄: 제공처 정보가 보이는 곳이라 JustWatch 출처, 데이터 기준일, "확인된 범위"를 목록마다 붙인다(frontend.md 출처 표기).
 * 화면 맨 아래에도 전체 출처·기준일 묶음이 있지만, 긴 홈을 내려가다 한 목록만 보는 사람도 출처를 볼 수 있게 짧게 반복한다.
 * 문구는 공통 SourceAttribution·DataAsOf와 같은 말("제공처 정보 출처", "데이터 기준일")을 한 줄로 줄인 것이다.
 */
export function RailSection({
  heading,
  description,
  moreTo,
  dataAsOf,
  status = 'ready',
  emptyTitle = '보여 줄 작품이 없습니다',
  emptyDescription,
  error,
  onRetry,
  children,
}: RailSectionProps) {
  const headingId = useId()
  const dataAsOfText = formatDataAsOf(dataAsOf) // 모르거나 해석할 수 없으면 null

  return (
    // grid-cols-1(= minmax(0, 1fr)): 그리드 칸의 최소 폭을 0으로 둬, 안의 가로 스크롤 줄이 카드 전체 폭만큼 칸을 넓히지 않게 한다
    // (그냥 grid만 두면 칸 최소 폭이 내용 폭이 되어 화면이 옆으로 넓어지고 가로 스크롤이 생긴다)
    <section aria-labelledby={headingId} className="grid grid-cols-1 gap-3">
      <div className="flex items-end justify-between gap-3">
        <div className="grid min-w-0 gap-0.5">
          <h2 id={headingId} className="text-lg font-bold sm:text-xl">
            {heading}
          </h2>
          {description && <p className="text-sm text-muted-foreground">{description}</p>}
        </div>
        {moreTo && (
          // min-h-11(44px) 누르는 영역. 글자 옆 꺾쇠는 "다른 화면으로 간다"는 보조 표시라 낭독기에서 숨긴다
          <Link
            to={moreTo}
            className="-mr-2 inline-flex min-h-11 shrink-0 items-center gap-0.5 rounded-md px-2 text-sm font-semibold text-muted-foreground hover:text-foreground"
          >
            더보기
            <span className="sr-only">: {heading}</span>
            <ChevronRightIcon aria-hidden="true" className="size-4" />
          </Link>
        )}
      </div>

      <RailBody
        status={status}
        heading={heading}
        emptyTitle={emptyTitle}
        emptyDescription={emptyDescription}
        error={error}
        onRetry={onRetry}
      >
        {children}
      </RailBody>

      <p className="text-xs text-muted-foreground tabular-nums">
        제공처 정보 출처:{' '}
        <a
          href={JUSTWATCH_URL}
          target="_blank"
          rel="noopener noreferrer"
          className="text-primary underline underline-offset-3"
        >
          JustWatch
          <span className="sr-only">(새 탭에서 열림)</span>
        </a>{' '}
        (TMDB를 통해 제공) · 데이터 기준일{' '}
        {dataAsOf && dataAsOfText ? (
          <time dateTime={dataAsOf} className="font-semibold text-foreground">
            {dataAsOfText}
          </time>
        ) : (
          '정보 없음'
        )}{' '}
        · 확인된 범위이며 실제와 다를 수 있습니다
      </p>
    </section>
  )
}

interface RailBodyProps {
  status: RailStatus
  heading: string
  emptyTitle: string
  emptyDescription?: string
  error?: unknown
  onRetry?: () => void
  children: ReactNode
}

// 상태에 따라 목록 / 불러오는 중 / 오류 / 빈 상태 중 하나를 그린다
function RailBody({
  status,
  heading,
  emptyTitle,
  emptyDescription,
  error,
  onRetry,
  children,
}: RailBodyProps) {
  if (status === 'loading') return <RailSkeleton heading={heading} />
  if (status === 'error') {
    return (
      <ErrorState
        title={`${withJosa(heading, '을/를')} 불러오지 못했습니다`}
        error={error}
        onRetry={onRetry}
      />
    )
  }
  if (status === 'empty') return <EmptyState title={emptyTitle} description={emptyDescription} />
  return <>{children}</>
}

// 가로 줄 자리에 카드 모양 회색 칸 5개를 보인다. 칸은 장식이라 낭독기에서 숨기고, 무엇을 불러오는지는 role="status" 글자로 알린다.
// 깜빡임(animate-pulse)은 움직임 줄이기를 켜지 않은 사용자에게만 보인다(motion-safe)
export function RailSkeleton({ heading }: { heading: string }) {
  return (
    <div>
      <p role="status" className="sr-only">
        {withJosa(heading, '을/를')} 불러오는 중입니다
      </p>
      <div aria-hidden="true" className="-mx-4 flex gap-3 overflow-hidden px-4 py-1 sm:gap-4">
        {[0, 1, 2, 3, 4].map((index) => (
          <div key={index} className="grid w-32 shrink-0 gap-2 sm:w-36 lg:w-40">
            <div className="aspect-2/3 w-full rounded-lg bg-muted motion-safe:animate-pulse" />
            <div className="h-3.5 w-4/5 rounded-full bg-muted motion-safe:animate-pulse" />
            <div className="h-3 w-1/2 rounded-full bg-muted motion-safe:animate-pulse" />
          </div>
        ))}
      </div>
    </div>
  )
}
