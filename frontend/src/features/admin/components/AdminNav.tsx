import { NavLink } from 'react-router'
import { cn } from 'cn'

// 관리자 메뉴 항목. 주소는 router.tsx의 관리자 경로와 같다(SCR-14~18)
const ADMIN_NAV_ITEMS: {
  to: string
  label: string
  isComingSoon: boolean // true면 "준비 중" 태그를 붙인다(화면은 ComingSoon 자리표시)
  showsUnmappedCount: boolean // true면 미매핑 제공처 수를 옆에 붙인다
}[] = [
  { to: '/admin/products', label: 'OTT 구독상품', isComingSoon: false, showsUnmappedCount: false },
  { to: '/admin/providers', label: '제공처 매핑', isComingSoon: false, showsUnmappedCount: true },
  { to: '/admin/collect', label: '수집', isComingSoon: false, showsUnmappedCount: false },
  { to: '/admin/monitoring', label: '모니터링', isComingSoon: true, showsUnmappedCount: false },
  { to: '/admin/llm', label: 'LLM 검토', isComingSoon: true, showsUnmappedCount: false },
]

interface AdminNavProps {
  // 미매핑 제공처 수. 0보다 크면 "제공처 매핑 (2)"처럼 보인다. 아직 모르거나 0이면 숫자를 붙이지 않는다
  unmappedProviderCount?: number
}

/**
 * 관리자 화면 위쪽의 메뉴(탭처럼 보이는 링크 줄)다(Task 029).
 *
 * shadcn Tabs가 아니라 링크(NavLink)로 만든 이유: Tabs는 "한 화면 안에서 패널을 바꾸는" 부품이라 화면 낭독기가 "탭 1/5"로 읽고
 * 화살표 키로 이동한다. 관리자 메뉴는 주소가 바뀌는 화면 이동이므로 <nav> 안의 링크가 뜻에 맞다.
 * NavLink는 현재 주소와 같은 링크에 aria-current="page"를 자동으로 붙여, 낭독기가 "현재 페이지"라고 알려 준다.
 *
 * 현재 메뉴 표시는 색만으로 하지 않는다: 2px 밑줄 + 굵은 글자 + 밝은 글자색을 함께 쓴다.
 * 375px에서는 줄바꿈(flex-wrap)으로 두 줄이 된다. 가로 스크롤 띠는 뒤쪽 메뉴가 잘려 안 보이게 되므로 쓰지 않는다.
 */
export function AdminNav({ unmappedProviderCount }: AdminNavProps) {
  const hasUnmapped = unmappedProviderCount != null && unmappedProviderCount > 0

  return (
    <nav aria-label="관리자 메뉴">
      <ul className="flex flex-wrap gap-x-5 gap-y-1 border-b border-border">
        {ADMIN_NAV_ITEMS.map(({ to, label, isComingSoon, showsUnmappedCount }) => (
          <li key={to}>
            <NavLink
              to={to}
              className={({ isActive }) =>
                cn(
                  // min-h-10: 터치 대상 40px. -mb-px: 밑줄이 목록 아래 구분선 위에 겹쳐 보이게 한다
                  '-mb-px inline-flex min-h-10 items-center gap-1.5 rounded-t-sm border-b-2 px-1 text-sm',
                  'focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none',
                  isActive
                    ? 'border-primary font-semibold text-foreground'
                    : 'border-transparent text-muted-foreground hover:text-foreground',
                )
              }
            >
              <span>{label}</span>
              {showsUnmappedCount && hasUnmapped && (
                <>
                  {/* 눈에 보이는 "(2)"는 낭독기에서 숨기고, 낭독기에는 뜻이 분명한 문장을 따로 읽힌다 */}
                  <span aria-hidden="true" className="tabular-nums">
                    ({unmappedProviderCount})
                  </span>
                  <span className="sr-only">, 미매핑 {unmappedProviderCount}건</span>
                </>
              )}
              {isComingSoon && (
                // "준비 중"은 색이 아니라 글자로 알린다. 점선 테두리는 "아직 제공 전"이라는 보조 모양이다
                <span className="rounded-full border border-dashed border-input px-1.5 text-xs leading-4 font-normal text-muted-foreground">
                  준비 중
                </span>
              )}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  )
}
