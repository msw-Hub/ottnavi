import { CalendarRangeIcon, ChevronRightIcon } from 'lucide-react'
import { Link } from 'react-router'
import type { ActivePlan, PlanType } from '@/api/generated/model'
import { ErrorState } from '@/components/common/ErrorState'
import { Button } from '@/components/ui/button'
import { useCurrentPlan } from '@/hooks/usePlanDrafts'
import { formatDate, formatWon } from '@/lib/format'

// 플랜 유형 이름(features/plan의 PLAN_TYPE_LABELS와 같은 글자. 기능 폴더끼리 import하지 않아 따로 둔다)
const PLAN_TYPE_TEXT: Record<PlanType, string> = {
  RECOMMENDED: '추천형',
  SAVER: '절약형',
  SIMPLE: '간편형',
}

// 카드에 보일 한 줄 요약. 저장한 플랜(ActivePlan)에서 홈이 쓰는 값만 뽑는다
interface PlanSummary {
  planTypeLabel: string // 저장한 플랜 유형 이름(추천형 / 절약형 / 간편형)
  monthLabel: string // 이번 달(예: "10월")
  productNames: string[] // 이번 달 구독할 상품 이름(없으면 빈 배열 → 구독 없음)
  monthlyCost: number // 이번 달 결제 예상 금액(원)
  mustWatchDone: number // 3개월 안에 끝까지 볼 수 있다고 계산된 "꼭 볼 작품" 수
  mustWatchTotal: number // 찜한 "꼭 볼 작품" 수
  savedAt: string // 플랜을 저장한 시각(ISO)
}

// 저장한 플랜에서 요약을 만든다. 첫 달(monthIndex 0)이 "이번 달"이다
function toPlanSummary(plan: ActivePlan): PlanSummary {
  const thisMonth = plan.months.find((month) => month.monthIndex === 0) ?? plan.months[0]
  return {
    planTypeLabel: PLAN_TYPE_TEXT[plan.planType],
    monthLabel: `${Number(thisMonth.month.slice(5, 7))}월`,
    productNames: thisMonth.products.map((product) => product.productName),
    monthlyCost: thisMonth.cost,
    mustWatchDone: plan.mustCompleted,
    mustWatchTotal: plan.mustTotal,
    savedAt: plan.activatedAt,
  }
}

/**
 * 로그인한 사용자에게 홈 맨 위에 보이는 "내 플랜" 자리다(반복 사용자가 홈에서 바로 플랜으로 가는 길).
 *
 * 저장한 현재 플랜을 서버에서 받아(useCurrentPlan, 계약 getCurrentPlan) 보인다. 회원에게만 그려지는 자리(HomeIntro의 memberSlot)라
 * 비로그인 요청(401)은 생기지 않는다. 세 가지 상태를 같은 카드 자리에서 바꿔 그려 화면이 출렁이지 않게 한다.
 * - 받는 중: 같은 높이의 회색 칸(motion-safe일 때만 깜빡임).
 * - 오류: 안내와 "다시 시도". 홈의 다른 줄은 영향받지 않는다.
 * - 성공: 플랜이 있으면 요약, 없으면(서버가 404가 아닌 { plan: null }을 준다) 계산 시작 안내.
 */
