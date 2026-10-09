import { useMockRole } from '@/hooks/useMockRole'
import { isMockMode } from '@/lib/mockScenario'
import { switchMockRole, type MockRole } from '@/mocks/mockSession'

// 선택지. 순서가 화면에 보이는 순서다
const MOCK_ROLE_OPTIONS: { value: MockRole; label: string }[] = [
  { value: 'GUEST', label: '비로그인' },
  { value: 'USER', label: '회원' },
  { value: 'ADMIN', label: '관리자' },
]

/**
 * 목업 로그인 상태(비로그인/회원/관리자)를 바꾸는 개발용 전환이다(Task 027 결정 ①).
 *
 * 목업 모드(VITE_USE_MOCK=true)에서만 그린다. 운영에서는 null이라 화면에 나오지 않고, 운영 빌드에서는 Vite가
 * isMockMode()를 상수로 바꿔 이 분기까지 지워 준다. 실제 인증(authStore, Task 057)과는 연결되지 않은 화면 확인용 장치다.
 *
 * select(네이티브 선택 상자)로 만든 이유: 머리글 윗줄 한 칸에 들어가는 작은 크기(h-6)여야 375px 폭에서도 머리글이
 * 두 줄을 유지한다(토스트 위치가 머리글 높이에 맞춰져 있다, app/providers.tsx). 키보드·낭독기 지원도 브라우저가 이미 해 준다.
 * 개발용임을 알리려고 "개발용" 글자를 앞에 붙였다.
 */
export function MockRoleSwitcher() {
  // 훅은 조건문보다 먼저 부른다(React 규칙). 운영에서는 값을 쓰지 않고 null만 돌려준다
  const role = useMockRole()
  if (!isMockMode()) return null

  return (
    <label className="flex items-center gap-1 text-xs text-muted-foreground">
      <span aria-hidden="true">개발용</span>
      <select
        aria-label="개발용 목업 로그인 상태"
        value={role}
        onChange={(event) => switchMockRole(event.target.value as MockRole)}
        className="h-6 rounded-md border border-input bg-background px-1 text-xs text-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
      >
        {MOCK_ROLE_OPTIONS.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </label>
  )
}
