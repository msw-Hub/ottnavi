import { useState, type ReactNode } from 'react'
import { InfoIcon } from 'lucide-react'
import { toast } from 'sonner'
import { ApiError } from '@/api/http'
import { DataAsOf } from '@/components/common/DataAsOf'
import { DataInsufficientTag } from '@/components/common/DataInsufficientTag'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { EstimatedTag } from '@/components/common/EstimatedTag'
import { LoadingState } from '@/components/common/LoadingState'
import { PosterCard } from '@/components/common/PosterCard'
import { PosterImage } from '@/components/common/PosterImage'
import { PriceDisplay } from '@/components/common/PriceDisplay'
import { ProviderStatusBadge } from '@/components/common/ProviderStatusBadge'
import { ServiceStatusRow, type ServiceStatusEntry } from '@/components/common/ServiceStatusRow'
import { SourceAttribution } from '@/components/common/SourceAttribution'
import type { AvailabilityStatus, PriceSource } from '@/components/common/displayTypes'
import { Button } from '@/components/ui/button'
import { AdminNav } from '@/features/admin/components/AdminNav'
import { MappingStateBadge } from '@/features/admin/components/MappingStateBadge'
import { PhaseLaterTag } from '@/features/admin/components/PhaseLaterTag'
import { RunStatusBadge } from '@/features/admin/components/RunStatusBadge'
import { StaleVerifiedTag } from '@/features/admin/components/StaleVerifiedTag'
import type { MappingDisplayState, RunDisplayStatus } from '@/features/admin/types'
import { getErrorMessage } from '@/lib/errorMessages'
import { formatMinutes } from '@/lib/format'
import { useMockRole } from '@/hooks/useMockRole'
import { switchMockRole, type MockRole } from '@/mocks/mockSession'

/*
 * 개발 모드에서만 열리는 공통 컴포넌트 확인 화면(/__dev/components, Task 026).
 * 라우터가 import.meta.env.DEV일 때만 이 화면을 등록하므로 운영 빌드에는 들어가지 않는다(app/router.tsx).
 * 모든 공통 표시 컴포넌트와 상태를 한 화면에 모아 V-F(브라우저 확인)와 Task 037 스크린샷 비교에 쓴다.
 * 구성은 확정 시안 D2c(.playwright-mcp/directions-dark/direction-D2c.html)를 따랐다. 작품·제공처는 모두 예시 데이터다.
 */

// 제공 상태 범례(시안 1절). 각 상태가 무슨 뜻인지 글로 설명한다
const STATUS_LEGEND: { status: AvailabilityStatus; description: string }[] = [
  { status: 'AVAILABLE', description: '구독(정액제)으로 볼 수 있다고 확인된 경우' },
  { status: 'NOT_AVAILABLE', description: '구독으로 제공하지 않음(대여·구매만 있는 경우 포함)' },
  { status: 'UNKNOWN', description: '한국 데이터가 없거나 수집 범위 밖이라 알 수 없음' },
]

// 7개 서비스 예시(서비스 이름·ID·데이터 충분도는 계약 example과 같다). 쿠팡플레이만 데이터 부족이다
const SAMPLE_SERVICE_STATUSES: ServiceStatusEntry[] = [
  { service: { ottServiceId: 1, name: '넷플릭스', dataQuality: 'NORMAL' }, status: 'AVAILABLE' },
  { service: { ottServiceId: 2, name: '디즈니+', dataQuality: 'NORMAL' }, status: 'NOT_AVAILABLE' },
  {
    service: { ottServiceId: 3, name: '애플 TV+', dataQuality: 'NORMAL' },
    status: 'NOT_AVAILABLE',
  },
  { service: { ottServiceId: 4, name: '티빙', dataQuality: 'NORMAL' }, status: 'NOT_AVAILABLE' },
  { service: { ottServiceId: 5, name: '웨이브', dataQuality: 'NORMAL' }, status: 'NOT_AVAILABLE' },
  { service: { ottServiceId: 6, name: '왓챠', dataQuality: 'NORMAL' }, status: 'UNKNOWN' },
  {
    service: { ottServiceId: 7, name: '쿠팡플레이', dataQuality: 'INSUFFICIENT' },
    status: 'UNKNOWN',
  },
]

