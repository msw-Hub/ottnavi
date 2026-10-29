import { useEffect } from 'react'
import { Outlet } from 'react-router'
import { DEMO_READONLY_NOTE_ID, DemoReadOnlyHint } from '@/components/common/DemoReadOnlyHint'
import { useAdminProviders } from '@/features/admin/hooks/useAdminProviders'
import { countUnmapped } from '@/features/admin/providerMapping'
import { AdminNav } from '@/features/admin/components/AdminNav'

/**
 * 관리자 화면(SCR-14~18)이 함께 쓰는 틀이다. 맨 위에 "관리자" 표시와 메뉴(AdminNav)를 두고, 그 아래 <Outlet />에 각 화면이 들어간다(Task 029).
 *
 * 쓰는 방법(frontend-dev가 router.tsx에 연결): RequireAdmin 아래에 "경로 없는 레이아웃 라우트"로 한 번 더 감싼다.
 *   { Component: RequireAdmin, children: [{ Component: AdminLayout, children: [ ...관리자 화면들 ] }] }
 * 이렇게 하면 화면을 옮겨 다녀도 메뉴는 다시 만들어지지 않고 본문만 바뀐다. 모니터링·LLM(준비 중) 화면에도 메뉴가 계속 보인다.
 *
 * "관리자"를 제목(h1)이 아닌 작은 글자로 둔 이유: 각 화면이 자기 제목(h1, 예: "OTT 구독상품 관리")을 갖는다.
 * 한 화면에 h1이 둘이면 낭독기 사용자가 제목으로 화면을 파악하기 어렵다.
 *
 * 미매핑 제공처 수(unmappedProviderCount)는 제공처 목록 훅(useAdminProviders)에서 센다. 제공처 화면과 같은 쿼리 키라
 * 요청 하나를 함께 쓰고, 매핑을 저장하면 목록이 다시 불러와져 숫자가 바로 줄어든다. 목록을 아직 못 불러왔거나 실패했으면
 * 숫자 없이 메뉴만 보인다(data가 없으면 undefined를 넘긴다).
 */
export function AdminLayout() {
  const providers = useAdminProviders()
  const unmappedCount = providers.data ? countUnmapped(providers.data.providers) : undefined

  // 관리자 밝은 테마: 마운트 동안 <html data-theme="admin">을 붙이고 떠날 때 뗀다(styles/admin-theme.css).
  // portal로 body 아래에 그려지는 대화상자·드롭다운까지 밝게 하려고 wrapper가 아니라 루트 속성을 쓴다
  useEffect(() => {
    document.documentElement.dataset.theme = 'admin'
    return () => {
      delete document.documentElement.dataset.theme
    }
  }, [])

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-1">
        <p className="text-sm font-medium text-muted-foreground">관리자</p>
        <AdminNav unmappedProviderCount={unmappedCount} />
      </div>
      {/* 데모 계정에게만 보이는 조회 전용 안내(Task 109). 표 행 안의 막힌 버튼들이 이 안내의 id를 설명으로 가리킨다 */}
      <DemoReadOnlyHint variant="banner" id={DEMO_READONLY_NOTE_ID} />
      <Outlet />
    </div>
  )
}
