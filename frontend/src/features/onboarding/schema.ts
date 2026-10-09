/*
 * 온보딩·내 설정 폼(SCR-07)의 입력 규칙이다. React Hook Form이 zodResolver로 이 스키마를 써서 검사한다.
 *
 * 왜 Zod 스키마로 두나: 규칙(구독 중일 때만 요금제·결제일 필수, 시청 시간 방식에 따라 필요한 칸이 다름)이 화면 코드에 흩어지면
 * 빠뜨리기 쉽다. 규칙을 한 곳에 두면 화면 없이 단위 테스트할 수 있고, 같은 규칙에서 타입(OnboardingFormValues)도 나온다.
 * 서버(백엔드)도 같은 규칙으로 다시 검사한다. 이 검사는 사용자가 빨리 고치도록 돕는 1차 안내일 뿐 보안 장치가 아니다.
 *
 * 숫자 칸 주의: 입력 칸이 비었을 때 값은 NaN이 아니라 undefined로 넘겨야 한다(register의 setValueAs에 toOptionalNumber를 쓴다).
 * NaN은 "숫자가 아님" 오류가 되어, 쓰지 않는 칸(예: 미구독인 서비스의 결제일)까지 막아 버리기 때문이다.
 */
import { z } from 'zod'
import { SUBSCRIPTION_STATUS_LABELS } from '@/components/common/displayTypes'
import type {
  SaveUserSettingsRequest,
  UserSubscription,
  WatchPreset,
} from '@/features/onboarding/mockTypes'
import {
  MAX_MONTHLY_WATCH_MINUTES,
  MIN_MONTHLY_WATCH_MINUTES,
  resolveMonthlyWatchMinutes,
  resolveWatchPreset,
} from '@/features/onboarding/watchTime'
import { formatMinutes } from '@/lib/format'

// 결제일 기본값. 온보딩에서 구독 중을 고르면 이 값을 채워 둔다(PRD SCR-07 "기본 1일")
export const DEFAULT_BILLING_DAY = 1

// 서비스 이용 상태 선택지. 순서가 화면에 보이는 순서다
export const SUBSCRIPTION_STATUS_VALUES = [
  'SUBSCRIBED',
  'FREE',
  'NOT_SUBSCRIBED',
  'UNKNOWN',
] as const

// 요금제 이름 최대 길이
const PLAN_NAME_MAX_LENGTH = 30

/**
 * 입력 칸의 문자열을 숫자로 바꾼다. 비었거나 숫자가 아니면 undefined다(NaN을 폼 값에 넣지 않기 위함).
 * 예: register('billingDay', { setValueAs: toOptionalNumber })
 */
export function toOptionalNumber(raw: unknown): number | undefined {
  if (typeof raw === 'number') return Number.isFinite(raw) ? raw : undefined
  if (typeof raw !== 'string' || raw.trim() === '') return undefined
  const parsed = Number(raw)
  return Number.isFinite(parsed) ? parsed : undefined
}

// 서비스 하나의 이용 상태 입력. SUBSCRIBED일 때만 요금제 이름·결제일이 필수다
const serviceSubscriptionSchema = z
  .object({
    ottServiceId: z.number({ error: '서비스 정보가 올바르지 않습니다.' }).int().positive(),
    status: z.enum(SUBSCRIPTION_STATUS_VALUES, { error: '이용 상태를 골라 주세요.' }),
    planName: z
      .string()
      .trim()
      .max(PLAN_NAME_MAX_LENGTH, `요금제 이름은 ${PLAN_NAME_MAX_LENGTH}자 이하로 입력해 주세요.`)
      .optional(),
    billingDay: z
      .number({ error: '결제일은 숫자로 입력해 주세요.' })
      .int('결제일은 정수로 입력해 주세요.')
      .min(1, '결제일은 1일부터 31일 사이로 입력해 주세요.')
      .max(31, '결제일은 1일부터 31일 사이로 입력해 주세요.')
      .optional(),
  })
  .superRefine((service, ctx) => {
    if (service.status !== 'SUBSCRIBED') return
    // 구독 중이면 요금제와 결제일이 있어야 한다. 그래야 "이미 결제한 달"을 비용에서 빼고 가입·해지 안내 날짜를 정할 수 있다
    if (!service.planName) {
      ctx.addIssue({
        code: 'custom',
        path: ['planName'],
        message: `${SUBSCRIPTION_STATUS_LABELS.SUBSCRIBED}이면 요금제 이름을 입력해 주세요.`,
      })
    }
    if (service.billingDay === undefined) {
      ctx.addIssue({
        code: 'custom',
        path: ['billingDay'],
        message: `${SUBSCRIPTION_STATUS_LABELS.SUBSCRIBED}이면 결제일을 입력해 주세요.`,
      })
    }
  })