/*
 * 예시 포스터 이미지(시안 D2c와 같은 그림). 외부(TMDB) 이미지를 요청하지 않으려고 주소 안에 그림을 직접 담은 data URI를 쓴다.
 * 안의 색 값은 포스터 "그림 내용"이지 화면 디자인 값이 아니라 디자인 토큰 규칙의 대상이 아니다. 개발 확인용 화면에서만 쓴다.
 */
const SAMPLE_POSTER_URL =
  'data:image/svg+xml;utf8,%3Csvg%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%20viewBox%3D%220%200%20200%20300%22%3E%3Cdefs%3E%3ClinearGradient%20id%3D%22g%22%20x1%3D%220%22%20y1%3D%220%22%20x2%3D%220%22%20y2%3D%221%22%3E%3Cstop%20offset%3D%220%22%20stop-color%3D%22%230b1a2a%22%2F%3E%3Cstop%20offset%3D%220.6%22%20stop-color%3D%22%231d3b53%22%2F%3E%3Cstop%20offset%3D%221%22%20stop-color%3D%22%23c2410c%22%2F%3E%3C%2FlinearGradient%3E%3C%2Fdefs%3E%3Crect%20width%3D%22200%22%20height%3D%22300%22%20fill%3D%22url(%23g)%22%2F%3E%3Ccircle%20cx%3D%22100%22%20cy%3D%22120%22%20r%3D%2246%22%20fill%3D%22none%22%20stroke%3D%22%23fde68a%22%20stroke-width%3D%2210%22%2F%3E%3Crect%20x%3D%2258%22%20y%3D%22200%22%20width%3D%2284%22%20height%3D%2212%22%20rx%3D%226%22%20fill%3D%22%23fde68a%22%2F%3E%3C%2Fsvg%3E'

// 포스터 카드 예시(시안 3절): 이미지 있음 / 없음 / 긴 제목
const SAMPLE_POSTERS = [
  {
    id: 'squid-game',
    title: '오징어 게임',
    posterUrl: SAMPLE_POSTER_URL,
    subtitle: '드라마 · 2021',
  },
  { id: 'no-poster', title: '예시 영화 제목', posterUrl: null, subtitle: '영화 · 2019' },
  {
    id: 'long-title',
    title: '제목이 아주 길어서 두 줄 이상으로 넘어가는 예시 작품: 부제까지 포함',
    posterUrl: null,
    subtitle: '드라마 · 2024',
  },
]

// 가격 표시 예시(시안 4절): 가격 출처 4종
const SAMPLE_PRICES: {
  id: string
  serviceName: string
  amount: number
  priceSource: PriceSource
  periodLabel?: string
  note: string
}[] = [
  {
    id: 'netflix',
    serviceName: '넷플릭스',
    amount: 13500,
    priceSource: 'ADMIN',
    note: '요금표의 기본 금액',
  },
  {
    id: 'tving',
    serviceName: '티빙',
    amount: 12000,
    priceSource: 'USER_OVERRIDE',
    note: '내가 고친 금액 · 기본 13,500원',
  },
  {
    id: 'wavve',
    serviceName: '웨이브',
    amount: 0,
    priceSource: 'ALREADY_PAID',
    periodLabel: '이번 달',
    note: '이미 구독 중이라 이번 달 비용에 넣지 않음',
  },
  {
    id: 'disney',
    serviceName: '디즈니+',
    amount: 0,
    priceSource: 'FREE',
    note: '가족 공유 등 무료로 이용 중',
  },
]

// 관리자 수집 실행 상태 4종과 각 상태의 뜻(Task 029)
const RUN_STATUS_LEGEND: { status: RunDisplayStatus; description: string }[] = [
  { status: 'RUNNING', description: '수집이 진행 중' },
  { status: 'COMPLETED', description: '끝까지 성공' },
  { status: 'FAILED', description: '오류로 멈춤(복구한 실행 포함). [다시 실행]으로 이어서 수집' },
  { status: 'STOPPED', description: '서버가 멈춰 기록만 남은 실행(서버가 판정). [복구] 대상' },
]

// 관리자 제공처 매핑 상태 3종과 각 상태의 뜻(Task 029)
const MAPPING_STATE_LEGEND: { state: MappingDisplayState; description: string }[] = [
  { state: 'UNMAPPED', description: '아직 확인하지 않은 제공처. 목록 맨 위에 강조' },
  { state: 'MAPPED', description: '7개 서비스 중 하나에 연결됨' },
  { state: 'IGNORED', description: '확인했고 쓰지 않기로 함(예: 대여·구매 전용 스토어)' },
]

