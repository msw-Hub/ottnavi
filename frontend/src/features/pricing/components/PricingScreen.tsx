import type { ReactNode } from 'react'
import { CalculatorIcon, CalendarIcon, LoaderCircleIcon, PencilIcon } from 'lucide-react'
import { Link, useLocation, useNavigate } from 'react-router'
import type { OttService } from '@/api/generated/model'
import { isApiError } from '@/api/http'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { LoadingState } from '@/components/common/LoadingState'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { PricingRow } from '@/features/pricing/components/PricingRow'
import { usePricing } from '@/features/pricing/hooks/usePricing'
import type { PricingOverview } from '@/features/pricing/mockTypes'
import { useOttServices } from '@/hooks/useOttServices'
import { useCalculatePlan } from '@/hooks/usePlanDrafts'
import { formatDataAsOf, formatMinutes, formatWon } from '@/lib/format'

// 화면 안에서 "계산하기" 버튼에 넘기는 계산 진행 상태와 시작 함수
interface PricingCalculation {
  isPending: boolean // 계산이 진행 중인지
  error: unknown // 계산 실패 원인. 실패하지 않았으면 null
  onCalculate: () => void // "계산하기"를 눌렀을 때 계산을 시작한다(성공하면 플랜 결과 화면으로 이동한다)
}

/**
 * SCR-09 요금 확인·수정 화면의 본문이다.
 *
 * 계산 훅(useCalculatePlan)을 이 화면이 직접 쓰는 이유: 예전에는 plan 기능의 훅이라 기능 폴더끼리 import 금지 때문에
 * pages/PricingPage가 대신 불러 props로 넘겼다. 훅을 src/hooks로 올려 공유하게 되면서 pages를 다시 얇게 두고 화면이 계산 시작·이동까지 맡는다.
 * 계산이 성공하면 플랜 결과(SCR-10)로 이동하고, 목업 확인용 주소 파라미터(?mock…)는 이어 붙여 같은 시나리오로 결과를 본다.
 *
 * 위에서 아래로: 예산·시청 시간 요약 띠 → 요금 기준일 → 서비스별 상품 표(좁은 화면은 카드) → 플랜 계산 버튼.
 * 요금이 곧 계산의 입력이라 "무엇을 근거로 계산하는지"를 요약 띠와 출처 라벨로 먼저 보여 준다.
 */
export function PricingScreen() {
  const pricing = usePricing()
  const services = useOttServices()
  const navigate = useNavigate()
  const location = useLocation()
  const calculate = useCalculatePlan()
  const calculation: PricingCalculation = {
    isPending: calculate.isPending,
    error: calculate.error,
    onCalculate: () =>
      calculate.mutate(undefined, {
        onSuccess: () => navigate({ pathname: '/plan', search: location.search }),
      }),
  }

  let content: ReactNode
  if (pricing.isPending || services.isPending) {
    content = <LoadingState message="요금 정보를 불러오고 있어요" />
  } else if (pricing.isError) {
    content = <ErrorState error={pricing.error} onRetry={() => pricing.refetch()} />
  } else if (services.isError) {
    content = <ErrorState error={services.error} onRetry={() => services.refetch()} />
  } else if (pricing.data.services.length === 0) {
    content = (
      <EmptyState
        title="요금 정보가 아직 없어요"
        description="관리자가 요금표를 등록하면 여기에 보여요. 잠시 후 다시 확인해 주세요."
      />
    )
  } else {
    content = (
      <PricingContent overview={pricing.data} services={services.data} calculation={calculation} />
    )
  }

  return (
    <section className="flex flex-col gap-5">
      <h1 className="text-2xl font-semibold">요금 확인</h1>
      {content}
    </section>
  )
}

interface PricingContentProps {
  overview: PricingOverview // 서비스별 요금과 요약
  services: OttService[] // 서비스 목록 API의 7개 서비스(이름·데이터 충분도)
  calculation: PricingCalculation
}