// 시청 시간 입력. mode에 따라 필요한 칸이 다르다(preset: 프리셋 / weekly: 주 N회 × 회당 M시간 / direct: 월 분 직접 입력)
const watchTimeSchema = z
  .object({
    mode: z.enum(['preset', 'weekly', 'direct'], { error: '시청 시간 입력 방식을 골라 주세요.' }),
    // 프리셋: 아무것도 고르지 않은 라디오 묶음을 React Hook Form은 undefined가 아니라 null로 폼 값에 넣는다.
    // enum만 두면 null이 영문 기본 메시지('Invalid option: expected one of ...')로 실패하고, 방식을 바꿔 칸이 사라진 뒤에도
    // 눈에 안 보이는 오류로 저장이 막힌다. 그래서 null도 "고르지 않음"으로 받고, 꼭 필요할 때(preset 방식)만 아래 superRefine이 한국어로 막는다.
    preset: z.enum(['LIGHT', 'NORMAL', 'HEAVY'], { error: '시청 시간을 골라 주세요.' }).nullish(),
    timesPerWeek: z.number({ error: '주당 횟수는 숫자로 입력해 주세요.' }).optional(),
    hoursPerTime: z.number({ error: '회당 시간은 숫자로 입력해 주세요.' }).optional(),
    directMinutes: z.number({ error: '월 시청 시간은 숫자로 입력해 주세요.' }).optional(),
  })
  .superRefine((watchTime, ctx) => {
    if (watchTime.mode === 'preset') {
      if (!watchTime.preset) {
        ctx.addIssue({ code: 'custom', path: ['preset'], message: '시청 시간을 골라 주세요.' })
      }
      return
    }

    if (watchTime.mode === 'weekly') {
      const { timesPerWeek, hoursPerTime } = watchTime
      if (
        timesPerWeek === undefined ||
        !Number.isInteger(timesPerWeek) ||
        timesPerWeek < 1 ||
        timesPerWeek > 7
      ) {
        ctx.addIssue({
          code: 'custom',
          path: ['timesPerWeek'],
          message: '주당 횟수는 1회부터 7회 사이의 정수로 입력해 주세요.',
        })
      }
      if (hoursPerTime === undefined || hoursPerTime <= 0 || hoursPerTime > 24) {
        ctx.addIssue({
          code: 'custom',
          path: ['hoursPerTime'],
          message: '회당 시간은 0보다 크고 24시간 이하로 입력해 주세요.',
        })
      }
    } else if (
      watchTime.directMinutes === undefined ||
      !Number.isInteger(watchTime.directMinutes)
    ) {
      ctx.addIssue({
        code: 'custom',
        path: ['directMinutes'],
        message: '월 시청 시간을 분 단위 정수로 입력해 주세요.',
      })
      return
    }

    // 환산·직접 입력 결과가 허용 범위 안인지 본다. 120분 미만이면 긴 시즌은 시작도 못 하고 영화 한 편 보기도 어렵다(PRD FR-10)
    const minutes = resolveMonthlyWatchMinutes(watchTime)
    if (minutes === null) return // 위에서 칸별 오류를 이미 냈다
    const path = watchTime.mode === 'weekly' ? ['hoursPerTime'] : ['directMinutes']
    if (minutes < MIN_MONTHLY_WATCH_MINUTES) {
      ctx.addIssue({
        code: 'custom',
        path,
        message: `월 시청 시간은 ${formatMinutes(MIN_MONTHLY_WATCH_MINUTES)} 이상이어야 합니다.`,
      })
    } else if (minutes > MAX_MONTHLY_WATCH_MINUTES) {
      ctx.addIssue({
        code: 'custom',
        path,
        message: `월 시청 시간은 ${formatMinutes(MAX_MONTHLY_WATCH_MINUTES)}을 넘을 수 없습니다.`,
      })
    }
  })

