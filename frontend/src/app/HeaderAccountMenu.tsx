import {
  BellIcon,
  CalendarDaysIcon,
  ChevronDownIcon,
  HeartIcon,
  HistoryIcon,
  LogOutIcon,
  ShieldIcon,
  SlidersHorizontalIcon,
  UserIcon,
  WalletIcon,
} from 'lucide-react'
import type { ReactNode } from 'react'
import { Link, useLocation } from 'react-router'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { useLogout } from '@/hooks/useLogout'
import { cn } from '@/lib/utils'

// 메뉴 항목 공통 모양. shadcn 기본(포커스 시 배경만 --accent)은 팝업 바탕과의 밝기 차가 작아 키보드 위치를 알아보기 어렵다.
// 그래서 포커스된 항목 안쪽에 2px 테두리(ring-inset)를 더한다. 원본(components/ui)은 건드리지 않고 className으로만 덮어쓴다.
// min-h-10(40px): 머리글의 다른 링크와 같은 누르는 영역 규칙(Task 037 ⑫)
const ITEM_CLASS =
  'min-h-10 w-full gap-2.5 px-2.5 py-0 text-sm focus:ring-2 focus:ring-ring focus:ring-inset'

// 현재 화면 항목 표시: 왼쪽 세로 막대(모양) + 굵은 글자 + 밝은 아이콘. aria-current는 MenuLink가 따로 붙인다(색만으로 구분하지 않는다)
const CURRENT_ITEM_CLASS =
  'font-bold before:absolute before:inset-y-2 before:left-0.5 before:w-[3px] before:rounded-full before:bg-primary'

interface MenuLinkProps {
  to: string // 이동할 경로(검색어 없이 경로만)
  search: string // 목업 확인용 주소 파라미터(없으면 빈 문자열). 운영에서는 늘 빈 문자열이다
  currentPath: string // 지금 보고 있는 경로. to와 정확히 같을 때만 "현재 화면"으로 본다
  icon: ReactNode // 앞쪽 장식 아이콘(낭독기에서는 숨긴다)
  accessibleName?: string // 보이는 글자와 다르게 읽어 줄 이름(개수·준비 중 같은 부가 정보를 함께 읽히려는 용도)
  end?: ReactNode // 오른쪽 끝에 붙는 부가 표시(개수, "준비 중")
  children: ReactNode // 항목 이름
}

/**
 * 메뉴 안의 이동 항목 하나다. Radix의 DropdownMenuItem(asChild)이 키보드 이동·닫힘을 맡고, 실제 요소는 react-router의 Link다.
 * (Link를 쓰는 이유: 화면 전체를 다시 불러오지 않는 앞쪽 이동이고, 새 탭 열기·주소 복사 같은 링크 기본 동작을 지킨다)
 *
 * 현재 화면 판정: 경로가 "정확히" 같을 때만이다. startsWith로 비교하면 /settings 항목이
 * /settings/notifications 화면에서도 현재로 보이고, /plan 항목이 /plan/history에서도 현재로 보인다.
 */
function MenuLink({ to, search, currentPath, icon, accessibleName, end, children }: MenuLinkProps) {
  const isCurrent = currentPath === to
  return (
    <DropdownMenuItem asChild className={cn(ITEM_CLASS, isCurrent && CURRENT_ITEM_CLASS)}>
      <Link
        to={{ pathname: to, search }}
        aria-current={isCurrent ? 'page' : undefined}
        aria-label={accessibleName}
      >
        {/* 현재 화면이면 아이콘도 글자색(foreground)으로 밝혀 굵은 글자와 무게를 맞춘다(시안 A와 같다). 이전: className="flex shrink-0 text-muted-foreground" */}
        <span
          aria-hidden="true"
          className={cn('flex shrink-0', isCurrent ? 'text-foreground' : 'text-muted-foreground')}
        >
          {icon}
        </span>
        {children}
        {/* 오른쪽 끝 부가 표시(개수, "준비 중")는 현재 화면 항목에서도 그대로 보인다. 현재 화면은 막대·굵은 글자·밝은 아이콘·aria-current로 알린다.
            이전: 현재 화면이면 이 자리에 "현재" 글자를 대신 그렸다(사용자 결정으로 뺐다. 위 단서만으로 색에 기대지 않고 알릴 수 있고, 개수·준비 중이 가려지는 문제가 있었다)
            {isCurrent ? (
              <span aria-hidden="true" className="ml-auto text-xs font-medium text-muted-foreground">현재</span>
            ) : (
              end
            )} */}
        {end}
      </Link>
    </DropdownMenuItem>
  )
}

// 묶음 제목. 눈으로만 보이게 하고(aria-hidden) 낭독기에는 그룹의 aria-label이 대신 읽힌다.
// Radix Label은 그룹 이름으로 연결되지 않아서, 제목을 읽히려면 Group에 이름을 직접 줘야 한다
function GroupLabel({ children }: { children: ReactNode }) {
  return (
    <DropdownMenuLabel aria-hidden="true" className="px-2.5 pt-1.5 pb-0.5">
      {children}
    </DropdownMenuLabel>
  )
}

