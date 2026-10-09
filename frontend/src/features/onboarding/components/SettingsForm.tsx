import type { ReactNode } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { LoaderCircleIcon } from 'lucide-react'
import { useForm, useWatch } from 'react-hook-form'
import { useLocation, useNavigate } from 'react-router'
import { toast } from 'sonner'
import type { OttService } from '@/api/generated/model'
import { isApiError } from '@/api/http'
import { MockVariantBanner } from '@/components/common/MockVariantBanner'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { buildDefaultValues } from '@/features/onboarding/defaultValues'
import { RequiredMark } from '@/features/onboarding/components/RequiredMark'
import { ServiceSubscriptionRow } from '@/features/onboarding/components/ServiceSubscriptionRow'
import { WatchTimeField } from '@/features/onboarding/components/WatchTimeField'
import { useSaveUserSettings } from '@/hooks/useUserSettings'
import type { UserSettings } from '@/features/onboarding/mockTypes'
import {
  onboardingFormSchema,
  toOptionalNumber,
  toSaveSettingsRequest,
  type OnboardingFormValues,
} from '@/features/onboarding/schema'
import { formatWon } from '@/lib/format'
import { getMockWatchVariant } from '@/lib/mockScenario'
import { getSafeRedirectPath } from '@/lib/safeRedirect'

interface SectionCardProps {
  stepNumber: number // 위에서부터 몇 번째 구역인지(1부터). 입력 순서를 눈에 보이게 한다
  title: string // 구역 제목
  description: string // 구역 안내(왜 묻는지)
  children: ReactNode // 구역의 입력 칸들
  banner?: ReactNode // 구역 제목 위에 놓는 검토용 띠 등
  notice?: ReactNode // 구역 안내 아래, 입력 칸 위에 놓는 보충 설명(선택지의 뜻 등)
}

// 입력 구역 하나를 카드로 묶는다. 번호는 장식이 아니라 순서 안내라 글자로 "1." 대신 원 안 숫자로 보인다
function SectionCard({
  stepNumber,
  title,
  description,
  children,
  banner,
  notice,
}: SectionCardProps) {
  return (
    <section aria-labelledby={`settings-section-${stepNumber}`} className="grid gap-3">
      {banner}
      <Card className="bg-card ring-1 ring-border">
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <span
              aria-hidden="true"
              className="grid size-6 shrink-0 place-content-center rounded-full bg-primary text-sm font-bold text-primary-foreground tabular-nums"
            >
              {stepNumber}
            </span>
            <h2 id={`settings-section-${stepNumber}`} className="text-lg font-semibold">
              {title}
            </h2>
          </CardTitle>
          <CardDescription>{description}</CardDescription>
          {notice}
        </CardHeader>
        <CardContent className="grid gap-0 divide-y divide-border">{children}</CardContent>
      </Card>
    </section>
  )
}

interface SettingsFormProps {
  settings: UserSettings // 불러온 내 설정(온보딩 전이면 빈 설정)
  services: OttService[] // 서비스 목록 API의 7곳(displayOrder 순으로 정렬해 받는다)
}

/**
 * 온보딩·내 설정 폼(SCR-07). 서비스 이용 상태 → 월 시청 시간 → 월 예산 → 저장 순서의 구역 카드로 이루어진다.
 *
 * 폼 상태는 React Hook Form, 검증은 Zod 스키마(onboarding/schema.ts)가 맡는다. 이 컴포넌트는 입력 칸을 배치하고
 * 저장 성공 뒤 원래 보던 화면(location.state.from)으로 돌려보내는 일만 한다.
 * noValidate: 브라우저 기본 검증 말풍선 대신 Zod 오류 문장을 칸 아래에 보이게 한다(문장이 서비스 말투로 통일된다).
 */
