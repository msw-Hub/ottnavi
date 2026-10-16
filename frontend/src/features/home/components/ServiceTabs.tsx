import type { ReactNode } from 'react'
import { CheckIcon } from 'lucide-react'
import { Tabs } from 'radix-ui'

export interface ServiceTabOption {
  value: string // 탭 값(서비스 코드 또는 'ALL')
  label: string // 탭 글자(서비스 이름)
}

interface ServiceTabsProps {
  label: string // 탭 묶음 이름(낭독기용, 예: "인기 순위 서비스 선택")
  value: string // 지금 고른 탭 값
  onValueChange: (value: string) => void // 탭을 바꿨을 때
  options: ServiceTabOption[] // 탭 목록. 순서가 화면 순서다
  children: ReactNode // 고른 탭의 내용(목록). 고른 탭 하나의 내용만 넘긴다
}

/**
 * 서비스(넷플릭스·티빙 등)를 고르는 알약 모양 탭이다(키노라이츠의 서비스 탭을 우리 토큰으로 옮긴 것). 로고 이미지 없이 이름 글자만 쓴다.
 *
 * Radix Tabs를 쓰는 이유: 탭 역할(tablist/tab/tabpanel), 고른 탭 알림(aria-selected), 화살표 키 이동(좌우·Home·End)을
 * 이미 지원해 직접 만드는 것보다 안전하다(context7 /websites/radix-ui_primitives Tabs 확인). shadcn의 tabs 원본은 모양이 달라
 * 덮어쓸 클래스가 많아서, 같은 Radix 부품을 이 파일에서 직접 감쌌다.
 *
 * 고른 탭 표시는 색만으로 하지 않는다: 바탕이 채워지는 것(테두리만 → 꽉 채움)에 더해 체크 아이콘이 붙는다(흑백에서도 구분).
 * 탭 높이 h-11(44px)은 손가락 터치 크기 기준이다. 탭이 8개라 375px에서는 한 줄이 넘쳐 탭 줄만 옆으로 밀어 본다(overflow-x-auto).
 * 고른 탭의 내용만 Tabs.Content 하나로 그린다. 고르지 않은 탭의 내용은 만들지 않아 목록 8개를 한꺼번에 그리지 않는다.
 */
export function ServiceTabs({ label, value, onValueChange, options, children }: ServiceTabsProps) {
  return (
    <Tabs.Root value={value} onValueChange={onValueChange} className="grid grid-cols-1 gap-4">
      {/* -mx-4 px-4: 모바일에서 탭 줄을 화면 끝까지 늘여 넘쳐 있는 탭이 잘린 모양으로 보이게 한다. py-2는 포커스 링이 잘리지 않게 하는 여백이다.
          min-h-15(60px = 탭 44px + 위아래 8px): 가로 스크롤 영역이 그리드 칸 안에 있으면 크롬이 높이를 내용보다 약 10px 작게 잡아 탭 아래쪽이 잘렸다
          (Playwright 실측: py-1이면 6px, py-2여도 2px 잘림. 숨긴 가로 스크롤바 높이만큼 빠지는 것으로 보인다). 최소 높이를 못 박아 막는다 */}
      <div className="-mx-4 min-h-15 overflow-x-auto px-4 py-2 [scrollbar-width:none]">
        <Tabs.List aria-label={label} className="flex w-max gap-2">
          {options.map((option) => (
            <Tabs.Trigger
              key={option.value}
              value={option.value}
              className="group inline-flex h-11 shrink-0 items-center gap-1 rounded-full border border-input px-4 text-sm font-semibold whitespace-nowrap text-muted-foreground hover:text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring data-[state=active]:border-primary data-[state=active]:bg-primary data-[state=active]:text-primary-foreground"
            >
              {/* 고른 탭에만 보이는 체크. 글자와 같은 뜻의 보조 표시라 낭독기에서는 숨긴다(고른 상태는 aria-selected가 알린다) */}
              <CheckIcon
                aria-hidden="true"
                strokeWidth={3}
                className="hidden size-4 group-data-[state=active]:block"
              />
              {option.label}
            </Tabs.Trigger>
          ))}
        </Tabs.List>
      </div>
      {/* Radix의 탭 내용 칸은 Tab 키로 한 번 머무른다(tabIndex=0). 그때 어디에 있는지 보이게 포커스 링을 남긴다 */}
      <Tabs.Content
        value={value}
        className="rounded-lg focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-ring"
      >
        {children}
      </Tabs.Content>
    </Tabs.Root>
  )
}
