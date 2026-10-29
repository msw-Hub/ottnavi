import { Link, Outlet } from 'react-router'
import { RequireAuth } from '@/app/guards/RequireAuth'
import { useSession } from '@/hooks/useSession'

/**
 * 관리자 화면(`/admin/*`, SCR-14~18)을 감싸는 라우트 가드다.
 *
 * RequireAuth와 같은 방식(경로 없는 부모 라우트)으로 관리자 화면 전체를 한 번에 막는다.
 * 로그인 확인(확인 중 로딩 → 비로그인이면 로그인 화면)은 RequireAuth에 맡기고, 통과한 사람에게만 역할(role)을 본다.
 *
 * 데모 계정(DEMO)은 조회만 하도록 통과시킨다(아래 AdminRoleGate 주석, Task 109).
 * 로그인했지만 ADMIN(또는 DEMO)이 아니면: "접근 권한이 없습니다" 안내 화면을 보이고 홈으로 가는 링크를 둔다(Task 057 결정).
 * 홈으로 자동 이동하지 않는 이유: 주소를 잘못 눌렀거나 관리자 권한이 아직 반영되지 않은 사용자가 "왜 쫓겨났는지" 모른 채
 * 첫 화면에 도착하는 것보다, 권한이 없다는 사실을 알려 주는 편이 원인을 알기 쉽다. 백엔드 `/api/admin/**`의 403(FORBIDDEN, "접근 권한이 없습니다.")과
 * 같은 문구·기준이다.
 *
 * 보안상 전제: 관리자 여부를 화면에서 판단하는 것은 메뉴·화면을 숨기는 편의 기능이다.
 * 역할 값은 메모리(authStore)의 값이고 재발급 때마다 서버가 DB에서 다시 읽어 준다(주소·저장소의 값은 믿지 않는다).
 * 실제 권한은 백엔드가 `/api/admin/**`에서 ADMIN 역할로 검사한다(FR-17). 이 가드가 뚫려도
 * 관리자 API는 403으로 막혀야 하며, 화면 가드에 보안을 의존하지 않는다.
 * 관리자 화면 코드는 router.tsx에서 lazy로 분리해 일반 사용자가 받는 첫 번들에 섞이지 않게 했다.
 */
export function RequireAdmin() {
  return (
    <RequireAuth>
      <AdminRoleGate />
    </RequireAuth>
  )
}

/*
 * RequireAuth를 통과한 뒤(로그인 확인 완료) 역할만 확인한다.
 *
 * 데모 계정(DEMO, Task 109)도 통과시킨다: 관리자 화면을 "조회만" 할 수 있게 한 결정이다(서버도 /api/admin/**의 GET만 DEMO에게 허락한다).
 * [보안] 화면을 보여 줘도 위험이 늘지 않는 이유: 쓰기·실행은 서버가 DEMO에게 403으로 막고, 화면도 확정 버튼을 막아(DemoLockedButton) 요청을 보내지 않는다.
 * 이 가드는 여전히 편의 기능이고, 역할 값은 재발급 때마다 서버가 DB에서 다시 읽은 메모리 값이다.
 */
function AdminRoleGate() {
  const { role } = useSession()

  // 이전: if (role !== 'ADMIN') — 데모 계정이 생기기 전에는 ADMIN만 통과했다
  if (role !== 'ADMIN' && role !== 'DEMO') {
    return (
      <section className="mx-auto flex w-full max-w-md flex-col gap-3">
        <h1 className="text-2xl font-semibold">접근 권한이 없습니다</h1>
        <p className="text-muted-foreground">이 화면은 관리자만 볼 수 있습니다.</p>
        {/*
         * 링크 크기 보정(Task 057·058 시각 검수): 이전에는 글자 높이(약 24px)만큼만 눌렸고, 세로 flex의 기본 늘이기(stretch) 때문에
         * 누르는 영역이 줄 전체 너비로 퍼져 있었다. min-h-11(44px)로 사용자 화면 버튼 높이(Task 037)와 맞추고,
         * self-start로 누르는 영역을 글자 폭에 맞춘다. 겉모양(밑줄 링크)은 그대로다.
         * 이전 값: className="underline underline-offset-4"
         */}
        <Link
          to="/"
          className="inline-flex min-h-11 items-center self-start underline underline-offset-4"
        >
          홈으로 이동
        </Link>
      </section>
    )
  }

  return <Outlet />
}