// 오류 상태 예시에 넘길 오류. 공통 인터셉터가 만드는 것과 같은 ApiError 모양으로 외부 데이터 오류를 흉내 낸다
const SAMPLE_API_ERROR = new ApiError({
  kind: 'problem',
  status: 502,
  errorCode: 'EXTERNAL_API_ERROR',
  title: 'Bad Gateway',
  detail: null,
  fieldErrors: [],
  userMessage: getErrorMessage('EXTERNAL_API_ERROR'),
})

interface PreviewSectionProps {
  id: string // 제목(h2)의 id. 구역(section)의 이름으로 연결한다
  title: string // 구역 제목
  children: ReactNode
}

// 확인 화면의 한 구역(카드). 시안의 .section 모양이다
function PreviewSection({ id, title, children }: PreviewSectionProps) {
  return (
    <section aria-labelledby={id} className="rounded-xl border border-border bg-card p-4 sm:p-5">
      <h2 id={id} className="mb-3.5 text-lg font-extrabold tracking-tight">
        {title}
      </h2>
      {children}
    </section>
  )
}

/** 공통 표시 컴포넌트 확인 화면(개발 모드 전용). */
export function ComponentsPreviewPage() {
  // 흑백 보기 여부. 켜면 화면 전체에 grayscale 필터를 씌워 "색 없이도 3상태가 구분되는지" 눈으로 확인한다
  const [isGrayscale, setIsGrayscale] = useState(false)
  // 다시 시도 버튼을 누른 횟수. 버튼이 실제로 onRetry를 부르는지 화면에서 확인하려고 둔다
  const [retryCount, setRetryCount] = useState(0)
  // 목업의 로그인 역할. 머리글의 개발용 전환(app/MockRoleSwitcher)과 같은 값을 구독해 둘이 항상 같은 값을 보인다(Task 027 2회차)
  const mockRole = useMockRole()

  // 목업 역할을 바꾼다. 머리글 전환과 같은 함수(switchMockRole)를 쓴다
  function handleMockRoleChange(role: MockRole) {
    switchMockRole(role)
  }
  // 1회차의 임시 구현(역할을 useState로 복사해 둠). 머리글 전환이 생겨 같은 변수를 구독하도록 바꿨다. 참고용으로 남긴다
  // const [mockRole, setMockRoleState] = useState<MockRole>(getMockRole)
  // function handleMockRoleChange(role: MockRole) {
  //   setMockRole(role)
  //   setMockRoleState(role)
  // }

  return (
    <div className={isGrayscale ? 'grid gap-5 grayscale' : 'grid gap-5'}>
      <div>
        <h1 className="mb-1 text-2xl font-bold">공통 컴포넌트 확인</h1>
        <p className="mb-3 text-muted-foreground">
          개발 모드에서만 열리는 확인용 화면입니다. 작품·제공처는 모두 예시 데이터입니다.
        </p>
        <div className="flex flex-wrap items-center gap-3">
          <Button
            type="button"
            variant="outline"
            size="lg"
            className="border-input bg-transparent"
            aria-pressed={isGrayscale}
            onClick={() => setIsGrayscale((previous) => !previous)}
          >
            {isGrayscale ? '컬러로 보기' : '흑백으로 보기'}
          </Button>
          <Button
            type="button"
            variant="outline"
            size="lg"
            className="border-input bg-transparent"
            onClick={() => toast.error(getErrorMessage('RATE_LIMIT_EXCEEDED'))}
          >
            오류 토스트 띄우기
          </Button>
        </div>
      </div>

      <PreviewSection id="preview-status" title="1. 제공 상태 배지">
        <div className="flex flex-wrap items-center gap-3">
          <ProviderStatusBadge status="AVAILABLE" />
          <ProviderStatusBadge status="NOT_AVAILABLE" />
          <ProviderStatusBadge status="UNKNOWN" />
          <DataInsufficientTag />
          <EstimatedTag />
        </div>
        <ul className="mt-3.5 grid gap-2 text-[0.9375rem]">
          {STATUS_LEGEND.map(({ status, description }) => (
            <li key={status} className="flex items-start gap-3">
              <span className="min-w-19 flex-none">
                <ProviderStatusBadge status={status} />
              </span>
              <span className="pt-0.5 text-muted-foreground">{description}</span>
            </li>
          ))}
        </ul>
      </PreviewSection>

      <PreviewSection id="preview-detail" title="2. 작품 상세 예시 — 7개 서비스 제공 상태">
        <div className="grid grid-cols-[6.5rem_1fr] items-start gap-3.5 sm:grid-cols-[9rem_1fr] sm:gap-5">
          <div className="sm:row-span-2">
            {/* 상세 화면은 제목을 옆에 따로 크게 보이므로 제목 없는 PosterImage만 쓴다 */}
            <PosterImage title="오징어 게임" posterUrl={SAMPLE_POSTER_URL} />
          </div>
          <div>
            <p className="text-[1.375rem] leading-tight font-bold">예시 작품: 오징어 게임</p>
            <p className="mt-1 mb-3 text-[0.9375rem] text-muted-foreground tabular-nums">
              드라마 · 2021 · 시즌 1 · 총 {formatMinutes(510)} <EstimatedTag />
            </p>
          </div>
          <div className="col-span-2 sm:col-span-1 sm:col-start-2">
            <ServiceStatusRow heading="구독 제공 상태" entries={SAMPLE_SERVICE_STATUSES} />
            <div className="mt-4 grid gap-1.5 border-t border-border pt-3">
              <p className="flex items-start gap-1.75 text-sm text-muted-foreground">
                <InfoIcon aria-hidden="true" className="mt-0.75 size-4 shrink-0" />
                <span>
                  <strong className="text-foreground">확인된 범위</strong>의 정보이며 실제 서비스와
                  다를 수 있습니다.
                </span>
              </p>
              <DataAsOf value="2026-10-09" />
              <SourceAttribution />
            </div>
          </div>
        </div>
      </PreviewSection>

      <PreviewSection id="preview-poster" title="3. 포스터 카드 — 이미지 있음 / 없음">
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-[repeat(auto-fill,minmax(9rem,1fr))]">
          {SAMPLE_POSTERS.map(({ id, title, posterUrl, subtitle }) => (
            <PosterCard
              key={id}
              title={title}
              posterUrl={posterUrl}
              subtitle={subtitle}
              to="/__dev/components"
            />
          ))}
        </div>
      </PreviewSection>

      <PreviewSection id="preview-price" title="4. 가격 표시 — 가격 출처 4종">
        <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          {SAMPLE_PRICES.map(({ id, serviceName, amount, priceSource, periodLabel, note }) => (
            <li
              key={id}
              className="grid gap-1 rounded-lg border border-border bg-background px-3.5 py-3"
            >
              <span className="text-[0.9375rem] font-semibold">{serviceName}</span>
              <PriceDisplay amount={amount} priceSource={priceSource} periodLabel={periodLabel} />
              <span className="text-[0.8125rem] text-muted-foreground">{note}</span>
            </li>
          ))}
        </ul>
      </PreviewSection>

      <PreviewSection id="preview-states" title="5. 로딩 · 오류 · 빈 상태">
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <LoadingState message="제공처 정보를 불러오는 중입니다" />
          <ErrorState
            error={SAMPLE_API_ERROR}
            onRetry={() => setRetryCount((count) => count + 1)}
          />
          <EmptyState
            title="검색 결과가 없습니다"
            description="제목을 다르게 입력하거나 원제로 검색해 보세요."
          />
        </div>
        <p className="mt-3 text-[0.8125rem] text-muted-foreground" aria-live="polite">
          다시 시도 누른 횟수: {retryCount}
        </p>
      </PreviewSection>

      <PreviewSection id="preview-data-as-of" title="6. 데이터 기준일 — 정상 / 잘못된 값">
        <div className="grid gap-1.5">
          <DataAsOf value="2026-10-09" />
          {/* 잘못된 값 두 줄이 무엇을 흉내 내는지 적어 둔다(없는 날짜, 빈 값) */}
          <DataAsOf value="2026-02-30" />
          <p className="-mt-1 text-[0.8125rem] text-muted-foreground">위 줄: 없는 날짜(2월 30일)</p>
          <DataAsOf value={null} />
          <p className="-mt-1 text-[0.8125rem] text-muted-foreground">위 줄: 빈 값(날짜 없음)</p>
        </div>
      </PreviewSection>

      <PreviewSection id="preview-mock-role" title="7. 목업 로그인 역할 (개발 확인용)">
        <p className="mb-3 text-sm text-muted-foreground">
          머리글의 &ldquo;개발용&rdquo; 전환과 같은 값입니다. 비로그인(GUEST)에서 작품 상세의 찜을
          누르면 로그인 화면으로 이동하고, 회원(USER)에서는 찜이 됩니다. 이 값은 새로고침하면
          비로그인으로 돌아갑니다.
        </p>
        <div role="group" aria-label="목업 로그인 역할" className="flex flex-wrap gap-2">
          {(['GUEST', 'USER'] as const).map((role) => (
            <Button
              key={role}
              type="button"
              variant="outline"
              size="lg"
              className="border-input bg-transparent"
              aria-pressed={mockRole === role}
              onClick={() => handleMockRoleChange(role)}
            >
              {role === 'GUEST' ? '비로그인(GUEST)' : '회원(USER)'}
            </Button>
          ))}
        </div>
        <p className="mt-3 text-[0.8125rem] text-muted-foreground" aria-live="polite">
          현재 역할: {mockRole}
        </p>
      </PreviewSection>

      {/* 관리자 화면(SCR-14~16)의 표시 컴포넌트 모음(Task 029). 화면 조립 전에 모양과 흑백 구분을 먼저 확인하려고 둔다 */}
      <PreviewSection id="preview-admin" title="8. 관리자 표시 컴포넌트 (Task 029)">
        <div className="grid gap-5">
          <div>
            <h3 className="mb-2 text-sm font-semibold">관리자 메뉴</h3>
            {/* 이 확인 화면 주소는 관리자 경로가 아니라 "현재 메뉴" 밑줄은 보이지 않는다. 숫자 2는 예시 값이다 */}
            <AdminNav unmappedProviderCount={2} />
          </div>

          <div>
            <h3 className="mb-2 text-sm font-semibold">수집 실행 상태</h3>
            <ul className="grid gap-2 text-[0.9375rem]">
              {RUN_STATUS_LEGEND.map(({ status, description }) => (
                <li key={status} className="flex items-start gap-3">
                  <span className="min-w-22 flex-none">
                    <RunStatusBadge status={status} />
                  </span>
                  <span className="pt-0.5 text-muted-foreground">{description}</span>
                </li>
              ))}
            </ul>
          </div>

          <div>
            <h3 className="mb-2 text-sm font-semibold">제공처 매핑 상태</h3>
            <ul className="grid gap-2 text-[0.9375rem]">
              {MAPPING_STATE_LEGEND.map(({ state, description }) => (
                <li key={state} className="flex items-start gap-3">
                  <span className="min-w-26 flex-none">
                    <MappingStateBadge state={state} />
                  </span>
                  <span className="pt-0.5 text-muted-foreground">{description}</span>
                </li>
              ))}
            </ul>
          </div>

          <div>
            <h3 className="mb-2 text-sm font-semibold">미매핑 행 강조 예시</h3>
            {/* 왼쪽 4px 막대(attention)는 보조 표시이고, 뜻은 배지의 아이콘·글자가 전한다 */}
            <div className="flex flex-wrap items-center gap-x-4 gap-y-2 rounded-lg border border-l-4 border-border border-l-attention-border bg-background px-3.5 py-3 text-sm">
              <MappingStateBadge state="UNMAPPED" />
              <span className="tabular-nums">119</span>
              <span>Amazon Prime Video (예시)</span>
            </div>
          </div>

          <div>
            <h3 className="mb-2 text-sm font-semibold">태그</h3>
            <div className="flex flex-wrap items-center gap-3">
              <PhaseLaterTag />
              <PhaseLaterTag phase="3단계" />
              <PhaseLaterTag label="2단계부터 제공" />
              <StaleVerifiedTag lastVerifiedDate="2026-06-30" />
              <StaleVerifiedTag lastVerifiedDate={null} />
            </div>
            <p className="mt-2 text-[0.8125rem] text-muted-foreground">
              마지막 태그: 확인일 값이 없을 때
            </p>
          </div>
        </div>
      </PreviewSection>
    </div>
  )
}
