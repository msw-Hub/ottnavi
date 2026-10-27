import { useState, type ReactNode } from 'react'
import {
  CalculatorIcon,
  CircleCheckIcon,
  HeartIcon,
  HistoryIcon,
  InfoIcon,
  LoaderCircleIcon,
  RefreshCwIcon,
  SaveIcon,
  // TriangleAlertIcon, // 이전: "다시 계산하기" 실패 안내의 경고 아이콘
} from 'lucide-react'
import { Link, useLocation } from 'react-router'
import { toast } from 'sonner'
import type { OttService } from '@/api/generated/model'
import { isApiError } from '@/api/http'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { LoadingState } from '@/components/common/LoadingState'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { PlanDetail } from '@/features/plan/components/PlanDetail'
import { PlanTypeSelector } from '@/features/plan/components/PlanTypeSelector'
// 이전: useCalculatePlan도 불러왔다("다시 계산하기" 버튼용)
import { useCurrentPlan, usePlanDrafts, useSavePlan } from '@/hooks/usePlanDrafts'
import { mergeIdenticalDrafts } from '@/features/plan/mergeDrafts'
import type { ActivePlan, PlanDraft, PlanType } from '@/api/generated/model'
import { PLAN_TYPE_LABELS } from '@/features/plan/planDisplay'
import { useOttServices } from '@/hooks/useOttServices'
import { formatDateTimeKorean } from '@/lib/format'

// 저장·계산 요청이 실패했을 때 보일 문장. 서버 문장(userMessage)이 있으면 그것을 쓴다
function getFailureMessage(error: unknown): string {
  return isApiError(error) ? error.userMessage : '잠시 후 다시 시도해 주세요.'
}

/**
 * SCR-10 플랜 결과 화면의 본문이다. pages/PlanPage는 이 컴포넌트를 연결만 한다(pages는 얇게, frontend.md).
 *
 * 화면은 항상 서버 상태(초안 GET, 현재 플랜 GET)를 기준으로 그린다. 새로고침·재방문해도 같은 결과가 복원되는 이유다.
 * 위에서 아래로 가르는 순서: 로딩 → 오류 → 초안이 있으면 결과(PlanResult) → 초안이 없고 현재 플랜이 있으면 "내 현재 플랜" → 둘 다 없으면 계산 안내.
 * 저장에 성공하면 초안은 서버에서 사라지고(나머지 DRAFT 삭제) 현재 플랜이 생기므로, 저장 직후에는 자연스럽게 "내 현재 플랜"으로 바뀐다.
 * 이때 "아직 계산한 플랜이 없어요"가 보이면 저장이 실패한 것처럼 오해하므로, 저장 직후에는 완료 안내 띠를 맨 위에 함께 보인다.
 */