// 데이터를 불러온 뒤의 화면
function PricingContent({ overview, services, calculation }: PricingContentProps) {
  const location = useLocation()
  const serviceById = new Map(services.map((service) => [service.ottServiceId, service]))
  // 예산·시청 시간이 비어 있으면 계산을 시작할 수 없다(서버도 400). 먼저 설정하도록 안내하고 버튼을 막는다
  const isReadyToCalculate =
    overview.monthlyBudget !== null && overview.monthlyWatchMinutes !== null
  const priceDate = formatDataAsOf(overview.priceEffectiveFrom)

  return (
    <div className="flex flex-col gap-5">
      {/* 예산·시청 시간 요약 띠: 계산이 무엇을 기준으로 하는지 */}
      <section
        aria-label="계산 기준 요약"
        className="grid gap-3 rounded-xl border border-border bg-card p-4 sm:grid-cols-[1fr_1fr_auto] sm:items-center"
      >
        <div>
          <p className="text-sm text-muted-foreground">월 예산</p>
          <p className="text-xl font-extrabold tabular-nums">{formatWon(overview.monthlyBudget)}</p>
        </div>
        <div>
          <p className="text-sm text-muted-foreground">월 시청 시간</p>
          <p className="text-xl font-extrabold tabular-nums">
            {formatMinutes(overview.monthlyWatchMinutes)}
          </p>
        </div>
        <Button asChild variant="outline" className="h-11 justify-self-start sm:justify-self-end">
          <Link to={`/settings${location.search}`} state={{ from: `/pricing${location.search}` }}>
            <PencilIcon aria-hidden="true" />
            설정 바꾸기
          </Link>
        </Button>
      </section>

      {!isReadyToCalculate && (
        <Alert className="border-info bg-info-bg text-info">
          <AlertTitle>예산과 시청 시간을 먼저 입력해 주세요</AlertTitle>
          <AlertDescription className="text-info">
            둘 다 있어야 플랜을 계산할 수 있어요. 위의 &quot;설정 바꾸기&quot;에서 입력해 주세요.
          </AlertDescription>
        </Alert>
      )}

      <div className="grid gap-1">
        {/* 요금 기준일: 요금표가 적용되기 시작한 날. 요금은 바뀔 수 있어 어느 날짜 기준인지 보여 준다 */}
        <p className="flex items-start gap-1.75 text-sm text-muted-foreground tabular-nums">
          <CalendarIcon aria-hidden="true" className="mt-0.75 size-4 shrink-0" />
          {priceDate ? (
            <span>
              요금 기준일 <strong className="font-semibold text-foreground">{priceDate}</strong> ·
              실제 요금과 다를 수 있어요. 다르면 아래에서 내 금액으로 고쳐 주세요.
            </span>
          ) : (
            <span>요금 기준일 정보 없음</span>
          )}
        </p>
      </div>

      <section aria-labelledby="pricing-list-heading" className="grid gap-3">
        <h2 id="pricing-list-heading" className="text-lg font-semibold">
          서비스별 요금
        </h2>
        {/* 넓은 화면에서만 보이는 열 제목. 좁은 화면에서는 칸마다 라벨이 붙는다. 낭독기에는 줄 안의 라벨이 읽히므로 이 줄은 숨긴다 */}
        <div
          aria-hidden="true"
          className="hidden gap-4 px-4 text-sm font-semibold text-muted-foreground md:grid md:grid-cols-[minmax(0,1.4fr)_minmax(0,0.8fr)_minmax(0,1.6fr)_minmax(0,1fr)]"
        >
          <span>서비스·상품</span>
          <span>기본 요금</span>
          <span>내 금액 (바꾸려면 입력)</span>
          <span>계산에 쓰는 금액</span>
        </div>
        <ul className="grid gap-3">
          {overview.services.map((service) => {
            const info = serviceById.get(service.ottServiceId)
            return (
              <PricingRow
                key={service.productId}
                service={service}
                serviceName={info?.name ?? service.productName}
                isInsufficientData={info?.dataQuality === 'INSUFFICIENT'}
                isLocked={calculation.isPending}
              />
            )
          })}
        </ul>
      </section>

      {/* 플랜 계산 구역. 모바일에서는 화면 아래에 고정하고, 넓은 화면에서는 목록 아래 카드로 둔다 */}
      <section
        aria-label="플랜 계산"
        className="grid gap-2 max-md:sticky max-md:bottom-0 max-md:-mx-4 max-md:z-20 max-md:border-t max-md:border-border max-md:bg-card max-md:px-4 max-md:pt-3 max-md:pb-[max(0.75rem,env(safe-area-inset-bottom))] md:rounded-xl md:border md:border-border md:bg-card md:p-4"
      >
        <Button
          type="button"
          size="lg"
          disabled={!isReadyToCalculate || calculation.isPending}
          onClick={calculation.onCalculate}
          className="h-12 w-full text-base md:w-auto md:justify-self-end md:px-6"
        >
          {calculation.isPending ? (
            <LoaderCircleIcon aria-hidden="true" className="motion-safe:animate-spin" />
          ) : (
            <CalculatorIcon aria-hidden="true" />
          )}
          {calculation.isPending ? '계산하는 중' : '3개월 플랜 계산하기'}
        </Button>

        {/* 진행 안내: 영역은 항상 있고 안의 글자만 바뀐다. 이미 있던 영역의 글자가 바뀌어야 낭독기(aria-live)가 읽어 준다 */}
        <div role="status" aria-live="polite">
          {calculation.isPending && (
            <div className="grid gap-1.5">
              <p className="text-sm font-semibold">3가지 플랜을 계산하고 있어요</p>
              <p className="text-sm text-muted-foreground">
                찜한 작품과 예산, 시청 시간에 맞춰 조합을 찾는 중이에요. 몇 초 걸릴 수 있어요.
              </p>
              {/* 진행 막대: 끝이 정해지지 않은 작업이라 움직이는 띠로만 보인다. 동작 줄이기 설정이면 멈춘 띠가 된다 */}
              <div aria-hidden="true" className="h-1.5 overflow-hidden rounded-full bg-muted">
                <div className="h-full w-1/3 rounded-full bg-primary motion-safe:animate-pulse" />
              </div>
            </div>
          )}
        </div>

        {calculation.error != null && !calculation.isPending && (
          <Alert variant="destructive">
            <AlertTitle>플랜을 계산하지 못했어요</AlertTitle>
            <AlertDescription className="text-destructive/90">
              {isApiError(calculation.error)
                ? calculation.error.userMessage
                : '잠시 후 다시 시도해 주세요.'}
              {isApiError(calculation.error) && calculation.error.status === 400 && (
                <>
                  {' '}
                  <Link to={`/wishlist${location.search}`} className="underline underline-offset-3">
                    찜 목록 확인하기
                  </Link>
                </>
              )}
            </AlertDescription>
          </Alert>
        )}
      </section>
    </div>
  )
}
