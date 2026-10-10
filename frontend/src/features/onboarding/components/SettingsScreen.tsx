import type { ReactNode } from 'react'
import { ErrorState } from '@/components/common/ErrorState'
import { LoadingState } from '@/components/common/LoadingState'
import { SettingsForm } from '@/features/onboarding/components/SettingsForm'
import { useUserSettings } from '@/hooks/useUserSettings'
import { useOttServices } from '@/hooks/useOttServices'

/**
 * SCR-07 온보딩·내 설정 화면의 본문이다. pages/SettingsPage는 이 컴포넌트를 연결만 한다(pages는 얇게, frontend.md).
 *
 * 내 설정과 서비스 목록(7곳)을 모두 불러온 뒤에야 폼을 그린다. 폼의 처음 값(defaultValues)은 첫 그림 때만 읽히므로,
 * 데이터가 오기 전에 폼을 그리면 저장된 값이 칸에 채워지지 않기 때문이다. 불러오는 동안은 로딩, 실패하면 오류(다시 시도)를 보인다.
 * 첫 방문(isOnboardingDone=false)이면 시작 안내를 위에 덧붙인다. 같은 폼을 이후에는 "내 설정 수정"으로 쓴다.
 */
export function SettingsScreen() {
  const settings = useUserSettings()
  const services = useOttServices()

  let content: ReactNode
  if (settings.isPending || services.isPending) {
    content = <LoadingState message="내 설정을 불러오고 있어요" />
  } else if (settings.isError) {
    content = <ErrorState error={settings.error} onRetry={() => settings.refetch()} />
  } else if (services.isError) {
    content = <ErrorState error={services.error} onRetry={() => services.refetch()} />
  } else {
    // 서비스 목록 API의 표시 순서(displayOrder)를 따른다. 원본 배열을 바꾸지 않으려고 복사해서 정렬한다
    const sortedServices = [...services.data].sort((a, b) => a.displayOrder - b.displayOrder)
    content = <SettingsForm settings={settings.data} services={sortedServices} />
  }

  const isFirstVisit = settings.data?.isOnboardingDone === false

  return (
    <section className="flex flex-col gap-5">
      <div className="mx-auto grid w-full max-w-2xl gap-1">
        <h1 className="text-2xl font-semibold">{isFirstVisit ? '시작하기' : '내 설정'}</h1>
        <p className="text-muted-foreground">
          {isFirstVisit
            ? '세 가지만 알려 주면 몇 달에 어디를 구독하면 좋을지 계산해 드려요. 처음 한 번만 입력하면 돼요.'
            : '여기서 바꾸면 다음 계산부터 반영돼요.'}
        </p>
      </div>
      <div className="mx-auto w-full max-w-2xl">{content}</div>
    </section>
  )
}
