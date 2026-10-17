import { useEffect, useRef, type ReactNode } from 'react'
import { CheckIcon, DatabaseIcon } from 'lucide-react'
import { Tabs } from 'radix-ui'
import type { ExclusiveService } from '@/features/exclusives/mockTypes'

interface ExclusiveServiceTabsProps {
  services: ExclusiveService[] // 탭으로 보일 서비스 7곳. 순서가 화면 순서다
  value: string // 지금 고른 탭 값(서비스 코드, 예: 'NETFLIX')
  onValueChange: (value: string) => void // 탭을 바꿨을 때
  children: ReactNode // 고른 탭의 내용. 고른 탭 하나의 내용만 넘긴다
}

/**
 * 독점 목록 화면의 서비스 탭 7개다(SCR-03).
 *
 * 홈의 서비스 탭(features/home/components/ServiceTabs)과 같은 모양(알약 탭, 고른 탭은 꽉 채움 + 체크)이다.
 * 기능 폴더끼리 import하지 않는 규칙(frontend.md) 때문에 따로 두었고, 하나 다른 점이 있다:
 * 데이터 부족 서비스(쿠팡플레이) 탭에는 이름 뒤에 데이터 아이콘을 붙여, 누르기 전에도 "이 탭은 목록이 아니라 안내"라는 것을 알린다.
 * 아이콘만으로는 뜻이 전달되지 않으니 낭독기에는 "(데이터 부족)"을 읽히고, 마우스를 올리면 title로도 보인다.
 * 두 탭 모양이 앞으로도 같다면 components/common으로 올려 하나로 합칠 수 있다(보고서 결정 질문).
 *
 * Radix Tabs를 쓰는 이유: 탭 역할(tablist/tab/tabpanel), 고른 탭 알림(aria-selected), 좌우·Home·End 키 이동을 이미 지원한다
 * (context7 /websites/radix-ui_primitives Tabs, 홈 탭과 같은 근거). 탭 높이 h-11(44px)은 손가락 터치 크기 기준이다.
 * 탭이 7개라 375px에서는 한 줄이 넘쳐 탭 줄만 옆으로 밀어 본다(overflow-x-auto). 화면 전체에는 가로 스크롤이 생기지 않는다.
 */
export function ExclusiveServiceTabs({
  services,
  value,
  onValueChange,
  children,
}: ExclusiveServiceTabsProps) {
  const scrollRef = useRef<HTMLDivElement>(null) // 옆으로 밀리는 탭 줄 칸

  // 고른 탭이 탭 줄 안에서 보이게 줄을 밀어 준다. 주소로 바로 열면(?service=WATCHA) 고른 탭이 오른쪽 바깥에 있어 보이지 않기 때문이다.
  // scrollIntoView를 쓰지 않은 이유: 페이지 전체가 위아래로 같이 움직일 수 있어, 탭 줄의 scrollLeft만 직접 맞춘다.
  // value가 바뀔 때마다 실행해 키보드(좌우 화살표)로 옮겼을 때도 따라가게 한다. 이미 보이는 위치면 움직이지 않는다.
  useEffect(() => {
    // 1. 탭 줄 칸과 고른 탭을 찾는다
    const container = scrollRef.current
    const active = container?.querySelector<HTMLElement>('[role="tab"][data-state="active"]')
    if (!container || !active) return
    // 2. 고른 탭이 칸의 보이는 범위(좌우 여백 16px 포함) 밖이면 그 범위 안으로 들어오게 민다
    const margin = 16
    const left = active.offsetLeft - margin
    const right = active.offsetLeft + active.offsetWidth + margin
    if (left < container.scrollLeft) {
      container.scrollLeft = left
    } else if (right > container.scrollLeft + container.clientWidth) {
      container.scrollLeft = right - container.clientWidth
    }
  }, [value])

  return (
    <Tabs.Root value={value} onValueChange={onValueChange} className="grid grid-cols-1 gap-4">
      {/* -mx-4 px-4: 모바일에서 탭 줄을 화면 끝까지 늘여 넘쳐 있는 탭이 잘린 모양으로 보이게 한다(더 있다는 신호).
          py-2와 min-h-15는 포커스 링과 탭 아래쪽이 잘리지 않게 하는 여백이다(홈 탭의 Playwright 실측 근거와 같다) */}
      <div
        ref={scrollRef}
        className="relative -mx-4 min-h-15 overflow-x-auto px-4 py-2 [scrollbar-width:none]"
      >
        <Tabs.List aria-label="독점 작품을 볼 서비스 선택" className="flex w-max gap-2">
          {services.map((service) => {
            const isInsufficient = service.dataQuality === 'INSUFFICIENT'
            return (
              <Tabs.Trigger
                key={service.code}
                value={service.code}
                title={isInsufficient ? `${service.name}: 데이터 부족` : undefined}
                className="group relative inline-flex h-11 shrink-0 items-center gap-1 rounded-full border border-input px-4 text-sm font-semibold whitespace-nowrap text-muted-foreground hover:text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring data-[state=active]:border-primary data-[state=active]:bg-primary data-[state=active]:text-primary-foreground"
              >
                {/* 고른 탭에만 보이는 체크. 고른 상태는 aria-selected가 알리므로 낭독기에서는 숨긴다 */}
                <CheckIcon
                  aria-hidden="true"
                  strokeWidth={3}
                  className="hidden size-4 group-data-[state=active]:block"
                />
                {service.name}
                {/* 탭에 relative를 둔 이유: sr-only 글자는 position:absolute라 기준 요소가 없으면 가로 스크롤 칸을 벗어나
                    문서 폭을 늘린다(375px 실측: 화면 폭이 539px로 늘어남). 탭을 기준으로 잡아 스크롤 칸 안에 가둔다 */}
                {isInsufficient && (
                  <>
                    <DatabaseIcon aria-hidden="true" className="size-3.5 opacity-80" />
                    <span className="sr-only">(데이터 부족)</span>
                  </>
                )}
              </Tabs.Trigger>
            )
          })}
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