export function PlanScreen() {
  const drafts = usePlanDrafts()
  const currentPlan = useCurrentPlan()
  const services = useOttServices()
  // 이전: 결과 화면 오른쪽 위 "다시 계산하기" 버튼용 계산 훅. 버튼을 "찜 목록 고치러 가기" 링크로 바꿔 더는 쓰지 않는다(운영 사용 피드백).
  // 저장 중 409로 다시 계산하는 일은 useSavePlan 훅 안에서 하므로 이 훅이 없어도 그대로 동작한다
  // const calculate = useCalculatePlan()
  const save = useSavePlan()
  const location = useLocation()

  // 방금 저장한 플랜의 유형. 값이 있으면 저장 완료 안내를 보인다
  const [savedPlanType, setSavedPlanType] = useState<PlanType | null>(null)
  // 저장 중 데이터가 바뀌어 자동으로 다시 계산했는지. true면 결과 위에 안내 띠를 보인다
  const [hasRecalculatedNotice, setHasRecalculatedNotice] = useState(false)
  // 저장하려던 초안이 서버에 없었는지(404). 훅이 초안을 다시 불러오므로 안내만 띄운다
  const [hasNotFoundNotice, setHasNotFoundNotice] = useState(false)

  let content: ReactNode
  if (drafts.isPending || currentPlan.isPending || services.isPending) {
    content = <LoadingState message="플랜을 불러오고 있어요" />
  } else if (drafts.isError) {
    content = <ErrorState error={drafts.error} onRetry={() => drafts.refetch()} />
  } else if (currentPlan.isError) {
    content = <ErrorState error={currentPlan.error} onRetry={() => currentPlan.refetch()} />
  } else if (services.isError) {
    content = <ErrorState error={services.error} onRetry={() => services.refetch()} />
  } else if (drafts.data.length > 0) {
    content = (
      <PlanResult
        drafts={drafts.data}
        services={services.data}
        hasCurrentPlan={currentPlan.data != null}
        search={location.search}
        isSaving={save.isPending}
        saveError={save.isError && !hasNotFoundNotice ? save.error : null}
        hasRecalculatedNotice={hasRecalculatedNotice}
        // 이전: "다시 계산하기" 버튼과 이어진 값들(버튼을 찜 목록 링크로 바꿔 쓰지 않는다)
        // isCalculating={calculate.isPending}
        // calculateError={calculate.isError ? calculate.error : null}
        // onRecalculate={() =>
        //   calculate.mutate(undefined, {
        //     onSuccess: () => {
        //       setHasRecalculatedNotice(false)
        //       setHasNotFoundNotice(false)
        //     },
        //   })
        // }
        onSave={(planId) => {
          setHasNotFoundNotice(false)
          save.mutate(
            { planId },
            {
              onSuccess: (result) => {
                if (result.kind === 'saved') {
                  setSavedPlanType(result.plan.planType)
                  setHasRecalculatedNotice(false)
                  toast.success('플랜을 저장했어요')
                  window.scrollTo({ top: 0 })
                } else {
                  // 저장 중 409(데이터 갱신) → 훅이 이미 다시 계산했다. 사용자가 새 결과를 확인하고 다시 저장해야 한다
                  setHasRecalculatedNotice(true)
                  toast.info('데이터가 바뀌어 다시 계산했어요')
                  window.scrollTo({ top: 0 })
                }
              },
              onError: (error) => {
                // 404 PLAN_DRAFT_NOT_FOUND: 훅이 초안을 다시 불러온다. 사용자에게는 다시 계산·선택을 안내한다
                if (isApiError(error) && error.status === 404) setHasNotFoundNotice(true)
              },
            },
          )
        }}
      />
    )
  } else if (currentPlan.data != null) {
    content = (
      <CurrentPlanView
        plan={currentPlan.data}
        services={services.data}
        search={location.search}
        isJustSaved={savedPlanType !== null}
      />
    )
  } else {
    content = (
      <EmptyState
        title="아직 계산한 플랜이 없어요"
        description="요금을 확인하고 계산하면 3개월 플랜 3가지를 보여 드려요."
        action={
          <Button asChild size="lg" className="mt-1 h-11">
            <Link to={`/pricing${location.search}`}>
              <CalculatorIcon aria-hidden="true" />
              요금 확인하고 계산하기
            </Link>
          </Button>
        }
      />
    )
  }

  return (
    <section className="flex flex-col gap-5">
      <h1 className="text-2xl font-semibold">플랜 결과</h1>
      {/* 404: 저장하려던 플랜이 서버에 없다(다시 계산해 지워졌거나 이미 저장됨). 서버 문구 대신 다음 행동을 안내한다 */}
      {hasNotFoundNotice && (
        <Alert variant="destructive" role="alert">
          <AlertTitle>저장하려던 플랜을 찾지 못했어요</AlertTitle>
          <AlertDescription className="text-destructive/90">
            {/* 이전 끝 문장: "…저장하거나, 다시 계산해 주세요." 결과 화면에 "다시 계산하기" 버튼이 없어져 갈 곳을 밝혔다 */}
            다시 계산했거나 이미 저장된 플랜일 수 있어요. 목록을 새로 불러왔으니 플랜을 다시 골라
            저장하거나, 요금 확인이나 찜 목록 화면에서 다시 계산해 주세요.
          </AlertDescription>
        </Alert>
      )}
      {content}
    </section>
  )
}

interface CurrentPlanViewProps {
  plan: ActivePlan // 서버의 현재 플랜(ACTIVE). 초안 필드를 모두 담고 저장 시각이 더해진다
  services: OttService[] // 서비스 목록 API의 7개 서비스(이름 표시용)
  search: string // 목업 확인용 주소 파라미터(?mock…)를 이동 링크에 이어 붙이기 위한 값
  isJustSaved: boolean // 방금 저장해서 이 화면으로 넘어왔는지(완료 안내 띠를 보일지)
}