export function MyPlanBanner() {
  const currentPlan = useCurrentPlan()

  if (currentPlan.isPending) {
    return (
      <section
        aria-labelledby="home-my-plan"
        className="grid gap-3 rounded-xl border border-border bg-card p-4 sm:p-5"
      >
        <h2 id="home-my-plan" className="sr-only">
          내 플랜
        </h2>
        <p role="status" className="sr-only">
          내 플랜을 불러오는 중입니다
        </p>
        <div aria-hidden="true" className="grid gap-2">
          <div className="h-4 w-24 rounded-full bg-muted motion-safe:animate-pulse" />
          <div className="h-6 w-3/4 rounded-full bg-muted motion-safe:animate-pulse" />
          <div className="h-4 w-1/2 rounded-full bg-muted motion-safe:animate-pulse" />
        </div>
      </section>
    )
  }

  if (currentPlan.isError) {
    return (
      <section aria-labelledby="home-my-plan">
        <h2 id="home-my-plan" className="sr-only">
          내 플랜
        </h2>
        <ErrorState
          title="내 플랜을 불러오지 못했습니다"
          error={currentPlan.error}
          onRetry={() => void currentPlan.refetch()}
        />
      </section>
    )
  }

  return <PlanBannerCard summary={currentPlan.data ? toPlanSummary(currentPlan.data) : null} />
}

interface PlanBannerCardProps {
  summary: PlanSummary | null // 저장한 현재 플랜 요약. 없으면 null → 계산 시작 안내
}

/**
 * 내 플랜 카드의 모양이다.
 * - 플랜이 있으면: 이번 달 구독할 상품과 금액을 한 줄로, 꼭 볼 작품을 몇 편 볼 수 있다고 계산됐는지와 저장일을 둘째 줄로 보이고 "플랜 보기"로 보낸다.
 * - 플랜이 없으면: 계산을 시작하라는 안내와 "플랜 계산하기"·"찜 목록" 버튼을 보인다.
 * 금액은 lib/format의 formatWon으로만 쓴다(frontend.md 숫자 표시). 결과는 "계산됐어요"처럼 계산 결과로 말하고 확정처럼 쓰지 않는다.
 */
function PlanBannerCard({ summary }: PlanBannerCardProps) {
  return (
    <section
      aria-labelledby="home-my-plan"
      className="grid gap-3 rounded-xl border border-border bg-card p-4 sm:flex sm:items-center sm:justify-between sm:gap-6 sm:p-5"
    >
      <div className="grid min-w-0 gap-1">
        <h2
          id="home-my-plan"
          className="flex items-center gap-1.5 text-sm font-semibold text-muted-foreground"
        >
          <CalendarRangeIcon aria-hidden="true" className="size-4" />내 플랜
          {summary && <span className="font-normal">· {summary.planTypeLabel}</span>}
        </h2>
        {summary ? (
          <>
            <p className="text-lg font-bold">
              {summary.productNames.length > 0
                ? `${summary.monthLabel}에는 ${summary.productNames.join(' + ')} 구독`
                : `${summary.monthLabel}에는 구독하지 않아도 돼요`}{' '}
              <span className="tabular-nums">· {formatWon(summary.monthlyCost)}</span>
            </p>
            <p className="text-sm text-muted-foreground tabular-nums">
              꼭 볼 작품 {summary.mustWatchTotal}편 중 {summary.mustWatchDone}편을 3개월 안에 볼 수
              있다고 계산됐어요 · 저장일 {formatDate(summary.savedAt) ?? '정보 없음'}
            </p>
          </>
        ) : (
          <>
            <p className="text-lg font-bold">아직 저장한 플랜이 없어요</p>
            <p className="text-sm text-muted-foreground">
              찜한 작품과 월 예산·시청 시간으로 3개월 구독 순서를 계산해 보세요.
            </p>
          </>
        )}
      </div>
      <div className="flex shrink-0 flex-wrap gap-2">
        {summary ? (
          <Button asChild size="lg" className="h-11 px-5">
            <Link to="/plan">
              플랜 보기
              <ChevronRightIcon aria-hidden="true" />
            </Link>
          </Button>
        ) : (
          <>
            <Button asChild size="lg" className="h-11 px-5">
              <Link to="/plan">플랜 계산하기</Link>
            </Button>
            <Button
              asChild
              variant="outline"
              size="lg"
              className="h-11 border-input bg-transparent"
            >
              <Link to="/wishlist">찜 목록</Link>
            </Button>
          </>
        )}
      </div>
    </section>
  )
}
