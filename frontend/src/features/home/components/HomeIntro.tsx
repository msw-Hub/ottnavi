import type { ReactNode } from 'react'
import { CalculatorIcon, HeartIcon, SlidersHorizontalIcon, type LucideIcon } from 'lucide-react'
import { Link } from 'react-router'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

// 처음 온 사람에게 보이는 3단계 안내. 순서가 화면 순서다(PRD 4절 시나리오: 찜 → 설정 → 계산)
const START_STEPS: { icon: LucideIcon; title: string; description: string }[] = [
  {
    icon: HeartIcon,
    title: '보고 싶은 작품 찜하기',
    description: '검색해서 찜하고, 꼭 볼 작품인지 우선순위를 골라요.',
  },
  {
    icon: SlidersHorizontalIcon,
    title: '내 상황 알려 주기',
    description: '이미 구독 중인 서비스, 월 예산, 한 달 시청 시간을 입력해요.',
  },
  {
    icon: CalculatorIcon,
    title: '3개월 플랜 받기',
    description: '어느 달에 어디를 구독하면 좋을지 계산해 보여 드려요.',
  },
]

interface HomeIntroProps {
  isMember: boolean // 로그인한 사용자면 true. 설명·3단계 안내를 빼고 제목을 작게 둔다
  memberSlot?: ReactNode // 로그인한 사용자에게 제목 바로 아래 보일 내용(내 플랜). 제목(h1)이 먼저 오도록 이 안에 넣는다
}

/**
 * 홈 맨 위(첫 화면)다. 한 줄 가치 제안(h1) · 짧은 설명 · 3단계 안내와 시작 버튼 순서다.
 * 검색창은 모든 화면 공통으로 머리글에 있다(홈 1차안 피드백: 본문의 큰 검색창을 없애고 머리글로 통일).
 *
 * 정보 위계(3초 안에 "이게 뭐고 무엇을 하면 되는지"):
 * 1. h1이 서비스가 하는 일을 한 문장으로 말한다("어느 달에 어디서" = 구독 순서 계산).
 * 2. 바로 아래 설명이 범위(국내 OTT 7곳·무료·비영리)를 말한다.
 * 3. 3단계 카드가 할 일을 순서대로 보이고, 시작 버튼(로그인)이 그 옆(모바일은 아래)에 있다.
 * 4. 시작 버튼 바로 아래 한 줄로 "로그인 없이도 위 검색창에서 찾을 수 있다"는 것을 알린다(비로그인에게만, 한 곳에만 둔다).
 * 로그인한 사용자(반복 사용자)는 1~4를 이미 알고 있어 제목만 작게 남기고, 그 아래에 내 플랜(memberSlot)을 바로 보인다.
 * 내 플랜을 제목 아래에 넣는 이유: 화면의 첫 제목(h1)보다 소제목(h2)이 먼저 나오면 낭독기의 제목 목록 순서가 어색해진다.
 *
 * 문구 수위: "계산해 보여 드려요"처럼 계산 결과를 제안으로 말하고 "가장 싸게", "보장" 같은 단정은 쓰지 않는다(frontend.md 표현 수위).
 */
export function HomeIntro({ isMember, memberSlot }: HomeIntroProps) {
  return (
    <section aria-labelledby="home-title" className="grid gap-6">
      <div className="grid gap-2">
        <h1
          id="home-title"
          className={cn('font-bold tracking-tight', isMember ? 'text-xl' : 'text-2xl sm:text-3xl')}
        >
          찜한 작품, 어느 달에 어디서 볼지 계산해 드려요
        </h1>
        {!isMember && (
          <p className="max-w-2xl text-muted-foreground">
            국내 OTT 7곳(넷플릭스·디즈니+·애플 TV+·티빙·웨이브·왓챠·쿠팡플레이)의 제공 정보와 내 월
            예산·시청 시간으로 3개월 구독 순서를 짜 주는 무료 비영리 서비스예요.
          </p>
        )}
      </div>

      {isMember ? memberSlot : <StartSteps />}
      {/* 이전(1차안 첫 판): 여기에 본문 큰 검색창(TitleSearchForm variant="hero")과 비로그인 안내가 있었다. 검색창은 머리글로 옮겼다 */}
    </section>
  )
}

// 3단계 안내 카드와 시작 버튼
function StartSteps() {
  return (
    <div className="grid gap-4 rounded-xl border border-border bg-card p-4 sm:p-5">
      <h2 className="text-base font-bold">이렇게 써요</h2>
      {/* <ol>: 단계에 순서가 있어 낭독기가 "목록, 항목 3개"와 순서를 함께 읽는다. 넓은 화면에서는 세 칸, 모바일은 한 칸 */}
      <ol className="grid gap-3 sm:grid-cols-3 sm:gap-4">
        {START_STEPS.map((step, index) => (
          <li key={step.title} className="flex gap-3">
            {/* 단계 번호는 원 안의 숫자 글자다(아이콘만 두면 순서가 색·모양으로만 보인다) */}
            <span className="grid size-8 shrink-0 place-items-center rounded-full bg-primary text-sm font-bold text-primary-foreground tabular-nums">
              {index + 1}
            </span>
            <div className="grid gap-0.5">
              <p className="flex items-center gap-1.5 font-semibold">
                <step.icon aria-hidden="true" className="size-4 text-primary" />
                {step.title}
              </p>
              <p className="text-sm text-muted-foreground">{step.description}</p>
            </div>
          </li>
        ))}
      </ol>
      <div className="flex flex-wrap items-center gap-2">
        {/* 로그인 뒤 내 설정(2단계)으로 이어지게 from을 넘긴다. 찜은 회원 기능이라 시작은 로그인이다 */}
        <Button asChild size="lg" className="h-11 px-5">
          <Link to="/login" state={{ from: '/settings' }}>
            무료로 플랜 계산 시작하기
          </Link>
        </Button>
        <Button asChild variant="outline" size="lg" className="h-11 border-input bg-transparent">
          <Link to="/ranking">먼저 인기 작품 둘러보기</Link>
        </Button>
      </div>
      {/* 비로그인에게만 보이는 안내(이 카드는 비로그인에게만 그려진다). 회원·관리자에게는 필요 없는 말이라 홈 어디에도 두지 않는다 */}
      <p className="text-sm text-muted-foreground">
        로그인하지 않아도 화면 위 검색창에서 작품을 찾고 어디서 볼 수 있는지 확인할 수 있어요.
      </p>
    </div>
  )
}
