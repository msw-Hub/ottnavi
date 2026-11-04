import type { ReactNode } from 'react'
import {
  BadgePercentIcon,
  CalculatorIcon,
  CalendarIcon,
  HeartIcon,
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
import { useWishlistSummary } from '@/hooks/useWishlistSummary'
import { formatDataAsOf, formatMinutes, formatWon } from '@/lib/format'

// 계산할 찜이 없을 때의 안내 종류. EMPTY: 찜이 하나도 없음 / ALL_WATCHED: 찜은 있지만 모두 "다 봤음"
type WishlistNoticeKind = 'EMPTY' | 'ALL_WATCHED'

// 안내 한 종류의 글자와 이동할 곳
interface WishlistNoticeContent {
  title: string // 안내 제목(굵은 한 줄)
  description: string // 무엇을 하면 계산할 수 있는지
  linkLabel: string // 이동 버튼 글자
  pathname: string // 이동 버튼이 가는 화면
}

// 종류별 안내 문구. 두 경우 모두 서버가 계산을 400(fieldErrors.wishlist)으로 거절하므로 미리 알려 버튼을 막는다
const WISHLIST_NOTICES: Record<WishlistNoticeKind, WishlistNoticeContent> = {
  EMPTY: {
    title: '찜한 작품을 먼저 담아 주세요',
    description:
      '계산은 찜한 작품을 기준으로 해요. 보고 싶은 작품을 1개 이상 찜하면 계산할 수 있어요.',
    linkLabel: '작품 찾아보기',
    pathname: '/search',
  },
  ALL_WATCHED: {
    title: '계산할 작품이 없어요',
    description:
      "찜한 작품을 모두 '다 봤음'으로 표시했어요. 새 작품을 찜하거나 '다 봤음'을 되돌려 주세요.",
    linkLabel: '찜 목록으로',
    pathname: '/wishlist',
  },
}

// 계산 버튼이 aria-describedby로 가리키는 안내 상자 id. 버튼이 왜 막혔는지 낭독기가 버튼과 함께 읽는다
const SETTINGS_NOTICE_ID = 'pricing-settings-notice'
const WISHLIST_NOTICE_ID = 'pricing-wishlist-notice'

/**
 * 찜 개수로 안내 종류를 고른다. 계산 대상(activeItemCount)이 있으면 안내가 없다(null).
 * 찜 개수를 모르면(불러오는 중·실패) 이 함수를 부르지 않고 안내를 보이지 않는다(호출하는 쪽에서 판단).
 */
function getWishlistNoticeKind(
  totalCount: number,
  activeItemCount: number,
): WishlistNoticeKind | null {
  if (activeItemCount > 0) return null
  return totalCount > 0 ? 'ALL_WATCHED' : 'EMPTY'
}

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
  // 계산할 찜이 있는지(빈 찜 안내용). 기능 폴더끼리 import하지 않아 공유 훅(src/hooks)으로 읽는다
  const wishlistSummary = useWishlistSummary()
  // 찜 개수를 알 때만 안내 종류를 정한다. 불러오는 중이면 안내가 잠깐 떴다 사라지는 깜빡임을 피하려고, 실패면 원래 흐름(서버 400 → 오류 상자)에 맡기려고 null이다
  const wishlistNoticeKind = wishlistSummary.isSuccess
    ? getWishlistNoticeKind(wishlistSummary.data.totalCount, wishlistSummary.data.activeItemCount)
    : null
  // 서버 400(fieldErrors.wishlist) 대비책에서 어떤 문구를 쓸지 고를 때 쓴다. 찜 개수를 모르면 "찜이 없음" 문구로 본다
  const hasAnyWishlist = wishlistSummary.isSuccess && wishlistSummary.data.totalCount > 0
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
        wishlistNoticeKind={wishlistNoticeKind}
        hasAnyWishlist={hasAnyWishlist}
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
  wishlistNoticeKind: WishlistNoticeKind | null // 계산할 찜이 없을 때의 안내 종류. 찜이 있거나 찜 개수를 모르면 null
  hasAnyWishlist: boolean // 찜이 1개 이상 있는지("다 봤음" 포함). 서버 400 대비책의 문구를 고를 때 쓴다
}

