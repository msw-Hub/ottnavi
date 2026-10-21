import type { ReactNode } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { BellIcon, InfoIcon, LoaderCircleIcon } from 'lucide-react'
import { useForm, useWatch } from 'react-hook-form'
import { Link, useLocation, useNavigate } from 'react-router'
import { toast } from 'sonner'
import type { OttService } from '@/api/generated/model'
import { isApiError } from '@/api/http'
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
import type { UserSettings } from '@/api/generated/model'
import {
  onboardingFormSchema,
  toOptionalNumber,
  toSaveSettingsRequest,
  type OnboardingFormValues,
} from '@/features/onboarding/schema'
import { formatWon } from '@/lib/format'
import { getSafeRedirectPath } from '@/lib/safeRedirect'

// 서버 400의 fieldErrors에 담기는 영문 필드명을 사용자가 아는 화면 이름표로 바꾸는 표.
// 표에 없는 필드는 원래 이름을 그대로 보인다(이름을 지어내지 않기 위함)
const FIELD_LABELS: Record<string, string> = {
  monthlyBudget: '월 예산',
  monthlyWatchMinutes: '한 달 시청 시간',
  subscriptions: '이용 중인 서비스',
  isLowestPriceMode: '계산할 요금제',
}

// 이름표 표에서 찾는다. hasOwn으로 확인하는 이유: 서버가 "constructor" 같은 이름을 보내도 객체 기본 속성이 딸려 나오지 않게 하기 위함이다
function getFieldLabel(field: string): string {
  return Object.hasOwn(FIELD_LABELS, field) ? FIELD_LABELS[field] : field
}

interface SectionCardProps {
  stepNumber: number // 위에서부터 몇 번째 구역인지(1부터). 입력 순서를 눈에 보이게 한다
  title: string // 구역 제목
  description: string // 구역 안내(왜 묻는지)
  children: ReactNode // 구역의 입력 칸들
  notice?: ReactNode // 구역 안내 아래, 입력 칸 위에 놓는 보충 설명(선택지의 뜻 등)
}

// 입력 구역 하나를 카드로 묶는다. 번호는 장식이 아니라 순서 안내라 글자로 "1." 대신 원 안 숫자로 보인다
function SectionCard({ stepNumber, title, description, children, notice }: SectionCardProps) {
  return (
    <section aria-labelledby={`settings-section-${stepNumber}`} className="grid gap-3">
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
 * 온보딩·내 설정 폼(SCR-07). 서비스 이용 상태 → 월 시청 시간 → 월 예산 → 계산할 요금제(Task 076) → 저장 순서의 구역 카드로 이루어진다.
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
            {/* 번들 안내(Task 076). 번들은 구독 상태를 따로 받지 않아, 이미 쓰는 번들은 포함 서비스별 "구독 중"으로 받아야 계산이 맞다 */}
            <li>
              <strong>번들 이용 중</strong>: 여러 서비스를 한 번에 결제하는 번들을 쓰고 있다면,
              포함된 서비스를 각각 &quot;구독 중&quot;으로 입력해 주세요.
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

      {/* 계산할 요금제(Task 076). 기본은 끔(표준 요금제로 계산).
          체크박스는 브라우저 기본 <input type="checkbox">를 쓴다(shadcn checkbox가 설치돼 있지 않다. 관리자 번들 구성 칸과 같은 방식).
          줄 전체를 <label>로 감싸 글자를 눌러도 켜지고, 높이 44px(min-h-11)로 손가락 터치 크기를 맞췄다 */}
      <SectionCard
        stepNumber={4}
        title="계산할 요금제"
        description="한 서비스에 요금제가 여럿이면 어떤 요금제로 계산할지 정해요."
      >
        <div className="grid gap-2 py-1">
          <label
            htmlFor="isLowestPriceMode"
            className="flex min-h-11 cursor-pointer items-start gap-3 rounded-lg border border-border px-3 py-2.5 has-[:checked]:border-primary has-[:focus-visible]:ring-3 has-[:focus-visible]:ring-ring/50"
          >
            <input
              id="isLowestPriceMode"
              type="checkbox"
              aria-describedby="isLowestPriceMode-help"
              className="mt-0.5 size-4.5 shrink-0 accent-primary focus-visible:outline-none"
              {...register('isLowestPriceMode')}
            />
            <span className="grid gap-1">
              <span className="text-base font-semibold">가장 저렴한 요금제로 계산할래요</span>
              <span
                id="isLowestPriceMode-help"
                className="text-sm break-keep text-muted-foreground"
              >
                켜면 광고형·베이직 같은 저가형까지 포함해 서비스마다 가장 싼 요금제로 계산하고,
                번들도 모두 비교해요. 끄면 표준 요금제로 계산해요.
              </span>
            </span>
          </label>
          {/* 모드를 바꿔도 이미 저장한 플랜은 바뀌지 않는다(계약 UpdateMySettingsRequest.isLowestPriceMode). 아이콘 + 글자로 알린다 */}
          <p className="flex items-start gap-1.5 text-sm break-keep text-muted-foreground">
            <InfoIcon aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
            바꿔도 이미 저장한 플랜은 그대로예요. 다음 계산과 매월 다시 계산할 때부터 반영돼요.
          </p>
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
            {/* 폼 검증을 통과했는데 서버만 거절한 경우(400 VALIDATION_FAILED)의 필드별 이유 */}
            {isApiError(save.error) && save.error.fieldErrors.length > 0 && (
              <ul className="mt-2 list-disc pl-5 text-sm text-destructive/90">
                {save.error.fieldErrors.map((fieldError) => (
                  <li key={`${fieldError.field}:${fieldError.message}`}>
                    {getFieldLabel(fieldError.field)}: {fieldError.message}
                  </li>
                ))}
              </ul>
            )}
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

      {/* 회원 탈퇴 링크(SCR-13, Task 081). 눈에 덜 띄도록 폼 맨 아래 오른쪽 구석에 작은 빨간 글자로만 둔다(사용자 확정).
          첫 방문(온보딩)에는 아직 가입을 마치기 전이라 보이지 않는다. 빨간색만으로 뜻을 전하지 않고 "회원 탈퇴" 글자가 있다.
          높이 44px(min-h-11)는 글자는 작아도 누르는 영역은 터치 크기를 맞추려는 것이다 */}
      {/* 알림 설정 링크(SCR-12, Task 071): 같은 "계정 관리" 줄의 왼쪽에 둔다. 알림은 설정 행이 있어야 저장되므로(ONBOARDING_REQUIRED)
          온보딩을 마친 뒤에만 보인다. 이전: 탈퇴 링크만 단독으로 있었다 */}
      {settings.isOnboardingDone && (
        <div className="flex flex-wrap items-center justify-between gap-x-4">
          <Link
            to="/settings/notifications"
            className="inline-flex min-h-11 items-center gap-1.5 rounded-md text-sm font-semibold text-primary underline-offset-4 hover:underline focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
          >
            <BellIcon aria-hidden="true" className="size-4" />
            알림 설정
          </Link>
          <Link
            to="/settings/withdraw"
            className="inline-flex min-h-11 items-center rounded-md px-2 text-xs text-destructive underline-offset-4 hover:underline focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
          >
            회원 탈퇴
          </Link>
        </div>
      )}
    </form>
  )
}
