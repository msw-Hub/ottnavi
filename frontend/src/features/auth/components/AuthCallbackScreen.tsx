import { useEffect, useState } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router'
import { ErrorState } from '@/components/common/ErrorState'
import { LoadingState } from '@/components/common/LoadingState'
import { isMockMode, MOCK_SCENARIO_PARAM } from '@/lib/mockScenario'
import { getSafeRedirectPath } from '@/lib/safeRedirect'
import { isMockOnboardingDone, setMockRole } from '@/mocks/mockSession'

// 로그인 처리에 걸리는 시간 흉내(밀리초). 로딩 화면이 눈에 보이게 하려는 값이다
const MOCK_CALLBACK_DELAY_MS = 700

// 온보딩(내 설정) 화면 경로. 처음 로그인한 사람은 원래 화면보다 먼저 여기로 보낸다(PRD SCR-06)
const ONBOARDING_PATH = '/settings'

/**
 * SCR-06 로그인 콜백 화면이다. 로그인 처리 중 로딩 → 성공이면 이동 / 실패면 안내와 "다시 시도".
 *
 * 흐름(TECH 4절)과 목업의 차이:
 * - 실제(Task 057): 백엔드가 Refresh Token을 HttpOnly 쿠키로 심고 이 주소로 보낸다. 이 화면이 POST /api/auth/refresh로
 *   Access Token을 받아 메모리(authStore)에만 저장한다. 토큰은 주소(쿼리·해시)로 받지 않는다(기록·Referer에 남기 때문).
 * - 목업(지금): 토큰이 없다. 잠깐 로딩을 보인 뒤 목업 역할을 USER로 바꾼다. 실제 인증 저장소·http.ts는 건드리지 않는다.
 *
 * 성공 뒤 갈 곳: 온보딩 미완료면 SCR-07(/settings), 완료했으면 from(원래 화면, 없으면 첫 화면).
 * from은 getSafeRedirectPath로 검사해 이 사이트 안의 경로만 쓴다. 온보딩으로 보낼 때도 from을 state로 이어 주어,
 * 온보딩을 마친 뒤(Task 028) 원래 화면으로 돌아갈 수 있게 한다.
 *
 * 실패 재현: 주소에 ?mockScenario=login-failed를 붙인다(목업 모드 전용). 목업이 아닌 환경에서는 로그인 연동이 없으므로 실패로 본다.
 */
export function AuthCallbackScreen() {
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const [isFailed, setIsFailed] = useState(false)

  const from = getSafeRedirectPath((location.state as { from?: unknown } | null)?.from)
  const scenario = searchParams.get(MOCK_SCENARIO_PARAM)
  const isMock = isMockMode()

  /*
   * 로딩 시간을 흉내 내려고 타이머를 쓰고, 정리 함수로 반드시 해제한다.
   * 정리하지 않으면 화면을 떠난 뒤에도 타이머가 실행되어 엉뚱한 이동이 일어난다(개발 모드의 StrictMode는 효과를 두 번 실행해 보기도 한다).
   * 의존성: 화면 값(from·scenario·isMock)이 바뀌면 처리를 처음부터 다시 한다.
   */
  useEffect(() => {
    const timerId = window.setTimeout(() => {
      if (!isMock || scenario === 'login-failed') {
        setIsFailed(true)
        return
      }
      setMockRole('USER')
      if (isMockOnboardingDone()) {
        navigate(from, { replace: true })
      } else {
        navigate(ONBOARDING_PATH, { replace: true, state: { from } })
      }
    }, MOCK_CALLBACK_DELAY_MS)
    return () => window.clearTimeout(timerId)
  }, [from, isMock, navigate, scenario])

  // 다시 시도: 로그인 화면으로 돌아가 처음부터 하게 한다(돌아갈 곳 from도 유지)
  function handleRetry() {
    navigate('/login', { replace: true, state: { from } })
  }

  if (isFailed) {
    return (
      <section className="mx-auto flex w-full max-w-md flex-col gap-4">
        <h1 className="text-2xl font-semibold">로그인하지 못했습니다</h1>
        <ErrorState
          title="로그인에 실패했습니다"
          message="로그인을 마치지 못했습니다. 잠시 후 다시 시도해 주세요."
          onRetry={handleRetry}
        />
      </section>
    )
  }

  return (
    <section className="mx-auto flex w-full max-w-md flex-col gap-4">
      <h1 className="text-2xl font-semibold">로그인 처리 중</h1>
      <LoadingState message="로그인을 확인하는 중입니다" />
    </section>
  )
}
