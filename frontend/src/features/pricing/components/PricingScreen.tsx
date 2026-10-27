import type { ReactNode } from 'react'
import {
  BadgePercentIcon,
  CalculatorIcon,
  CalendarIcon,
  ListChecksIcon,
  LoaderCircleIcon,
  PencilIcon,
} from 'lucide-react'
import { Link, useLocation, useNavigate } from 'react-router'
import type { OttService, ServicePrice, UserSettings } from '@/api/generated/model'
import { isApiError } from '@/api/http'
import { BlockingProgressDialog } from '@/components/common/BlockingProgressDialog'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { LoadingState } from '@/components/common/LoadingState'
import { PageSection } from '@/components/common/PageSection'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { PricingList } from '@/features/pricing/components/PricingGroupCard'
import { usePricing } from '@/features/pricing/hooks/usePricing'
import { useOttServices } from '@/hooks/useOttServices'
import { useCalculatePlan } from '@/hooks/usePlanDrafts'
import { useUserSettings } from '@/hooks/useUserSettings'
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
 * 위에서 아래로: 예산·시청 시간 요약 띠 → 서비스별 요금(제목 바로 아래 요금 기준일 → 상품 카드) → 플랜 계산 버튼.
 * 요금이 곧 계산의 입력이라 "무엇을 근거로 계산하는지"를 요약 띠와 출처 라벨로 먼저 보여 준다.
 */