// 온보딩·내 설정 폼 전체
export const onboardingFormSchema = z.object({
  // 서비스별 이용 상태. 몇 곳인지는 서비스 목록 API가 정하므로(현재 7곳) 개수를 고정하지 않고 1곳 이상만 요구한다
  services: z.array(serviceSubscriptionSchema).min(1, '서비스 목록이 비어 있습니다.'),
  watchTime: watchTimeSchema,
  // 월 예산(원): 필수, 0 이상 정수. 0원이면 구독 없이 이미 쓰는 서비스만으로 계산한다
  monthlyBudget: z
    .number({ error: '월 예산을 입력해 주세요.' })
    .int('월 예산은 원 단위 정수로 입력해 주세요.')
    .min(0, '월 예산은 0원 이상이어야 합니다.'),
})

// 폼 값 타입(스키마에서 나온다)
export type OnboardingFormValues = z.infer<typeof onboardingFormSchema>

/**
 * 폼 값을 저장 요청(PUT /me/settings)으로 바꾼다. 검증을 통과한 값만 넘긴다.
 *
 * - SUBSCRIBED가 아닌 서비스는 요금제·결제일을 null로 비운다(이전에 입력했다가 상태를 바꾼 값이 서버로 새지 않게 한다).
 * - 프리셋을 골랐으면 그 값, 환산·직접 입력이면 CUSTOM이다. 환산 입력값(weeklyInput)은 항상 함께 보내고,
 *   R11-18 안 a(분만 저장)에서는 서버가 버리고 안 b에서만 저장한다(목업 핸들러가 이 차이를 보여 준다).
 */
export function toSaveSettingsRequest(values: OnboardingFormValues): SaveUserSettingsRequest {
  const monthlyWatchMinutes = resolveMonthlyWatchMinutes(values.watchTime)
  if (monthlyWatchMinutes === null) {
    // 검증을 통과한 값이라면 일어나지 않는다. 호출 순서가 잘못됐을 때 조용히 잘못된 값을 보내지 않으려는 방어다
    throw new Error('월 시청 시간을 계산할 수 없습니다. 폼 검증을 먼저 통과해야 합니다.')
  }

  const subscriptions: UserSubscription[] = values.services.map((service) => {
    const isSubscribed = service.status === 'SUBSCRIBED'
    return {
      ottServiceId: service.ottServiceId,
      status: service.status,
      planName: isSubscribed ? (service.planName ?? null) : null,
      billingDay: isSubscribed ? (service.billingDay ?? DEFAULT_BILLING_DAY) : null,
    }
  })

  const { watchTime } = values
  const { timesPerWeek, hoursPerTime } = watchTime
  const watchPreset: WatchPreset = resolveWatchPreset(watchTime)

  return {
    subscriptions,
    monthlyBudget: values.monthlyBudget,
    monthlyWatchMinutes,
    watchPreset,
    weeklyInput:
      watchTime.mode === 'weekly' && timesPerWeek !== undefined && hoursPerTime !== undefined
        ? { timesPerWeek, hoursPerTime }
        : null,
  }
}