interface HeaderAccountMenuProps {
  isAdmin: boolean // ADMIN이면 "관리자" 묶음을 보인다. 표시만 바꾸는 것이고 실제 권한 검사는 백엔드와 라우트 가드가 한다
  isDemo: boolean // 데모 계정(DEMO)이면 맨 위에 "데모 계정" 표시를 두고, 관리자 묶음을 "조회만" 표시와 함께 보인다(Task 109)
  wishlistCount: number // 계산 대상 찜 개수. 머리글 하트 배지와 같은 값을 받아 쓴다(HeaderAccountNav가 한 번만 요청해서 내려준다)
  search: string // 모든 이동 링크에 이어 붙일 목업 확인용 주소 파라미터("?mockScenario=…" 꼴 또는 빈 문자열)
}

/**
 * 로그인한 사용자의 머리글 "내 메뉴" 드롭다운이다(시안 A).
 *
 * 구성: 플랜(내 플랜, 변경 내역) / 내 정보(설정, 요금, 찜, 알림) / 관리자(ADMIN만) / 로그아웃.
 * 로그아웃과 관리 링크는 예전에 머리글에 따로 있었고 이 메뉴 안으로 옮겼다. 찜 하트(배지)는 머리글에 남기고 메뉴에도 찜 목록을 둔다.
 *
 * 왜 shadcn DropdownMenu(Radix)인가: 열고 닫기, 방향키·Home·End 이동, Esc로 닫고 버튼으로 포커스 복귀, 바깥 클릭 닫기,
 * 화면 가장자리에서 위치 보정을 직접 만들지 않아도 된다(검증된 부품 우선).
 * 접근성 주의: Radix는 role="menu"/"menuitem"(WAI-ARIA의 명령 메뉴 패턴)을 쓴다. 항목이 링크라서 낭독기에는 "메뉴 항목"으로 읽힌다.
 * 사이트 이동 목록은 원래 "열고 닫는 목록(disclosure)"이 더 정석이지만, 계정 메뉴는 널리 쓰이는 관례이고 Radix가 키보드 동작을 보장해
 * 이 패턴을 택했다. 한계는 보고서에 적었다.
 *
 * 머리글 높이를 키우지 않게 버튼에 -my-2 min-h-10을 쓴다(HeaderAccountNav의 다른 링크와 같은 이유, 토스트 위치 --header-height).
 * 모바일(375px)은 아이콘 + 화살표(폭 48px), sm(640px) 이상은 "내 메뉴" 글자도 보인다. 글자는 시각용이고 이름은 aria-label이 정한다.
 */