// 저장해 둔 "내 현재 플랜". 초안 결과와 같은 내용 컴포넌트(PlanDetail)를 읽기 전용으로 재사용한다
function CurrentPlanView({ plan, services, search, isJustSaved }: CurrentPlanViewProps) {
  const serviceNames = new Map(services.map((service) => [service.ottServiceId, service.name]))
  const typeName = PLAN_TYPE_LABELS[plan.planType]
  // 서비스 화면의 다른 날짜("2026년 10월 9일")와 같은 말투로 맞춘 저장 시각(관리자용 숫자 형식 formatDateTime은 쓰지 않는다)
  const savedAt = formatDateTimeKorean(plan.activatedAt)

  return (
    <div className="flex flex-col gap-6">
      {isJustSaved && (
        // 저장 완료는 오류가 아니라 안내라 role="status"로 덮어쓴다. shadcn Alert의 기본값 role="alert"는 낭독기가 하던 말을 끊고 급하게 읽는다
        <Alert role="status" className="border-primary">
          <CircleCheckIcon aria-hidden="true" />
          <AlertTitle className="text-base font-bold">{typeName} 플랜을 저장했어요</AlertTitle>
          <AlertDescription>
            저장한 플랜은 이 기준으로 안내돼요. 조건이 바뀌면 다시 계산해 저장할 수 있어요.
          </AlertDescription>
        </Alert>
      )}

      {/* 변경 내역(SCR-11) 진입점은 저장한 플랜이 있을 때만 의미가 있어(매월 재계산은 저장한 플랜을 기준으로 한다) 이 화면에만 둔다.
          제목 줄 오른쪽(좁은 화면은 아래)에 둬 긴 플랜을 다 내려가지 않아도 보이게 했다. 머리글 메뉴는 늘리지 않는다(Task 068 사용자 결정 5)
          이전: <div className="grid gap-1"> 안에 제목·저장 시각만 있었다 */}
      <div className="flex flex-wrap items-end justify-between gap-x-4 gap-y-1">
        <div className="grid gap-1">
          <h2 className="text-xl font-semibold">내 현재 플랜 · {typeName}</h2>
          {savedAt && (
            <p className="text-sm text-muted-foreground tabular-nums">{savedAt}에 저장</p>
          )}
        </div>
        {/* 이전: 변경 내역 API가 없어 링크를 목업 모드(isMockMode())에서만 보였다. Task 070에서 API를 연결해 조건을 없앴다 */}
        <Link
          to={`/plan/history${search}`}
          className="inline-flex min-h-11 items-center gap-1.5 rounded-md text-sm font-semibold text-primary underline-offset-3 hover:underline focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
        >
          <HistoryIcon aria-hidden="true" className="size-4" />
          변경 내역 보기
        </Link>
      </div>

      <div className="grid gap-6">
        <PlanDetail draft={plan} typeNames={typeName} serviceNames={serviceNames} />
      </div>

      <div className="flex flex-wrap gap-2">
        <Button asChild className="h-11">
          <Link to={`/pricing${search}`}>
            <RefreshCwIcon aria-hidden="true" />
            다시 계산하기
          </Link>
        </Button>
        <Button asChild variant="outline" className="h-11">
          <Link to={`/wishlist${search}`}>찜 목록 보기</Link>
        </Button>
        <Button asChild variant="outline" className="h-11">
          <Link to={`/${search}`}>작품 찾아보기</Link>
        </Button>
      </div>
    </div>
  )
}

interface PlanResultProps {
  drafts: PlanDraft[] // 플랜 유형 3종 초안(선택이 같은 것도 3개 그대로 온다)
  services: OttService[] // 서비스 목록 API의 7개 서비스(이름 표시용)
  hasCurrentPlan: boolean // 이미 저장한 현재 플랜이 있는지(있으면 새로 저장할 때 바뀐다는 안내를 보인다)
  search: string // 목업 확인용 주소 파라미터(?mock…)를 "찜 목록 고치러 가기" 링크에 이어 붙이기 위한 값
  isSaving: boolean // 저장 중인지(저장 도중 자동 재계산도 포함)
  saveError: unknown // 저장 실패 원인. 없으면 null
  hasRecalculatedNotice: boolean // 저장 중 자동 재계산이 있었는지
  // 이전: "다시 계산하기" 버튼용 값들(버튼을 찜 목록 링크로 바꿔 쓰지 않는다)
  // isCalculating: boolean // 다시 계산 중인지
  // calculateError: unknown // 다시 계산 실패 원인. 없으면 null
  // onRecalculate: () => void // "다시 계산하기"를 눌렀을 때
  onSave: (planId: number) => void // 저장 버튼을 눌렀을 때. 합친 그룹이면 그중 첫 초안의 planId를 넘긴다(유형은 서버가 planId로 안다)
}

