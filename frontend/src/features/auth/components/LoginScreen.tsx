import { CalendarRangeIcon, HeartIcon, SaveIcon } from 'lucide-react'
import { createSearchParams, useLocation, useNavigate } from 'react-router'
import { Button } from '@/components/ui/button'
import { isMockMode, MOCK_SCENARIO_PARAM } from '@/lib/mockScenario'
import { getSafeRedirectPath } from '@/lib/safeRedirect'

// 로그인하면 쓸 수 있는 기능 안내(PRD SCR-06 "로그인 혜택 안내")
const LOGIN_BENEFITS = [
  { icon: HeartIcon, text: '보고 싶은 작품을 찜하고 우선순위를 정할 수 있습니다.' },
  {
    icon: CalendarRangeIcon,
    text: '월 예산과 시청 시간에 맞춰 3개월 구독 플랜을 계산해 줍니다.',
  },
  { icon: SaveIcon, text: '계산한 플랜을 저장하고 다음에 이어서 볼 수 있습니다.' },
]

/**
 * SCR-06 로그인 화면이다. "Google로 로그인" 버튼과 로그인 혜택을 안내한다.
 *
 * 돌아갈 곳(from)은 router state로 받는다. 찜 버튼이나 머리글 "로그인" 링크가 지금 화면을 from에 담아 보낸다.
 * 사용자가 바꿀 수 있는 값이라 getSafeRedirectPath로 이 사이트 안의 경로만 통과시킨다(오픈 리다이렉트 방지).
 *
 * 목업 단계: 버튼은 실제 Google로 가지 않고 /auth/callback으로 이동만 한다(from도 같이 넘긴다).
 * TODO(Task 057): 실제 연동은 브라우저를 GET /api/oauth2/authorization/google(TECH 4절)로 보낸다.
 *   그 경우 페이지가 통째로 바뀌어 router state가 사라지므로, from은 이동 전에 sessionStorage에 임시로 담았다가
 *   콜백에서 꺼내 getSafeRedirectPath로 다시 검사하는 방식으로 옮긴다(from은 토큰이 아니라 단순 경로라 저장해도 안전하다).
 *   토큰은 주소나 sessionStorage에 두지 않는다.
 * 목업이 아닌 환경에서는 실제 연동이 없으므로 버튼을 비활성으로 두고 이유를 알린다.
 */
export function LoginScreen() {
  const navigate = useNavigate()
  const location = useLocation()
  const rawFrom = (location.state as { from?: unknown } | null)?.from
  const from = getSafeRedirectPath(rawFrom, '')
  const isMock = isMockMode()

  // 목업 시나리오(?mockScenario=login-failed)를 로그인 화면 주소에 붙여 열었다면 콜백 화면으로도 이어 준다
  const mockScenario = new URLSearchParams(location.search).get(MOCK_SCENARIO_PARAM)

  function handleLogin() {
    navigate(
      {
        pathname: '/auth/callback',
        search:
          isMock && mockScenario
            ? `?${createSearchParams({ [MOCK_SCENARIO_PARAM]: mockScenario })}`
            : '',
      },
      { state: { from } },
    )
  }

  return (
    <section className="mx-auto flex w-full max-w-md flex-col gap-5">
      <div className="flex flex-col gap-2">
        <h1 className="text-2xl font-semibold">로그인</h1>
        <p className="text-muted-foreground">
          {from
            ? '이 기능은 로그인이 필요합니다. 로그인하면 보던 화면으로 돌아옵니다.'
            : 'Google 계정으로 간편하게 로그인할 수 있습니다.'}
        </p>
      </div>

      <ul
        aria-label="로그인 혜택"
        className="grid gap-3 rounded-xl border border-border bg-card p-4"
      >
        {LOGIN_BENEFITS.map(({ icon: Icon, text }) => (
          <li key={text} className="flex items-start gap-3 text-[0.9375rem]">
            <Icon aria-hidden="true" className="mt-0.5 size-4.5 shrink-0 text-primary" />
            <span>{text}</span>
          </li>
        ))}
      </ul>

      <div className="grid gap-2">
        <Button
          type="button"
          size="lg"
          className="h-11"
          onClick={handleLogin}
          disabled={!isMock}
          aria-describedby={isMock ? undefined : 'login-unavailable'}
        >
          Google로 로그인
        </Button>
        {!isMock && (
          <p id="login-unavailable" className="text-sm text-muted-foreground">
            로그인 연동은 아직 준비 중입니다.
          </p>
        )}
      </div>
    </section>
  )
}
