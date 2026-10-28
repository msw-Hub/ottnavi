import { useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { useLoginDemo } from '@/api/generated/auth/auth'
import { resolveLoginDestination } from '@/features/auth/loginDestination'
import { isMockMode } from '@/lib/mockScenario'
import { useAuthStore } from '@/stores/authStore'

/*
 * 데모 로그인 요청에만 붙이는 axios 설정(api/http.ts의 요청별 표시 필드).
 * - _skipRateLimitToast: 429(같은 IP 15분 5회 초과)는 로그인 화면이 "약 N분 뒤에" 문장으로 직접 안내하므로 전역 토스트를 띄우지 않는다.
 * - _authRetried: 이 요청의 401(DEMO_LOGIN_FAILED)은 "아이디·비밀번호가 틀림"이지 "토큰 만료"가 아니다.
 *   이미 로그인한 채 이 화면을 열면 Authorization이 붙어 나가는데, 그때 401 재발급 인터셉터가 재발급 뒤 같은 요청을 다시 보내지 않도록
 *   "이미 재시도한 요청"으로 표시해 둔다. 재시도하면 실패 횟수만 하나 더 쌓인다(서버는 실패도 요청 제한에 센다).
 */
const DEMO_LOGIN_REQUEST_OPTIONS = { _skipRateLimitToast: true, _authRetried: true }

/**
 * 데모 계정 로그인 요청 훅이다(Task 109, POST /api/auth/demo-login).
 *
 * 흐름:
 * 1. 아이디·비밀번호를 보낸다. 성공하면 서버가 Refresh Token을 HttpOnly 쿠키(rt)로 심고, 본문에 Access Token과 role(DEMO)을 준다.
 *    Google 로그인과 달리 /auth/refresh를 따로 부르지 않아도 된다(계약).
 * 2. [보안] Access Token은 authStore의 메모리에만 넣는다(저장소·주소에 두지 않는다, stores/authStore 주석).
 *    역할은 응답 값(서버가 DB에서 읽은 값)을 쓴다. 목업 모드에서는 MSW 핸들러가 목업 역할을 DEMO로 바꾸므로 authStore를 쓰지 않는다
 *    (Google 로그인 콜백 화면의 목업 처리와 같다, hooks/useSession).
 * 3. 이동: Google 로그인과 같은 규칙(resolveLoginDestination)이다. 온보딩 전이면 /settings, 아니면 돌아갈 곳(from, 없으면 홈).
 *    데모 계정은 여러 사람이 함께 쓰므로 대개 이미 온보딩을 마쳐 원래 화면으로 가지만, 계정이 처음 만들어진 직후에는 설정 화면부터 간다.
 *    토스트는 띄우지 않는다(머리글 아래 데모 안내 띠가 로그인됐음을 알린다).
 *
 * 오류(401·403·404·429·500)는 이 훅에서 다루지 않고 호출한 화면(DemoLoginSection)이 상태별 문장으로 보인다.
 *
 * @param from 로그인 뒤 돌아갈 내부 경로. 이미 getSafeRedirectPath로 검사한 값이어야 한다(빈 문자열이면 홈)
 */
export function useDemoLogin(from: string) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  return useLoginDemo({
    request: DEMO_LOGIN_REQUEST_OPTIONS,
    mutation: {
      onSuccess: async (response) => {
        // 1. 실제 환경: 토큰과 역할을 메모리에 넣는다(목업 모드는 핸들러가 이미 역할을 바꿨다)
        if (!isMockMode()) {
          useAuthStore.getState().setSession(response.data.accessToken, response.data.role)
        }
        // 2. 온보딩 여부를 보고 이동할 곳을 정한 뒤 이동한다(뒤로 가기로 로그인 화면이 다시 나오지 않게 replace)
        const { path, state } = await resolveLoginDestination(queryClient, from || '/')
        navigate(path, { replace: true, state })
      },
    },
  })
}
