import { useEffect, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useLocation, useNavigate, useSearchParams } from 'react-router'
import { refreshSession } from '@/api/http'
import { ErrorState } from '@/components/common/ErrorState'
import { LoadingState } from '@/components/common/LoadingState'
import { PageTitle } from '@/components/common/PageTitle'
import { resolveLoginDestination } from '@/features/auth/loginDestination'
import { clearLoginReturnTo, readLoginReturnTo } from '@/lib/loginReturn'
import { isMockMode, MOCK_SCENARIO_PARAM } from '@/lib/mockScenario'
import { getSafeRedirectPath } from '@/lib/safeRedirect'
import { setMockRole } from '@/mocks/mockSession'

// 로그인 처리에 걸리는 시간 흉내(밀리초). 로딩 화면이 눈에 보이게 하려는 값이다(목업 모드 전용)
const MOCK_CALLBACK_DELAY_MS = 700

// 백엔드가 로그인 실패 때 /auth/callback으로 붙여 보내는 값(TECH 4절: /auth/callback?error=login_failed)
const LOGIN_FAILED_QUERY = { name: 'error', value: 'login_failed' }

// 로그인 직후 이동할 곳을 정하는 resolveLoginDestination(온보딩 미완료면 /settings)은 데모 계정 로그인(Task 109)과 함께 쓰려고
// features/auth/loginDestination.ts로 옮겼다. 내용은 그대로다.

/**
 * SCR-06 로그인 콜백 화면이다. 로그인 처리 중 로딩 → 성공이면 이동 / 실패면 안내와 "다시 시도".
 *
 * 흐름(TECH 4절):
 * 1. 브라우저가 Google 로그인을 마치고 백엔드를 거쳐 이 주소로 돌아온다. 이때 백엔드가 Refresh Token을 HttpOnly 쿠키로 심어 둔다.
 *    실패했다면 주소에 ?error=login_failed가 붙는다(오류 코드가 아니라 리다이렉트로 알린다).
 * 2. 이 화면이 POST /api/auth/refresh로 Access Token을 받아 메모리(authStore)에만 저장한다(api/http.ts의 refreshSession).
 *    토큰은 주소(쿼리·해시)로 받지 않는다(기록·Referer에 남기 때문).
 * 3. 성공하면 돌아갈 화면으로 이동한다. 앱 시작 때의 재발급(restoreSession)과 겹쳐도 refreshSession이 요청 하나로 묶는다.
 *
 * 돌아갈 곳(from): 목업 모드는 router state, 실제 환경은 페이지가 통째로 바뀌었으므로 로그인 화면이 sessionStorage에 보관한
 * 경로(lib/loginReturn)다. 둘 다 getSafeRedirectPath로 검사해 이 사이트 안의 경로만 쓴다.
 * 온보딩 미완료(내 설정의 isOnboardingDone=false)면 SCR-07(/settings)로 보낸다(Task 058, resolveLoginDestination).
 *
 * 목업 모드: 토큰이 없다. 잠깐 로딩을 보인 뒤 목업 역할을 USER로 바꾼다. 실패 재현은 주소에 ?mockScenario=login-failed를 붙인다.
 */
export function AuthCallbackScreen() {
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const [isRefreshFailed, setIsRefreshFailed] = useState(false)
  const queryClient = useQueryClient()

  const stateFrom = getSafeRedirectPath((location.state as { from?: unknown } | null)?.from, '')
  const scenario = searchParams.get(MOCK_SCENARIO_PARAM)
  const isMock = isMockMode()
  // 백엔드가 로그인 실패를 알려 온 경우. 재발급을 시도할 필요 없이 실패 화면을 보인다
  const hasLoginError = searchParams.get(LOGIN_FAILED_QUERY.name) === LOGIN_FAILED_QUERY.value

  /*
   * 의존성: 화면 값(stateFrom·scenario·isMock·hasLoginError)이 바뀌면 처리를 처음부터 다시 한다.
   * 정리 함수에서 취소 표시를 하는 이유: 화면을 떠난 뒤(또는 개발 모드의 StrictMode가 효과를 두 번 실행할 때 첫 번째 실행의 결과가)
   * 늦게 도착해 엉뚱한 이동·상태 변경을 일으키지 않게 한다. 재발급 요청 자체는 refreshSession이 하나로 묶으므로 두 번 나가지 않는다.
   */
  useEffect(() => {
    // 1. 목업 모드: 로딩을 흉내 낸 뒤 회원으로 바꾸고 이동한다(시간 흉내용 타이머는 정리 함수에서 해제한다)
    if (isMock) {
      let isMockCancelled = false
      const timerId = window.setTimeout(() => {
        if (scenario === 'login-failed') {
          setIsRefreshFailed(true)
          return
        }
        setMockRole('USER')
        const target = stateFrom || '/'
        void resolveLoginDestination(queryClient, target).then(({ path, state }) => {
          if (!isMockCancelled) navigate(path, { replace: true, state })
        })
      }, MOCK_CALLBACK_DELAY_MS)
      return () => {
        isMockCancelled = true
        window.clearTimeout(timerId)
      }
    }

    // 2. 실제 환경: 백엔드가 실패를 알렸으면 재발급하지 않는다
    if (hasLoginError) return

    // 3. 실제 환경: 쿠키로 Access Token을 받고, 성공하면 돌아갈 화면으로 이동한다
    let isCancelled = false
    refreshSession()
      .then(async () => {
        if (isCancelled) return
        const target = stateFrom || readLoginReturnTo('/')
        // 첫 로그인(온보딩 전)이면 원래 화면보다 먼저 내 설정으로 보낸다. 돌아갈 곳(target)은 state로 넘겨 저장 뒤 복귀한다
        const { path, state } = await resolveLoginDestination(queryClient, target)
        if (isCancelled) return
        clearLoginReturnTo() // 이동이 끝난 뒤 지워야 효과 이중 실행(개발 모드)에서 값을 잃지 않는다
        navigate(path, { replace: true, state })
      })
      .catch(() => {
        if (!isCancelled) setIsRefreshFailed(true)
      })
    return () => {
      isCancelled = true
    }
  }, [hasLoginError, isMock, navigate, queryClient, scenario, stateFrom])

  // 다시 시도: 로그인 화면으로 돌아가 처음부터 하게 한다(돌아갈 곳도 유지)
  function handleRetry() {
    navigate('/login', { replace: true, state: { from: stateFrom || readLoginReturnTo('') } })
  }

  if (isRefreshFailed || hasLoginError) {
    return (
      <section className="mx-auto flex w-full max-w-md flex-col gap-4">
        {/* 탭 제목도 화면 제목과 맞춘다. 실패 여부는 이 컴포넌트만 알아서 여기서 정한다(pages는 얇게 두려고 상태를 올리지 않는다) */}
        <PageTitle name="로그인하지 못했습니다" />
        <h1 className="text-2xl font-semibold">로그인하지 못했습니다</h1>
        {/* 상자 제목은 화면 제목(h1 "로그인하지 못했습니다")과 같은 말을 되풀이하지 않고 다음에 할 일을 말한다(057 검수 제안 1) */}
        <ErrorState
          title="다시 로그인해 주세요"
          message="로그인을 마치지 못했습니다. 잠시 후 다시 시도해 주세요."
          onRetry={handleRetry}
        />
      </section>
    )
  }

  return (
    <section className="mx-auto flex w-full max-w-md flex-col gap-4">
      <PageTitle name="로그인 중" />
      <h1 className="text-2xl font-semibold">로그인 처리 중</h1>
      <LoadingState message="로그인을 확인하는 중입니다" />
    </section>
  )
}