export function SettingsForm({ settings, services }: SettingsFormProps) {
  const navigate = useNavigate()
  const location = useLocation()
  const save = useSaveUserSettings()

  const {
    register,
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<OnboardingFormValues>({
    resolver: zodResolver(onboardingFormSchema),
    defaultValues: buildDefaultValues(settings, services),
    shouldFocusError: false, // 기본 포커스는 쓰지 않고 아래 focusFirstInvalidField가 화면 순서로 직접 옮긴다
  })
  const monthlyBudget = useWatch({ control, name: 'monthlyBudget' })

  // 저장 성공 뒤 이동할 곳: 로그인 전에 보던 화면(from)이 이 사이트 안의 경로면 거기로, 없으면 첫 화면(/)으로 간다.
  // from은 주소로 올 수 있는 값이라 getSafeRedirectPath로 검사한다(오픈 리다이렉트 방지). 없을 때는 목업 확인용 주소 파라미터(?mock…)를 이어 준다
  function goBackToPreviousScreen() {
    const from = getSafeRedirectPath((location.state as { from?: unknown } | null)?.from, '')
    if (from) navigate(from)
    else navigate({ pathname: '/', search: location.search })
  }

  // 검증에 실패하면 화면 위에서부터 첫 번째 오류 칸으로 포커스를 옮긴다.
  // React Hook Form 기본 포커스는 칸 등록 순서를 따라 화면 순서와 어긋날 수 있고, 숨겨 둔 라디오 칸에는 포커스 표시가 안 보인다.
  // 그래서 오류 칸(aria-invalid=true)을 화면 순서(DOM 순서)로 찾아 직접 옮기고, 라디오 묶음이면 묶음이 보이도록 화면을 스크롤한다.
  function focusFirstInvalidField(form: HTMLFormElement | null) {
    const firstInvalid = form?.querySelector<HTMLElement>('[aria-invalid="true"]')
    if (!firstInvalid) return
    firstInvalid.focus()
    const scrollTarget = firstInvalid.closest('fieldset') ?? firstInvalid
    scrollTarget.scrollIntoView({ block: 'center', behavior: 'smooth' })
  }

  const onSubmit = handleSubmit(
    (values) => {
      save.mutate(toSaveSettingsRequest(values), {
        onSuccess: () => {
          toast.success('설정을 저장했어요')
          goBackToPreviousScreen()
        },
      })
    },
    // 오류 표시(aria-invalid)가 화면에 반영된 다음 찾아야 하므로 렌더링 뒤로 미룬다
    (_errors, event) => {
      const form = event?.target instanceof HTMLFormElement ? event.target : null
      window.requestAnimationFrame(() => focusFirstInvalidField(form))
    },
  )

  return (
    <form noValidate onSubmit={onSubmit} className="mx-auto grid max-w-2xl gap-6">
      <SectionCard
        stepNumber={1}
        title="이용 중인 서비스"
        description="지금 어떤 서비스를 쓰는지 알려 주세요. 이미 결제한 달은 비용에서 빼고 계산해요."
        notice={
          <ul className="grid gap-1 rounded-lg bg-muted px-3 py-2.5 text-sm">
            <li>
              <strong>무료 이용</strong>: 예를 들어 통신사 제휴, 멤버십 혜택, 가족 공유처럼 추가
              요금 없이 쓰는 경우예요. 비용 0원인 서비스로 계산해요.
            </li>
            <li>
              <strong>잘 모르겠음</strong>: 이용 여부가 확실하지 않을 때 골라요. 시청 가능한
              서비스로 세지 않아요.
            </li>
          </ul>
        }
      >
        {services.map((service, index) => (
          <ServiceSubscriptionRow
            key={service.ottServiceId}
            index={index}
            service={service}
            control={control}
            register={register}
            errors={errors}
          />
        ))}
      </SectionCard>

      <SectionCard
        stepNumber={2}
        title="한 달 시청 시간"
        description="한 달에 영상을 볼 수 있는 시간이에요. 긴 드라마가 몇 달에 걸쳐 나뉘는지 계산에 쓰여요."
        banner={
          <MockVariantBanner
            decisionLabel="R11-18 시청 시간 환산 입력 저장"
            variant={getMockWatchVariant()}
            description={{
              a: '환산 결과를 분(월 N분)으로만 저장해요. 저장한 뒤 다시 열면 "직접 입력"으로 보여요.',
              b: '"주 N회 × 한 번에 M시간" 입력값도 함께 저장해요. 저장한 뒤 다시 열어도 환산 입력이 그대로 보여요.',
            }}
          />
        }
      >
        <div className="py-1">
          <WatchTimeField control={control} register={register} errors={errors} />
        </div>
      </SectionCard>

      <SectionCard
        stepNumber={3}
        title="한 달 예산"
        description="한 달에 OTT 구독에 쓸 수 있는 최대 금액이에요. 0원이면 이미 쓰는 서비스만으로 계산해요."
      >
        <div className="grid gap-1.5 py-1">
          <Label htmlFor="monthlyBudget">
            월 예산 (원)
            <RequiredMark />
          </Label>
          <div className="flex items-center gap-2">
            <Input
              id="monthlyBudget"
              type="number"
              inputMode="numeric"
              min={0}
              step={100}
              aria-required="true"
              placeholder="예: 20000"
              aria-invalid={errors.monthlyBudget ? true : undefined}
              aria-describedby={errors.monthlyBudget ? 'monthlyBudget-error' : undefined}
              className="h-11 w-44 tabular-nums"
              {...register('monthlyBudget', { setValueAs: toOptionalNumber })}
            />
            <span className="text-sm">원</span>
            {typeof monthlyBudget === 'number' && Number.isFinite(monthlyBudget) && (
              <span className="text-sm text-muted-foreground tabular-nums">
                = {formatWon(monthlyBudget)}
              </span>
            )}
          </div>
          {errors.monthlyBudget && (
            <p id="monthlyBudget-error" role="alert" className="text-sm text-destructive">
              {errors.monthlyBudget.message}
            </p>
          )}
        </div>
      </SectionCard>

      {/* 저장 구역: 오류 요약과 저장 버튼. 서버가 거절한 이유(400 등)는 userMessage로 보인다 */}
      <section aria-label="저장" className="grid gap-3">
        {save.isError && (
          <Alert variant="destructive">
            <AlertTitle>저장하지 못했어요</AlertTitle>
            <AlertDescription className="text-destructive/90">
              {isApiError(save.error) ? save.error.userMessage : '잠시 후 다시 시도해 주세요.'}
            </AlertDescription>
          </Alert>
        )}
        <Button type="submit" size="lg" className="h-11 w-full text-base" disabled={save.isPending}>
          {save.isPending && (
            <LoaderCircleIcon aria-hidden="true" className="motion-safe:animate-spin" />
          )}
          {save.isPending ? '저장하는 중' : '저장하기'}
        </Button>
        <p className="text-center text-sm text-muted-foreground">
          저장한 내용은 언제든 이 화면에서 다시 바꿀 수 있어요.
        </p>
      </section>
    </form>
  )
}
