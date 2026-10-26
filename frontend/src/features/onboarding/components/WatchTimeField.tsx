import { ClockIcon } from 'lucide-react'
import { useWatch, type Control, type FieldErrors, type UseFormRegister } from 'react-hook-form'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { RequiredMark } from '@/features/onboarding/components/RequiredMark'
import { SegmentedRadio } from '@/features/onboarding/components/SegmentedRadio'
import { toOptionalNumber, type OnboardingFormValues } from '@/features/onboarding/schema'
import {
  resolveMonthlyWatchMinutes,
  WATCH_PRESET_LABELS,
  WATCH_TIME_PRESETS,
  WEEKS_PER_MONTH,
  type SelectablePreset,
} from '@/features/onboarding/watchTime'
import { formatMinutes } from '@/lib/format'

// 입력 방식 선택지. 가장 빠른 방식(프리셋)을 처음에 둔다
const MODE_OPTIONS = [
  { value: 'preset', label: '간단히 고르기' },
  { value: 'weekly', label: '주 N회로 계산' },
  { value: 'direct', label: '직접 입력' },
]

// 프리셋 선택지: 글자 아래에 실제 시간을 함께 보여 "가볍게"가 얼마인지 바로 알게 한다
const PRESET_OPTIONS = (Object.keys(WATCH_TIME_PRESETS) as SelectablePreset[]).map((preset) => ({
  value: preset,
  label: WATCH_PRESET_LABELS[preset],
  hint: `월 ${formatMinutes(WATCH_TIME_PRESETS[preset])}`,
}))

interface WatchTimeFieldProps {
  control: Control<OnboardingFormValues> // 폼 제어 객체. 입력 방식과 값을 보고 아래 칸과 "월 약 X시간"을 바꾼다
  register: UseFormRegister<OnboardingFormValues> // 입력 칸을 폼에 연결하는 함수
  errors: FieldErrors<OnboardingFormValues> // 검증 오류
}

// 오류 문장을 칸 아래에 보인다. aria-describedby로 칸과 연결되도록 id를 받는다
function FieldError({ id, message }: { id: string; message?: string }) {
  if (!message) return null
  return (
    <p id={id} role="alert" className="text-sm text-destructive">
      {message}
    </p>
  )
}

/**
 * 월 시청 시간을 입력하는 구역이다(SCR-07). 입력 방식은 세 가지: 간단히 고르기(프리셋) / 주 N회 × 회당 M시간 환산 / 분 단위 직접 입력.
 *
 * 어떤 방식이든 아래에 "월 약 X시간"을 바로 보여 준다. 사용자가 입력하는 값(주 3회 등)과 계산에 쓰이는 값(월 약 26시간)이
 * 다를 수 있는데, 그 차이를 저장 전에 확인하게 해 환산이 4.33주 기준의 어림값임을 알 수 있게 하기 위함이다.
 * 계산 자체는 watchTime.ts의 resolveMonthlyWatchMinutes가 한다(화면은 결과만 보인다).
 */
