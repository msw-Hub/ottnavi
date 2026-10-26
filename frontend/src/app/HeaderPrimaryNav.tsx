import { NavLink } from 'react-router'
import { cn } from '@/lib/utils'

// 머리글 주 메뉴. 순서가 화면에 보이는 순서다(홈 1차안: 홈 · 랭킹 · 탐색)
const PRIMARY_MENU = [
  { to: '/', label: '홈', isEnd: true }, // isEnd: `/`는 모든 주소의 앞부분이라 정확히 `/`일 때만 현재 메뉴로 본다
  { to: '/ranking', label: '랭킹', isEnd: false },
  { to: '/explore', label: '탐색', isEnd: false },
] as const

interface HeaderPrimaryNavProps {
  className?: string // 머리글 안 배치를 덧붙일 때만 쓴다
}

/**
 * 머리글의 주 메뉴(홈 · 랭킹 · 탐색)다.
 *
 * NavLink를 쓰는 이유: 지금 주소와 맞는 메뉴에 aria-current="page"를 자동으로 붙여, 낭독기가 "현재 페이지"라고 읽는다.
 * 현재 메뉴 표시는 색만으로 하지 않는다: 글자가 밝아지는 것에 더해 글자 아래 2px 막대(after:)가 생긴다(흑백에서도 보인다).
 * 누르는 영역은 min-h-10(40px)·좌우 px-3이다. -my-2는 넓힌 높이가 머리글 줄 높이를 키우지 않게 하는 장치다(HeaderAccountNav와 같은 방식).
 * 모바일(640px 미만)에서도 로고 바로 옆에 둔다(보이는 순서 = Tab 순서). 세 항목뿐이라 375px에서 로고와 한 줄에 들어간다.
 */
export function HeaderPrimaryNav({ className }: HeaderPrimaryNavProps) {
  return (
    <nav aria-label="주 메뉴" className={cn('flex items-center', className)}>
      {/* 이전: className="-ml-3 flex items-center gap-1 sm:ml-0"(모바일 둘째 줄에서 로고 글자와 왼쪽을 맞추던 값). 지금은 모든 폭에서 로고 옆이다 */}
      <ul className="flex items-center gap-1">
        {PRIMARY_MENU.map((menu) => (
          <li key={menu.to}>
            <NavLink
              to={menu.to}
              end={menu.isEnd}
              className={({ isActive }) =>
                cn(
                  'relative -my-2 inline-flex min-h-10 items-center rounded-md px-3 text-[0.9375rem] font-semibold underline-offset-4 hover:text-foreground',
                  // 현재 메뉴 아래 막대: 글자 폭 안쪽(좌우 px-3만큼 들여서)에 그린다
                  "after:absolute after:inset-x-3 after:bottom-1 after:h-0.5 after:rounded-full after:content-['']",
                  isActive ? 'text-foreground after:bg-primary' : 'text-muted-foreground',
                )
              }
            >
              {menu.label}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  )
}