export function PricingScreen() {
  const pricing = usePricing()
  const services = useOttServices()
  // 예산·시청 시간 요약은 요금 응답에 없어 내 설정에서 읽는다(계약 listMyServicePrices는 요금 목록만 준다)
  const settings = useUserSettings()
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
  if (pricing.isPending || services.isPending || settings.isPending) {
    content = <LoadingState message="요금 정보를 불러오고 있어요" />
  } else if (pricing.isError) {
    content = <ErrorState error={pricing.error} onRetry={() => pricing.refetch()} />
  } else if (services.isError) {
    content = <ErrorState error={services.error} onRetry={() => services.refetch()} />
  } else if (settings.isError) {
    content = <ErrorState error={settings.error} onRetry={() => settings.refetch()} />
  } else if (pricing.data.length === 0) {
    content = (
      <EmptyState
        title="요금 정보가 아직 없어요"
        description="관리자가 요금표를 등록하면 여기에 보여요. 잠시 후 다시 확인해 주세요."
      />
    )
  } else {
    content = (
      <PricingContent
        prices={pricing.data}
        settings={settings.data}
        services={services.data}
        calculation={calculation}
      />
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
  prices: ServicePrice[] // 서비스별 요금(상품 단위 줄)
  settings: UserSettings // 내 설정(예산·시청 시간 요약에 쓴다)
  services: OttService[] // 서비스 목록 API의 7개 서비스(이름·데이터 충분도)
  calculation: PricingCalculation
}

// 데이터를 불러온 뒤의 화면
function PricingContent({ prices, settings, services, calculation }: PricingContentProps) {
  const location = useLocation()
  const isLowestPriceMode = settings.isLowestPriceMode
  // 예산·시청 시간이 비어 있으면 계산을 시작할 수 없다(서버도 400). 먼저 설정하도록 안내하고 버튼을 막는다
  const isReadyToCalculate =
    settings.monthlyBudget !== null && settings.monthlyWatchMinutes !== null
  // 요금 기준일: 관리자가 요금을 마지막으로 확인한 일시(lastVerifiedAt) 중 가장 늦은 것의 한국 날짜.
  // ISO-8601(오프셋 포함) 문자열은 같은 형식끼리 사전순 비교가 시간순이 아닐 수 있어 Date로 바꿔 비교한다
  const latestVerifiedAt = prices.reduce<string | null>(
    (latest, price) =>
      latest === null || new Date(price.lastVerifiedAt) > new Date(latest)
        ? price.lastVerifiedAt
        : latest,
    null,
  )
  const priceDate = formatDataAsOf(latestVerifiedAt)

  return (
    // 구간(계산 기준 요약 → 서비스별 요금 → 플랜 계산) 사이를 gap-section(40/48px) 하나로 띄운다(styles/tokens.css R1·R2).
    // 이전: <div className="flex flex-col gap-5"> (구간 사이 20px가 요금 카드 사이 12px와 비슷해 "서비스별 요금"이 어디서 시작하는지 약했다)
    <div className="flex flex-col gap-section">
      {/* 요약 띠와 "먼저 입력해 주세요" 안내는 같은 이야기(설정값)라 16px로 붙여 한 덩어리로 둔다.
          이전에는 둘이 바깥 묶음의 직접 자식이었다(구간 사이 간격을 크게 하면 서로 멀어져서 감쌌다) */}
      <div className="flex flex-col gap-4">
        {/* 예산·시청 시간 요약 띠: 계산이 무엇을 기준으로 하는지 */}
        <section
          aria-label="계산 기준 요약"
          className="grid gap-3 rounded-xl border border-border bg-card p-4 sm:grid-cols-[1fr_1fr_auto] sm:items-center"
        >
          <div>
            <p className="text-sm text-muted-foreground">월 예산</p>
            <p className="text-xl font-extrabold tabular-nums">
              {formatWon(settings.monthlyBudget)}
            </p>
          </div>
          <div>
            <p className="text-sm text-muted-foreground">월 시청 시간</p>
            <p className="text-xl font-extrabold tabular-nums">
              {formatMinutes(settings.monthlyWatchMinutes)}
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
      </div>

      {/* 이전: 요금 기준일 줄을 요약 띠와 "서비스별 요금" 제목 사이에 따로 두었다. 위아래 간격이 같아(20px) 어느 구역의 설명인지 모호했다(운영 피드백).
          기준일은 아래 요금 목록의 기준이고 "아래에서 내 금액으로 고쳐 주세요"도 목록을 가리키므로, "서비스별 요금" 제목 바로 아래 설명으로 옮겼다.
      <div className="grid gap-1">
        <p className="flex items-start gap-1.75 text-sm text-muted-foreground tabular-nums">
          <CalendarIcon aria-hidden="true" className="mt-0.75 size-4 shrink-0" />
          ...(아래 제목 묶음 안의 문단과 같은 내용)
        </p>
      </div> */}

      {/* 서비스별 요금(Task 076 시안 A 확정): 계산 방식 안내 띠 + 서비스당 카드 한 장.
          계산 방식은 모드에 따라 아이콘·글자가 함께 바뀌어 색에 기대지 않는다. 번들은 마지막 "번들" 카드로 묶인다(계산은 번들을 상품 하나로 본다, PRD FR-19) */}
      {/* 제목과 요금 기준일을 한 묶음(간격 4px)으로 두는 일과 제목 크기(h2 text-xl bold), 제목 묶음과 내용 사이 16px는 공통 PageSection이 맡는다(R3·R4).
          내용(계산 방식 띠 → 요금 목록) 사이는 이전과 같은 12px라 안쪽 div로 감쌌다.
          이전: <section aria-labelledby="pricing-list-heading" className="grid gap-3">
                  <div className="grid gap-1"><h2 id="pricing-list-heading" className="text-lg font-semibold">서비스별 요금</h2>…기준일…</div> */}
      <PageSection
        headingId="pricing-list-heading"
        title="서비스별 요금"
        description={
          // 요금 기준일: 관리자가 요금을 마지막으로 확인한 날. 요금은 바뀔 수 있어 어느 날짜 기준인지 보여 준다
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
        }
      >
        <div className="grid gap-3">
          {/* 계산 방식 안내 띠: "지금 어떻게 계산하는지(굵게) → 그게 무슨 뜻인지 → 반대로 하려면 어디서 바꾸는지(링크)" 순서로 읽힌다.
            이전에는 "표준 요금제로 계산해요"와 "설정에서 바꾸기"만 있어 무엇을 무엇으로 바꾸는지 알 수 없었다(운영 사용 피드백).
            링크 이름에 바뀐 뒤의 상태를 넣어(예: "설정에서 저렴한 요금제로 바꾸기") 링크만 따로 읽어도 뜻이 통하게 했다(낭독기의 링크 목록).
            링크는 설정 화면의 4번 "계산할 요금제" 구역(id="price-mode", onboarding/SettingsForm)으로 바로 내려가 체크박스에 포커스를 준다.
            주소에 search(?mock… 목업 확인용 파라미터)를 그대로 붙이고 hash(#price-mode)로 이동 대상을 알린다 */}
          <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1 rounded-xl border border-border bg-muted px-4 py-2">
            <div className="flex items-start gap-2 py-1">
              {isLowestPriceMode ? (
                <BadgePercentIcon aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
              ) : (
                <ListChecksIcon aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
              )}
              <div className="grid gap-0.5">
                <p className="text-sm font-semibold break-keep">
                  {isLowestPriceMode
                    ? '지금은 가장 저렴한 요금제로 계산해요'
                    : '지금은 표준 요금제로 계산해요'}
                </p>
                <p className="text-sm break-keep text-muted-foreground">
                  {isLowestPriceMode
                    ? '광고형·베이직 같은 저가형까지 포함해 서비스마다 가장 싼 요금제를 써요.'
                    : '광고형·베이직 같은 저가형 요금제는 빼고 계산해요.'}
                </p>
              </div>
            </div>
            <Link
              to={{ pathname: '/settings', search: location.search, hash: '#price-mode' }}
              state={{ from: `/pricing${location.search}` }}
              className="inline-flex min-h-11 items-center rounded-md text-sm font-semibold underline underline-offset-3 focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none max-sm:pl-7"
            >
              {isLowestPriceMode
                ? '설정에서 표준 요금제로 바꾸기'
                : '설정에서 저렴한 요금제로 바꾸기'}
            </Link>
            {/* 이전 문구·링크(무엇을 바꾸는지 드러나지 않았다):
          <p className="flex items-center gap-2 py-2 text-sm font-semibold break-keep">
            {isLowestPriceMode ? '가장 저렴한 요금제로 계산해요' : '표준 요금제로 계산해요'}
          </p>
          <Link to={`/settings${location.search}`} state={{ from: `/pricing${location.search}` }}>설정에서 바꾸기</Link> */}
          </div>
          <PricingList prices={prices} services={services} isLocked={calculation.isPending} />
        </div>
      </PageSection>

      {/* 플랜 계산 구역. 모바일에서는 화면 아래에 고정하고(내용을 가리지 않게 배경·윗선을 둔다),
          넓은 화면에서는 박스 없이 버튼만 오른쪽에 둔다(박스 안에 다른 문구가 없어 빈 공간만 차지했다) */}
      <section
        aria-label="플랜 계산"
        className="grid gap-2 max-md:sticky max-md:bottom-0 max-md:-mx-4 max-md:z-20 max-md:border-t max-md:border-border max-md:bg-card max-md:px-4 max-md:pt-3 max-md:pb-[max(0.75rem,env(safe-area-inset-bottom))]"
      >
        <Button
          type="button"
          size="lg"
          disabled={!isReadyToCalculate || calculation.isPending}
          onClick={calculation.onCalculate}
          className="h-12 w-full text-base md:w-auto md:justify-self-end md:px-6"
        >
          {calculation.isPending ? (
            // loading-spin: 평소 회전, 동작 줄이기면 밝기만 오르내림(styles/tokens.css). 이전: motion-safe:animate-spin
            <LoaderCircleIcon aria-hidden="true" className="loading-spin" />
          ) : (
            <CalculatorIcon aria-hidden="true" />
          )}
          {calculation.isPending ? '계산하는 중' : '3개월 플랜 계산하기'}
        </Button>

        {/* 계산 중 진행 창: 화면 가운데에 닫을 수 없는 창을 띄우고 뒤 화면을 흐리게 덮는다(운영 사용 피드백).
            이전에는 버튼 아래 작은 막대라 화면 아래쪽(모바일 고정 막대)에서만 보여 계산 중인지 놓치기 쉬웠고, 그동안 요금을 고칠 수도 있었다.
            계산이 끝나면 isPending이 false가 되어 창이 저절로 사라진다(성공이면 결과 화면으로 이동, 실패면 아래 오류 안내가 보인다) */}
        <BlockingProgressDialog
          open={calculation.isPending}
          title="플랜을 계산하고 있어요"
          description="찜한 작품과 예산, 시청 시간에 맞춰 3가지 플랜을 찾는 중이에요. 몇 초 걸릴 수 있어요."
          activityLabel="계산하는 중이에요" // 창 아래 "7초째 계산하는 중이에요"
        />
        {/* 이전 진행 안내(버튼 아래 막대):
        <div role="status" aria-live="polite">
          {calculation.isPending && (
            <div className="grid gap-1.5">
              <p className="text-sm font-semibold">3가지 플랜을 계산하고 있어요</p>
              <p className="text-sm text-muted-foreground">
                찜한 작품과 예산, 시청 시간에 맞춰 조합을 찾는 중이에요. 몇 초 걸릴 수 있어요.
              </p>
              <div aria-hidden="true" className="h-1.5 overflow-hidden rounded-full bg-muted">
                <div className="h-full w-1/3 rounded-full bg-primary motion-safe:animate-pulse" />
              </div>
            </div>
          )}
        </div> */}

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
