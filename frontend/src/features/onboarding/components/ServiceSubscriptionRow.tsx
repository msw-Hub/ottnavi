import { useState } from 'react'
import {
  useWatch,
  type Control,
  type FieldErrors,
  type UseFormRegister,
  type UseFormSetValue,
} from 'react-hook-form'
import type { OttService } from '@/api/generated/model'
import { DataInsufficientTag } from '@/components/common/DataInsufficientTag'
import { SUBSCRIPTION_STATUS_LABELS } from '@/components/common/displayTypes'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { RequiredMark } from '@/features/onboarding/components/RequiredMark'
import { SegmentedRadio } from '@/features/onboarding/components/SegmentedRadio'
import { toOptionalNumber, type OnboardingFormValues } from '@/features/onboarding/schema'

// 요금제 선택 상자에서 "직접 입력"을 뜻하는 값. 실제 요금제 이름과 겹치지 않도록 이름으로 쓸 수 없는 모양으로 둔다
const CUSTOM_PLAN_VALUE = '__custom__'

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
  setValue: UseFormSetValue<OnboardingFormValues> // 선택 상자에서 고른 요금제 이름을 폼 값(services.N.planName)에 넣을 때 쓴다
  planNameOptions: string[] // 이 서비스의 단품 요금제 이름 목록(요금 목록 API에서 만든다). 비어 있으면 선택 상자 없이 직접 입력 칸만 보인다
}

/**
 * 서비스 한 곳의 이용 상태(미구독 / 구독 중 / 무료 이용 / 잘 모르겠음)를 고르는 줄이다(SCR-07).
 *
 * 구독 중을 고르면 아래에 요금제 이름·결제일 칸이 펼쳐진다. 펼침 여부는 폼 값(status)에서 계산하므로 별도 상태를 두지 않는다.
 * 펼침 영역을 조건부로 그리지 않고 hidden 속성으로 숨기는 이유: 입력 칸이 항상 폼에 연결돼 있어야, 구독 중으로 바꿨다가
 * 다른 상태로 갔다가 돌아와도 입력해 둔 값이 그대로 남는다. hidden은 화면 낭독기와 키보드 이동에서도 빠지므로 접근성에 문제가 없다.
 *
 * 요금제 이름 칸의 설계(2026-11-03): 이미 등록된 요금제를 선택 상자로 먼저 고르게 하고, 없을 때만 "직접 입력"을 고르게 한다.
 * 서버에는 지금처럼 글자(planName)만 저장되므로 폼 값은 계속 services.N.planName 하나뿐이다. 두 방식을 한 필드로 합치는 방법:
 * - 단순한 방법은 선택 상자용 필드와 입력용 필드를 따로 두는 것인데, 둘 중 무엇을 보낼지 따로 정해야 하고 검증 규칙도 갈라진다.
 * - 그래서 폼에 연결된 칸은 기존 텍스트 입력 하나만 두고, 선택 상자는 폼에 연결하지 않은 채 setValue로 그 칸의 값만 바꾼다.
 * - "직접 입력을 골랐는지"만 이 컴포넌트의 useState로 들고(isCustomChosen), 선택 상자에 보일 값은 아래에서 계산한다.
 * - 직접 입력 칸은 hidden으로 숨길 뿐 항상 폼에 연결해 둔다(위 구독 중 펼침 영역과 같은 이유).
 */
