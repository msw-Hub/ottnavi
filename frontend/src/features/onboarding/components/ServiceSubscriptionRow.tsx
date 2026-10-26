import { useWatch, type Control, type FieldErrors, type UseFormRegister } from 'react-hook-form'
import type { OttService } from '@/api/generated/model'
import { DataInsufficientTag } from '@/components/common/DataInsufficientTag'
import { SUBSCRIPTION_STATUS_LABELS } from '@/components/common/displayTypes'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { RequiredMark } from '@/features/onboarding/components/RequiredMark'
import { SegmentedRadio } from '@/features/onboarding/components/SegmentedRadio'
import { toOptionalNumber, type OnboardingFormValues } from '@/features/onboarding/schema'

// 선택됐을 때 칸 색(tokens.css의 --choice-*). 네 선택지가 서로 다른 색이고, 체크 아이콘·굵은 글자·상태 이름 글자를 함께 쓰므로 색만으로 구분하지 않는다
const SELECTED_CLASS = {
  NOT_SUBSCRIBED:
    'peer-checked:border-choice-none-bg peer-checked:bg-choice-none-bg peer-checked:text-choice-fg',
  SUBSCRIBED:
    'peer-checked:border-choice-subscribed-bg peer-checked:bg-choice-subscribed-bg peer-checked:text-choice-fg',
  FREE: 'peer-checked:border-choice-free-bg peer-checked:bg-choice-free-bg peer-checked:text-choice-fg',
  UNKNOWN:
    'peer-checked:border-choice-unknown-bg peer-checked:bg-choice-unknown-bg peer-checked:text-choice-fg',
}

// 이용 상태 선택지. 순서가 화면 순서다(미구독을 처음에 두는 이유: 대부분의 서비스가 미구독이라 가장 먼저 보여야 빠르게 훑는다)
const STATUS_OPTIONS = [
  {
    value: 'NOT_SUBSCRIBED',
    label: SUBSCRIPTION_STATUS_LABELS.NOT_SUBSCRIBED,
    selectedClassName: SELECTED_CLASS.NOT_SUBSCRIBED,
  },
  {
    value: 'SUBSCRIBED',
    label: SUBSCRIPTION_STATUS_LABELS.SUBSCRIBED,
    selectedClassName: SELECTED_CLASS.SUBSCRIBED,
  },
  {
    value: 'FREE',
    label: SUBSCRIPTION_STATUS_LABELS.FREE,
    selectedClassName: SELECTED_CLASS.FREE,
  },
  // 점선 테두리: "확정하지 못한 선택"을 모양으로 알린다(제공 상태 "모름"의 점선과 같은 발상)
  {
    value: 'UNKNOWN',
    label: SUBSCRIPTION_STATUS_LABELS.UNKNOWN,
    isDashed: true,
    selectedClassName: SELECTED_CLASS.UNKNOWN,
  },
]

interface ServiceSubscriptionRowProps {
  index: number // 폼의 services 배열 안 위치. 입력 칸 이름(services.0.status 등)에 쓴다
  service: Pick<OttService, 'name' | 'dataQuality'> // 서비스 이름과 데이터 충분도(쿠팡플레이는 "데이터 부족")
  control: Control<OnboardingFormValues> // 폼 제어 객체. 이용 상태를 보고 요금제·결제일 칸을 펼칠지 정한다
  register: UseFormRegister<OnboardingFormValues> // 입력 칸을 폼에 연결하는 함수
  errors: FieldErrors<OnboardingFormValues> // 검증 오류. 이 줄의 요금제·결제일 오류를 칸 아래에 보인다
}

/**
 * 서비스 한 곳의 이용 상태(미구독 / 구독 중 / 무료 이용 / 잘 모르겠음)를 고르는 줄이다(SCR-07).
 *
 * 구독 중을 고르면 아래에 요금제 이름·결제일 칸이 펼쳐진다. 펼침 여부는 폼 값(status)에서 계산하므로 별도 상태를 두지 않는다.
 * 펼침 영역을 조건부로 그리지 않고 hidden 속성으로 숨기는 이유: 입력 칸이 항상 폼에 연결돼 있어야, 구독 중으로 바꿨다가
 * 다른 상태로 갔다가 돌아와도 입력해 둔 값이 그대로 남는다. hidden은 화면 낭독기와 키보드 이동에서도 빠지므로 접근성에 문제가 없다.
 */
export function ServiceSubscriptionRow({
  index,
  service,
  control,
  register,
  errors,
}: ServiceSubscriptionRowProps) {
  const status = useWatch({ control, name: `services.${index}.status` })
  const isSubscribed = status === 'SUBSCRIBED'
  const rowErrors = errors.services?.[index]

  return (
    <div className="grid gap-3 py-4 first:pt-0 last:pb-0">
      <SegmentedRadio
        legend={
          <span className="flex flex-wrap items-center gap-x-2 gap-y-1 text-base">
            {service.name}
            {service.dataQuality === 'INSUFFICIENT' && <DataInsufficientTag />}
          </span>
        }
        options={STATUS_OPTIONS}
        registration={register(`services.${index}.status`)}
        className="grid-cols-2 sm:grid-cols-4"
      />

      <div
        hidden={!isSubscribed}
        className="grid gap-3 rounded-lg border border-border bg-background p-3 sm:grid-cols-[1fr_9rem]"
      >
        <div className="grid gap-1.5">
          <Label htmlFor={`planName-${index}`}>
            요금제 이름
            <RequiredMark />
          </Label>
          <Input
            id={`planName-${index}`}
            type="text"
            placeholder="예: 스탠다드"
            autoComplete="off"
            aria-required="true"
            aria-invalid={rowErrors?.planName ? true : undefined}
            aria-describedby={rowErrors?.planName ? `planName-${index}-error` : undefined}
            className="h-11"
            {...register(`services.${index}.planName`)}
          />
          {rowErrors?.planName && (
            <p id={`planName-${index}-error`} role="alert" className="text-sm text-destructive">
              {rowErrors.planName.message}
            </p>
          )}
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor={`billingDay-${index}`}>
            매월 결제일
            <RequiredMark />
          </Label>
          <div className="flex items-center gap-2">
            <Input
              id={`billingDay-${index}`}
              type="number"
              inputMode="numeric"
              min={1}
              max={31}
              aria-required="true"
              aria-invalid={rowErrors?.billingDay ? true : undefined}
              aria-describedby={rowErrors?.billingDay ? `billingDay-${index}-error` : undefined}
              className="h-11 tabular-nums"
              {...register(`services.${index}.billingDay`, { setValueAs: toOptionalNumber })}
            />
            <span className="shrink-0 text-sm">일</span>
          </div>
          {rowErrors?.billingDay && (
            <p id={`billingDay-${index}-error`} role="alert" className="text-sm text-destructive">
              {rowErrors.billingDay.message}
            </p>
          )}
        </div>
      </div>
    </div>
  )
}