// 데이터를 불러온 뒤의 화면
function PricingContent({
  prices,
  settings,
  services,
  calculation,
  wishlistNoticeKind,
  hasAnyWishlist,
}: PricingContentProps) {
  const location = useLocation()
  const isLowestPriceMode = settings.isLowestPriceMode
  // 예산·시청 시간이 비어 있으면 계산을 시작할 수 없다(서버도 400). 먼저 설정하도록 안내하고 버튼을 막는다
  const hasSettings = settings.monthlyBudget !== null && settings.monthlyWatchMinutes !== null
  // 계산할 찜이 없어도 서버가 400으로 거절한다. 같은 방식으로 안내하고 버튼을 막는다
  const wishlistNotice = wishlistNoticeKind ? WISHLIST_NOTICES[wishlistNoticeKind] : null
  // 이전: const isReadyToCalculate = settings.monthlyBudget !== null && settings.monthlyWatchMinutes !== null (예산·시청 시간만 봤다)
  const isReadyToCalculate = hasSettings && wishlistNotice === null
  // 버튼이 막힌 이유를 가리키는 안내 상자 id 목록(보이는 것만). 없으면 undefined라 속성이 붙지 않는다
  const blockedReasonIds =
    [!hasSettings && SETTINGS_NOTICE_ID, wishlistNotice && WISHLIST_NOTICE_ID]
      .filter(Boolean)
      .join(' ') || undefined
  // 서버가 찜 없음으로 400을 돌려준 경우(fieldErrors의 field가 wishlist). 찜 개수를 읽지 못했거나 그 사이 찜이 바뀌어 화면 안내를 지나친 때의 대비책이다.
  // 서버는 예산 없음을 monthlyBudget, 찜 없음을 wishlist 필드로 구분해 보내고 공통 인터셉터가 fieldErrors로 꺼내 준다(api/http.ts)
  const isWishlistRejected =
    isApiError(calculation.error) &&
    calculation.error.status === 400 &&
    calculation.error.fieldErrors.some((fieldError) => fieldError.field === 'wishlist')
  const rejectedNotice = isWishlistRejected
    ? WISHLIST_NOTICES[hasAnyWishlist ? 'ALL_WATCHED' : 'EMPTY']
    : null
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

        {/* 계산을 막는 이유 안내. 예산·시청 시간 안내와 찜 안내가 둘 다 해당하면 8px 간격으로 세로로 쌓는다.
            둘 다 없으면 묶음째 그리지 않는다(빈 묶음도 바깥 gap 한 칸을 차지하므로) */}
        {(!hasSettings || wishlistNotice) && (
          <div className="grid gap-2">
            {/* 이전 조건: {!isReadyToCalculate && (…)} (계산 가능 여부가 예산·시청 시간만 봤다) */}
            {!hasSettings && (
              <Alert id={SETTINGS_NOTICE_ID} className="border-info bg-info-bg text-info">
                <AlertTitle>예산과 시청 시간을 먼저 입력해 주세요</AlertTitle>
                <AlertDescription className="text-info">
                  둘 다 있어야 플랜을 계산할 수 있어요. 위의 &quot;설정 바꾸기&quot;에서 입력해
                  주세요.
                </AlertDescription>
              </Alert>
            )}
            {/* 계산할 찜이 없음 안내. 오류가 아니라 다음 할 일을 알려 주는 글이라 role="note"다
                (shadcn Alert의 기본 role="alert"는 화면을 열자마자 낭독기가 하던 말을 끊고 급하게 읽는다) */}
            {wishlistNotice && (
              <Alert
                id={WISHLIST_NOTICE_ID}
                role="note"
                className="border-info bg-info-bg text-info"
              >
                <HeartIcon aria-hidden="true" />
                <AlertTitle>{wishlistNotice.title}</AlertTitle>
                <AlertDescription className="text-info">
                  {wishlistNotice.description}
                </AlertDescription>
                {/* 버튼은 설명(AlertDescription) 밖에 둔다. 설명 안의 링크는 밑줄 글자 모양이 덧씌워진다(shadcn alert의 [&_a]:underline).
                    col-start-2: 아이콘 열을 비우고 제목·설명과 같은 줄 시작에 맞춘다 */}
                <div className="col-start-2 mt-2">
                  <Button asChild variant="outline" className="h-11">
                    <Link to={{ pathname: wishlistNotice.pathname, search: location.search }}>
                      {wishlistNotice.linkLabel}
                    </Link>
                  </Button>
                </div>
              </Alert>
            )}
          </div>
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
          // 막혔을 때 그 이유(위 안내 상자)를 버튼 설명으로 잇는다. 낭독기가 "흐리게 표시됨" 뒤에 이유를 함께 읽는다
          aria-describedby={blockedReasonIds}
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

        {/* 대비책: 화면 안내를 지나쳐 서버가 찜 없음(400 fieldErrors.wishlist)으로 거절한 경우. 빨간 오류 상자는 그대로 두고
            서버 문구 대신 위 안내와 같은 문구·이동 링크로 바꿔 무엇을 하면 되는지 알린다 */}
        {rejectedNotice && !calculation.isPending && (
          <Alert variant="destructive">
            <AlertTitle>{rejectedNotice.title}</AlertTitle>
            <AlertDescription className="text-destructive/90">
              {rejectedNotice.description}{' '}
              <Link
                to={{ pathname: rejectedNotice.pathname, search: location.search }}
                className="underline underline-offset-3"
              >
                {rejectedNotice.linkLabel}
              </Link>
            </AlertDescription>
          </Alert>
        )}
        {/* 이전 조건: calculation.error != null && !calculation.isPending (찜 없음 400도 여기서 서버 문구로 보였다) */}
        {calculation.error != null && !calculation.isPending && !rejectedNotice && (
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