// 초안이 있을 때의 결과 화면: 유형 선택 → 요약 → 달별 카드 → 남은 작품 → 기준일·출처 → 저장
function PlanResult({
  drafts,
  services,
  hasCurrentPlan,
  search,
  isSaving,
  saveError,
  hasRecalculatedNotice,
  onSave,
}: PlanResultProps) {
  const groups = mergeIdenticalDrafts(drafts)
  // 고른 유형은 화면에서만 쓰는 값이라 컴포넌트 상태로 둔다. 다시 계산해 그룹이 바뀌어 고른 유형이 사라졌다면 첫 그룹으로 돌아간다
  const [pickedType, setPickedType] = useState<PlanType | null>(null)
  const selectedGroup = groups.find((group) => group.planTypes[0] === pickedType) ?? groups[0]
  const { draft } = selectedGroup
  const typeNames = selectedGroup.planTypes
    .map((planType) => PLAN_TYPE_LABELS[planType])
    .join(' · ')
  // 이전: const isBusy = isCalculating || isSaving (다시 계산 버튼이 없어져 저장 중일 때만 바쁘다)
  const isBusy = isSaving

  const serviceNames = new Map(services.map((service) => [service.ottServiceId, service.name]))

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-muted-foreground">
          찜한 작품과 예산·시청 시간으로 계산한 3개월 플랜 3가지예요. 하나를 골라 저장하세요.
          {/* 바뀐다는 안내는 저장 버튼 바로 위 안내 띠로 옮겼다(누르기 직전에 읽히게, 067 검수 제안 1) */}
          {/* {hasCurrentPlan && ' 저장하면 지금 저장해 둔 플랜이 이 플랜으로 바뀌어요.'} */}
        </p>
        {/* 결과가 마음에 들지 않을 때 할 일은 같은 조건으로 다시 계산하는 것이 아니라 찜(조건)을 고치는 것이라,
            같은 자리를 "찜 목록 고치러 가기" 링크로 바꿨다(운영 사용 피드백). 찜을 고친 뒤 계산은 찜 목록·요금 확인 화면에서 한다.
            저장 중에는 화면을 떠나지 않게 막는다(링크는 disabled가 없어 aria-disabled + 누름 무시로 막는다) */}
        <Button
          asChild
          variant="outline"
          className="h-11 aria-disabled:pointer-events-none aria-disabled:opacity-50"
        >
          <Link
            to={`/wishlist${search}`}
            aria-disabled={isBusy || undefined}
            tabIndex={isBusy ? -1 : undefined}
          >
            <HeartIcon aria-hidden="true" />찜 목록 고치러 가기
          </Link>
        </Button>
        {/* 이전 "다시 계산하기" 버튼(같은 조건으로 다시 계산):
        <Button type="button" variant="outline" disabled={isBusy} onClick={onRecalculate} className="h-11">
          {isCalculating ? (
            <LoaderCircleIcon aria-hidden="true" className="motion-safe:animate-spin" />
          ) : (
            <RefreshCwIcon aria-hidden="true" />
          )}
          {isCalculating ? '계산하는 중' : '다시 계산하기'}
        </Button> */}
      </div>

      {/* 진행 안내: 영역은 항상 두고 안의 글자만 바꿔야 낭독기(aria-live)가 읽어 준다 */}
      <div role="status" aria-live="polite" className={isBusy ? 'block' : 'sr-only'}>
        {/* 이전: 다시 계산 중 안내(버튼이 없어져 쓰지 않는다)
        {isCalculating && (
          <p className="flex items-center gap-2 text-sm font-semibold">
            <LoaderCircleIcon aria-hidden="true" className="size-4 motion-safe:animate-spin" />
            3가지 플랜을 다시 계산하고 있어요
          </p>
        )} */}
        {isSaving && (
          <p className="flex items-center gap-2 text-sm font-semibold">
            <LoaderCircleIcon aria-hidden="true" className="size-4 motion-safe:animate-spin" />
            플랜을 저장하고 있어요. 데이터가 바뀌었다면 다시 계산해요
          </p>
        )}
      </div>

      {/* 저장 중 데이터가 바뀌어 다시 계산한 경우: 결과가 달라졌을 수 있어 확인 후 다시 저장하게 안내한다 */}
      {/* role="status" 영역은 항상 DOM에 두고 안의 내용만 바꾼다. 조건부로 영역째 새로 만들면 낭독기가 "나중에 생긴 영역"의 글자를 읽지 않는 경우가 많다.
          비어 있을 때는 높이가 없어 레이아웃에 영향이 없다(flex gap은 빈 요소도 칸을 차지하므로 empty:hidden으로 감춘다) */}
      <div role="status" className="empty:hidden">
        {hasRecalculatedNotice && (
          <p className="flex items-start gap-2 rounded-xl border border-info bg-info-bg px-4 py-3 text-info">
            <InfoIcon aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
            <span className="font-semibold">
              데이터가 바뀌어 다시 계산했어요. 결과를 확인하고 저장해 주세요
            </span>
          </p>
        )}
      </div>

      {/* 이전: "다시 계산하기" 실패 안내(버튼이 없어져 쓰지 않는다)
      {calculateError != null && (
        <Alert variant="destructive">
          <TriangleAlertIcon aria-hidden="true" />
          <AlertTitle>플랜을 다시 계산하지 못했어요</AlertTitle>
          <AlertDescription className="text-destructive/90">
            {getFailureMessage(calculateError)}
          </AlertDescription>
        </Alert>
      )} */}

      <PlanTypeSelector
        groups={groups}
        selectedType={selectedGroup.planTypes[0]}
        onSelect={setPickedType}
        isBusy={isBusy}
      />

      <div
        className={isBusy ? 'grid gap-6 opacity-60 transition-opacity' : 'grid gap-6'}
        aria-busy={isBusy}
      >
        <PlanDetail draft={draft} typeNames={typeNames} serviceNames={serviceNames} />
      </div>

      {/* 저장 구역. 모바일·데스크톱 모두 화면 아래에 고정해 긴 결과를 훑은 뒤에도 바로 저장할 수 있게 한다(067 검수 제안 2: 데스크톱도 고정).
          main의 좌우 여백(px-4)만큼 -mx-4로 넓혀 막대가 본문 폭을 꽉 채운다. 내용 끝에서는 푸터를 가리지 않고 함께 스크롤된다(sticky).
          데스크톱은 버튼을 오른쪽에 둔다(md:justify-items-end).
          이전(모바일만 고정): className="grid gap-2 max-md:sticky max-md:bottom-0 max-md:-mx-4 max-md:z-20 max-md:border-t max-md:border-border max-md:bg-card max-md:px-4 max-md:pt-3 max-md:pb-[max(0.75rem,env(safe-area-inset-bottom))] md:justify-items-end" */}
      <section
        aria-label="플랜 저장"
        className="sticky bottom-0 z-20 -mx-4 grid gap-2 border-t border-border bg-card px-4 pt-3 pb-[max(0.75rem,env(safe-area-inset-bottom))] md:justify-items-end"
      >
        {/* 이미 저장한 플랜이 있으면 누르기 직전에 "바뀐다"는 사실을 아이콘+글자 안내 띠로 알린다(067 검수 제안 1).
            경고(빨강·노랑)가 아니라 참고 안내라 info 색을 쓴다. 데스크톱에서도 버튼 폭이 아니라 막대 폭을 꽉 채운다(justify-self-stretch) */}
        {hasCurrentPlan && (
          <p className="flex items-start gap-2 justify-self-stretch rounded-lg border border-info bg-info-bg px-3 py-2 text-sm text-info">
            <InfoIcon aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
            <span className="break-keep">저장하면 지금 저장해 둔 플랜이 이 플랜으로 바뀌어요.</span>
          </p>
        )}
        {saveError != null && !isSaving && (
          <p role="alert" className="text-sm text-destructive">
            저장하지 못했어요. {getFailureMessage(saveError)}
          </p>
        )}
        <Button
          type="button"
          size="lg"
          disabled={isBusy}
          onClick={() => onSave(draft.planId)}
          className="h-12 w-full text-base md:w-auto md:px-6"
        >
          {isSaving ? (
            <LoaderCircleIcon aria-hidden="true" className="motion-safe:animate-spin" />
          ) : (
            <SaveIcon aria-hidden="true" />
          )}
          {isSaving ? '저장하는 중' : `${typeNames} 플랜으로 저장하기`}
        </Button>
      </section>
    </div>
  )
}