export function HeaderAccountMenu({
  isAdmin,
  isDemo,
  wishlistCount,
  search,
}: HeaderAccountMenuProps) {
  const { pathname } = useLocation()
  // 로그아웃: 서버에 폐기를 요청하고, 메모리의 토큰·쿼리 캐시를 비운 뒤 첫 화면으로 보낸다(hooks/useLogout)
  const logout = useLogout()
  const hasWishlist = wishlistCount > 0

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button
          type="button"
          aria-label="내 메뉴"
          className="group -my-2 inline-flex min-h-10 min-w-12 items-center justify-center gap-1 rounded-md px-1.5 text-sm hover:bg-accent hover:text-accent-foreground focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none data-[state=open]:bg-accent sm:px-2.5"
        >
          <UserIcon aria-hidden="true" className="size-5" />
          <span aria-hidden="true" className="max-sm:hidden">
            내 메뉴
          </span>
          <ChevronDownIcon
            aria-hidden="true"
            className="size-3.5 text-muted-foreground transition-transform group-data-[state=open]:rotate-180 motion-reduce:transition-none"
          />
        </button>
      </DropdownMenuTrigger>

      {/*
        align="end": 버튼 오른쪽 끝에 맞춘다. w-60: shadcn 기본(버튼 폭을 따름)이면 모바일에서 48px로 줄어들어 덮어쓴다. max-w는 아주 좁은 화면 보호.
        focus-visible:outline-none, focus-visible:ring-foreground/10!: 메뉴 상자 자체의 포커스 테두리를 끈다(시각 검수에서 찾은 문제).
          마우스로 열면 Radix가 항목이 아니라 메뉴 상자에 포커스를 둔다. 그 직전에 키보드를 썼다면(검색어 입력 등) 브라우저가 이를 키보드 포커스로 보고,
          index.css의 전역 :focus-visible 규칙이 상자의 옅은 테두리(ring-1 ring-foreground/10)와 윤곽선을 진한 하늘색으로 바꿔 메뉴 전체가 선택된 것처럼 보였다.
          상자는 누르는 대상이 아니라 표시가 필요 없고, 방향키로 항목에 가면 항목의 안쪽 2px 테두리가 위치를 알려 준다.
          !(important)를 쓴 이유: 전역 규칙이 층(@layer) 밖에 있어 보통 유틸리티로는 못 이긴다. 색만 원래 값으로 되돌린다.
        이전: className="w-60 max-w-[calc(100vw-2rem)]"
      */}
      <DropdownMenuContent
        align="end"
        className="w-60 max-w-[calc(100vw-2rem)] focus-visible:ring-foreground/10! focus-visible:outline-none"
      >
        {/*
          데모 계정 표시(Task 109). 누르는 항목이 아니라 지금 어떤 계정인지 알리는 머리말이다(DropdownMenuLabel은 키보드 이동에서 건너뛴다).
          알약 모양 테두리 + 글자라 색만으로 알리지 않는다
        */}
        {isDemo && (
          <>
            <DropdownMenuLabel className="grid gap-1 px-2.5 pt-2 pb-1.5">
              <span className="w-fit rounded-full border border-info bg-info-bg px-2 text-xs font-semibold text-info">
                데모 계정
              </span>
              <span className="text-xs font-normal text-muted-foreground">
                공용 계정으로 로그인했어요
              </span>
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
          </>
        )}

        <DropdownMenuGroup aria-label="플랜">
          <GroupLabel>플랜</GroupLabel>
          <MenuLink
            to="/plan"
            search={search}
            currentPath={pathname}
            icon={<CalendarDaysIcon className="size-4" />}
          >
            내 플랜
          </MenuLink>
          <MenuLink
            to="/plan/history"
            search={search}
            currentPath={pathname}
            icon={<HistoryIcon className="size-4" />}
          >
            플랜 변경 내역
          </MenuLink>
        </DropdownMenuGroup>

        <DropdownMenuSeparator />

        <DropdownMenuGroup aria-label="내 정보">
          <GroupLabel>내 정보</GroupLabel>
          <MenuLink
            to="/settings"
            search={search}
            currentPath={pathname}
            icon={<SlidersHorizontalIcon className="size-4" />}
          >
            내 설정
          </MenuLink>
          <MenuLink
            to="/pricing"
            search={search}
            currentPath={pathname}
            icon={<WalletIcon className="size-4" />}
          >
            요금 확인
          </MenuLink>
          <MenuLink
            to="/wishlist"
            search={search}
            currentPath={pathname}
            icon={<HeartIcon className="size-4" />}
            accessibleName={hasWishlist ? `찜 목록, ${wishlistCount}개` : undefined}
            end={
              hasWishlist ? (
                <span
                  aria-hidden="true"
                  className="ml-auto text-xs font-medium text-muted-foreground tabular-nums"
                >
                  {wishlistCount}개
                </span>
              ) : undefined
            }
          >
            찜 목록
          </MenuLink>
          {/* 알림 설정은 실제 화면(NotificationSettingsPage, Task 071~073)으로 이어지는 일반 링크다.
              이전: "준비 중" 알약(end)과 accessibleName="알림 설정, 준비 중"을 붙였다(2단계 자리표시 시절).
              <MenuLink ... accessibleName="알림 설정, 준비 중"
                end={<span aria-hidden="true" className="ml-auto inline-flex h-5 items-center rounded-full border border-dashed border-input px-1.5 text-[0.6875rem] font-medium text-muted-foreground">준비 중</span>}> */}
          <MenuLink
            to="/settings/notifications"
            search={search}
            currentPath={pathname}
            icon={<BellIcon className="size-4" />}
          >
            알림 설정
          </MenuLink>
        </DropdownMenuGroup>

        {/* 데모 계정도 관리자 화면을 조회할 수 있어 같은 묶음을 보이고, 쓰기가 막혀 있음을 "조회만" 표시로 알린다 */}
        {(isAdmin || isDemo) && (
          <>
            <DropdownMenuSeparator />
            <DropdownMenuGroup aria-label="관리자">
              <GroupLabel>관리자</GroupLabel>
              <MenuLink
                to="/admin/collect"
                search={search}
                currentPath={pathname}
                icon={<ShieldIcon className="size-4" />}
                accessibleName={isDemo ? '관리 화면, 조회만 가능' : undefined}
                end={
                  isDemo ? (
                    <span
                      aria-hidden="true"
                      className="ml-auto inline-flex h-5 items-center rounded-full border border-dashed border-input px-1.5 text-[0.6875rem] font-medium text-muted-foreground"
                    >
                      조회만
                    </span>
                  ) : undefined
                }
              >
                관리 화면
              </MenuLink>
            </DropdownMenuGroup>
          </>
        )}

        <DropdownMenuSeparator />

        {/* 빨강으로 칠하지 않는다: 데이터가 지워지는 동작이 아니다.
            회원 탈퇴는 이 메뉴에 두지 않고 푸터(app/RootLayout.tsx, 로그인한 사용자에게만)에 둔다. 빨강은 탈퇴 확인 화면에서만 쓴다.
            이전: (회원 탈퇴는 이 메뉴에 두지 않고 내 설정 안에서만 한다) — 내 설정 맨 아래 링크를 푸터로 옮기며 고쳤다 */}
        <DropdownMenuItem className={ITEM_CLASS} onSelect={() => void logout()}>
          <LogOutIcon aria-hidden="true" className="size-4 text-muted-foreground" />
          로그아웃
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