export function WatchTimeField({ control, register, errors }: WatchTimeFieldProps) {
  const watchTime = useWatch({ control, name: 'watchTime' })
  const monthlyMinutes = resolveMonthlyWatchMinutes(watchTime)
  const watchErrors = errors.watchTime

  return (
    <div className="grid gap-4">
      <SegmentedRadio
        legend="입력 방식"
        options={MODE_OPTIONS}
        registration={register('watchTime.mode')}
        className="grid-cols-3"
      />

      {watchTime.mode === 'preset' && (
        <div className="grid gap-1.5">
          <SegmentedRadio
            legend={
              <>
                한 달에 얼마나 보시나요?
                <RequiredMark />
              </>
            }
            options={PRESET_OPTIONS}
            registration={register('watchTime.preset')}
            errorId={watchErrors?.preset ? 'preset-error' : undefined}
            className="grid-cols-3"
          />
          <FieldError id="preset-error" message={watchErrors?.preset?.message} />
        </div>
      )}

      {watchTime.mode === 'weekly' && (
        <div className="grid gap-2">
          <div className="flex flex-wrap items-end gap-x-2 gap-y-3">
            <div className="grid gap-1.5">
              <Label htmlFor="timesPerWeek">
                일주일에
                <RequiredMark />
              </Label>
              <div className="flex items-center gap-2">
                <Input
                  id="timesPerWeek"
                  type="number"
                  inputMode="numeric"
                  min={1}
                  max={7}
                  aria-required="true"
                  aria-invalid={watchErrors?.timesPerWeek ? true : undefined}
                  aria-describedby={watchErrors?.timesPerWeek ? 'timesPerWeek-error' : undefined}
                  className="h-11 w-24 tabular-nums"
                  {...register('watchTime.timesPerWeek', { setValueAs: toOptionalNumber })}
                />
                <span className="text-sm">회</span>
              </div>
            </div>
            <span aria-hidden="true" className="pb-3 text-muted-foreground">
              ×
            </span>
            <div className="grid gap-1.5">
              <Label htmlFor="hoursPerTime">
                한 번에
                <RequiredMark />
              </Label>
              <div className="flex items-center gap-2">
                <Input
                  id="hoursPerTime"
                  type="number"
                  inputMode="decimal"
                  step="any"
                  min={0}
                  max={24}
                  aria-required="true"
                  aria-invalid={watchErrors?.hoursPerTime ? true : undefined}
                  aria-describedby={watchErrors?.hoursPerTime ? 'hoursPerTime-error' : undefined}
                  className="h-11 w-24 tabular-nums"
                  {...register('watchTime.hoursPerTime', { setValueAs: toOptionalNumber })}
                />
                <span className="text-sm">시간</span>
              </div>
            </div>
          </div>
          <FieldError id="timesPerWeek-error" message={watchErrors?.timesPerWeek?.message} />
          <FieldError id="hoursPerTime-error" message={watchErrors?.hoursPerTime?.message} />
          <p className="text-sm text-muted-foreground">
            한 달을 {WEEKS_PER_MONTH}주로 보고 계산해요. 어림값이라 아래 결과가 맞지 않으면 직접
            입력을 써 주세요.
          </p>
        </div>
      )}

      {watchTime.mode === 'direct' && (
        <div className="grid gap-1.5">
          <Label htmlFor="directMinutes">
            한 달 시청 시간 (분 단위)
            <RequiredMark />
          </Label>
          <div className="flex items-center gap-2">
            <Input
              id="directMinutes"
              type="number"
              inputMode="numeric"
              min={0}
              aria-required="true"
              aria-invalid={watchErrors?.directMinutes ? true : undefined}
              aria-describedby={watchErrors?.directMinutes ? 'directMinutes-error' : undefined}
              className="h-11 w-36 tabular-nums"
              {...register('watchTime.directMinutes', { setValueAs: toOptionalNumber })}
            />
            <span className="text-sm">분</span>
          </div>
          <FieldError id="directMinutes-error" message={watchErrors?.directMinutes?.message} />
        </div>
      )}

      {/* 계산 결과를 바로 보여 주는 줄. 값이 바뀔 때마다 낭독기가 정중하게(polite) 읽어 준다 */}
      <p
        aria-live="polite"
        className="flex items-center gap-2 rounded-lg bg-muted px-3 py-2.5 text-sm"
      >
        <ClockIcon aria-hidden="true" className="size-4 shrink-0" />
        {monthlyMinutes === null ? (
          <span className="text-muted-foreground">
            시간을 입력하면 한 달에 볼 수 있는 시간이 여기에 바로 보여요.
          </span>
        ) : (
          <span>
            <strong className="text-base tabular-nums">
              월 약 {formatMinutes(monthlyMinutes)}
            </strong>{' '}
            볼 수 있어요
          </span>
        )}
      </p>
    </div>
  )
}