export function ServiceSubscriptionRow({
  index,
  service,
  control,
  register,
  errors,
  setValue,
  planNameOptions,
}: ServiceSubscriptionRowProps) {
  const status = useWatch({ control, name: `services.${index}.status` })
  const isSubscribed = status === 'SUBSCRIBED'
  const rowErrors = errors.services?.[index]

  // 1. 요금제 이름 칸의 현재 값과 선택지 여부. 값은 폼이 가진 글자 하나다
  const planName = useWatch({ control, name: `services.${index}.planName` }) ?? ''
  const hasPlanOptions = planNameOptions.length > 0
  // 2. 사용자가 선택 상자에서 "직접 입력"을 눌렀는지. 눌렀다면 입력한 글자가 우연히 선택지와 같아져도 직접 입력 모드를 유지한다
  const [isCustomChosen, setIsCustomChosen] = useState(false)
  // 3. 선택 상자에 보일 값: 직접 입력을 눌렀거나, 저장된 이름이 선택지에 없으면(예: 예전에 직접 친 "스탠다드") 처음부터 직접 입력,
  //    선택지에 있으면 그 이름, 아직 아무것도 없으면 빈 값(안내 문구)
  let selectValue = ''
  if (isCustomChosen) selectValue = CUSTOM_PLAN_VALUE
  else if (planNameOptions.includes(planName)) selectValue = planName
  else if (planName !== '') selectValue = CUSTOM_PLAN_VALUE
  // 4. 직접 입력 칸을 보일지: 선택지가 없으면 항상(지금과 똑같은 화면), 있으면 직접 입력 모드일 때만
  const isCustomInputShown = !hasPlanOptions || selectValue === CUSTOM_PLAN_VALUE
  const planNameErrorId = `planName-${index}-error`

  // 선택 상자 변경: 요금제를 고르면 그 이름을 폼 값에 넣고, 직접 입력을 고르면 칸을 비운 채 입력 칸으로 포커스를 옮긴다.
  // shouldValidate: 오류가 떠 있던 칸이면 고르는 즉시 오류를 지운다(저장 때까지 빨간 문구가 남지 않게 한다)
  function handlePlanSelectChange(value: string) {
    const fieldName = `services.${index}.planName` as const
    if (value === CUSTOM_PLAN_VALUE) {
      setIsCustomChosen(true)
      setValue(fieldName, '')
      // 입력 칸이 hidden에서 풀린 다음 포커스를 줘야 하므로 렌더링 뒤로 미룬다
      window.requestAnimationFrame(() =>
        document.getElementById(`planName-${index}-custom`)?.focus(),
      )
      return
    }
    setIsCustomChosen(false)
    setValue(fieldName, value, { shouldValidate: true })
  }

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
        {/* content-start(시각 검수 2026-11-03): 왼쪽 칸에 선택 상자 + 직접 입력 칸이 함께 보이면 왼쪽이 더 길어진다.
            grid 기본값(stretch)이면 오른쪽 결제일 칸의 줄들이 남는 높이만큼 늘어나 라벨과 입력 칸이 아래로 벌어졌다.
            위에서부터 쌓아 두 칸의 라벨·첫 입력 칸이 같은 높이에 나란히 오게 한다 */}
        <div className="grid content-start gap-1.5">
          {/* 선택 상자가 있으면 라벨은 선택 상자를, 없으면 지금처럼 입력 칸을 가리킨다 */}
          <Label htmlFor={hasPlanOptions ? `planName-${index}` : `planName-${index}-custom`}>
            요금제 이름
            <RequiredMark />
          </Label>
          {/* 선택 상자는 브라우저 기본 <select>를 쓴다: 모바일에서 기기 기본 선택 창이 열려 손가락 조작이 쉽고,
              키보드·낭독기 지원이 기본이다. shadcn Select(Radix)는 폼에 연결하려면 숨은 select를 따로 다뤄야 해서 이 용도에는 과하다.
              폼에는 연결하지 않고(name 없음) 고른 값을 setValue로 planName에 넣는다 */}
          {hasPlanOptions && (
            <select
              id={`planName-${index}`}
              value={selectValue}
              onChange={(event) => handlePlanSelectChange(event.target.value)}
              aria-required="true"
              aria-invalid={!isCustomInputShown && rowErrors?.planName ? true : undefined}
              aria-describedby={
                !isCustomInputShown && rowErrors?.planName ? planNameErrorId : undefined
              }
              // 시각 검수(2026-11-03) 전 값. 바탕이 투명해 바로 아래 직접 입력 칸(옅게 채워진 바탕)과 다른 부품처럼 보였다:
              // className="h-11 w-full rounded-lg border border-input bg-transparent px-2.5 text-base outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 aria-invalid:border-destructive aria-invalid:ring-3 aria-invalid:ring-destructive/20 md:text-sm"
              // 지금 값: components/ui/input.tsx(shadcn Input)의 바탕·글자·전환·오류 색을 그대로 옮겨 두 칸이 한 부품처럼 보이게 한다.
              // - dark:bg-input/30: 앱은 dark: 클래스가 항상 켜져 있어(index.css) Input도 이 바탕으로 그려진다
              // - dark:aria-invalid:*: 오류일 때 테두리·링 농도도 Input과 같게 맞춘다
              // - text-foreground: 기기 기본 글자색이 아니라 토큰 색으로 쓴다
              // - [&>option]:bg-background [&>option]:text-foreground: 펼친 목록(option)의 바탕과 글자색을 토큰으로 직접 정한다(2026-11-03 운영 피드백).
              //   이전에는 "펼친 목록은 color-scheme: dark(index.css)라 어둡게 열린다"고 보고 정하지 않았으나, 실제로는 목록이 브라우저 기본 흰 바탕으로 열리고
              //   글자만 어두운 테마의 흰색을 물려받아 흰 바탕에 흰 글자로 안 보였다. 선택 상자 본체의 바탕이 투명·반투명(dark:bg-input/30)이라
              //   목록이 그 바탕을 이어받지 못한다. 목록 바탕은 불투명이어야 하므로 반투명인 input 토큰이 아니라 background 토큰을 쓴다.
              // - 화살표는 브라우저 기본 화살표를 둔다. 앱의 다른 <select>(상품 등록 서비스 칸, 개발용 역할 선택)와 같은 모양이다
              className="h-11 w-full min-w-0 rounded-lg border border-input bg-transparent px-2.5 text-base text-foreground transition-colors outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 aria-invalid:border-destructive aria-invalid:ring-3 aria-invalid:ring-destructive/20 md:text-sm dark:bg-input/30 dark:aria-invalid:border-destructive/50 dark:aria-invalid:ring-destructive/40 [&>option]:bg-background [&>option]:text-foreground"
            >
              <option value="" disabled>
                요금제를 골라 주세요
              </option>
              {planNameOptions.map((name) => (
                <option key={name} value={name}>
                  {name}
                </option>
              ))}
              <option value={CUSTOM_PLAN_VALUE}>직접 입력</option>
            </select>
          )}
          {/* 직접 입력 칸: 항상 폼에 연결해 두고 필요 없을 때만 숨긴다. 숨긴 동안은 aria-invalid를 달지 않는다
              (오류 칸으로 포커스를 옮기는 코드가 숨은 칸을 집지 않게 하려는 것) */}
          <Input
            id={`planName-${index}-custom`}
            type="text"
            hidden={!isCustomInputShown}
            placeholder="예: 스탠다드"
            autoComplete="off"
            aria-label={hasPlanOptions ? '요금제 이름 직접 입력' : undefined}
            aria-required="true"
            aria-invalid={isCustomInputShown && rowErrors?.planName ? true : undefined}
            aria-describedby={
              isCustomInputShown && rowErrors?.planName ? planNameErrorId : undefined
            }
            className="h-11"
            {...register(`services.${index}.planName`)}
          />
          {rowErrors?.planName && (
            <p id={planNameErrorId} role="alert" className="text-sm text-destructive">
              {rowErrors.planName.message}
            </p>
          )}
        </div>
        <div className="grid content-start gap-1.5">
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
