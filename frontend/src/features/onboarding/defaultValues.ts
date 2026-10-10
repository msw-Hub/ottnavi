import type { DefaultValues } from 'react-hook-form'
import type { OttService } from '@/api/generated/model'
import type { UserSettings } from '@/features/onboarding/mockTypes'
import { DEFAULT_BILLING_DAY, type OnboardingFormValues } from '@/features/onboarding/schema'
import { WATCH_TIME_PRESETS, type SelectablePreset } from '@/features/onboarding/watchTime'

/**
 * 저장된 설정(또는 온보딩 전의 빈 설정)을 폼의 처음 값으로 바꾼다.
 *
 * 시청 시간은 "저장할 때 어떤 방식으로 골랐는지"를 되살린다: 프리셋이면 그 프리셋, 환산 입력값이 있으면 환산(R11-18 안 b에서만 서버가 돌려줌),
 * 분 값만 있으면 직접 입력. 저장된 값이 아무것도 없으면 프리셋 방식의 '보통'(NORMAL)을 미리 골라 둔다.
 * 미리 고르는 이유: 처음 온 사람이 가장 흔한 선택지를 그대로 쓰고 지나갈 수 있고, 필수 칸이 하나 줄어 첫 저장까지의 입력이 가볍다.
 * 저장된 설정이 있으면 그 값이 항상 우선한다.
 * 서비스는 서비스 목록 API(7곳)를 기준으로 줄을 만들고, 저장된 값이 없는 서비스는 "미구독"으로 둔다.
 * 컴포넌트 파일 밖에 둔 이유: 화면 없이 규칙만 단위 테스트하고, 컴포넌트 파일에서 함수를 내보내 생기는 린트 경고를 피하기 위함이다.
 */
export function buildDefaultValues(
  settings: UserSettings,
  services: OttService[],
): DefaultValues<OnboardingFormValues> {
  const savedByServiceId = new Map(settings.subscriptions.map((s) => [s.ottServiceId, s]))

  // 저장값이 없을 때의 시작 선택: 프리셋 방식 + 보통
  let watchTime: DefaultValues<OnboardingFormValues>['watchTime'] = {
    mode: 'preset',
    preset: 'NORMAL',
  }
  if (settings.watchPreset && settings.watchPreset !== 'CUSTOM') {
    watchTime = { mode: 'preset', preset: settings.watchPreset }
  } else if (settings.weeklyInput) {
    watchTime = { mode: 'weekly', ...settings.weeklyInput }
  } else if (settings.monthlyWatchMinutes !== null) {
    // 직접 입력(CUSTOM)으로 저장했더라도 분 값이 프리셋과 정확히 같으면 프리셋으로 열어 준다.
    // 같은 900분을 '직접 입력'으로 보여 주면 사용자가 프리셋을 고르지 않았다고 오해하기 쉽기 때문이다
    const matchedPreset = (Object.keys(WATCH_TIME_PRESETS) as SelectablePreset[]).find(
      (key) => WATCH_TIME_PRESETS[key] === settings.monthlyWatchMinutes,
    )
    watchTime = matchedPreset
      ? { mode: 'preset', preset: matchedPreset }
      : { mode: 'direct', directMinutes: settings.monthlyWatchMinutes }
  }

  return {
    services: services.map((service) => {
      const saved = savedByServiceId.get(service.ottServiceId)
      return {
        ottServiceId: service.ottServiceId,
        status: saved?.status ?? 'NOT_SUBSCRIBED',
        planName: saved?.planName ?? '',
        billingDay: saved?.billingDay ?? DEFAULT_BILLING_DAY,
      }
    }),
    watchTime,
    monthlyBudget: settings.monthlyBudget ?? undefined,
  }
}
