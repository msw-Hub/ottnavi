import type { ReactNode } from 'react'
import { CalculatorIcon, HeartIcon, SlidersHorizontalIcon, type LucideIcon } from 'lucide-react'
import { Link } from 'react-router'
import { Button } from '@/components/ui/button'
import { getTmdbImageUrl } from '@/lib/tmdbImage'
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
  // 비로그인 배너 배경(포스터 콜라주)에 깔 TMDB 포스터 상대 경로들. 인기 순위(오늘·전체) 순서 그대로다.
  // null은 포스터가 없는 작품이라 건너뛴다. 받는 중·실패면 빈 배열이고, 그때는 모든 칸을 토큰 색 칸으로 채운다
  heroPosterPaths?: (string | null)[]
}

/*
 * 배너 포스터 콜라주 설정(2026-10-27 사용자 결정: 시안 A "포스터 콜라주 히어로", 모바일은 줄여서).
 * 성능: 실제 이미지는 최대 HERO_POSTER_MAX장(w342, 장당 약 20~40KB)만 쓴다. 인기 순위 줄의 2위 아래 카드도 w342라
 *   같은 주소는 브라우저가 한 번만 받는다. 1위는 줄에서 큰 카드(w500)라 배너용 w342 한 장이 따로 나간다.
 * 칸 수(HERO_TILE_COUNT)가 이미지 수보다 많은 이유: 남는 칸은 토큰 색 그라디언트로 채워 이미지 요청 없이 "모아 놓은" 느낌만 낸다.
 * 모바일(640px 미만)은 첫 줄 HERO_MOBILE_TILE_COUNT칸만 보이고 나머지는 숨긴다(display:none + loading="lazy"라 요청도 나가지 않는다).
 */
const HERO_TILE_COUNT = 15 // 넓은 화면 콜라주 칸 수(5열 × 3줄)
const HERO_COLUMNS = 5 // 콜라주 열 수(열마다 높이를 어긋나게 하는 데 쓴다)
const HERO_MOBILE_TILE_COUNT = 5 // 모바일에서 보이는 칸 수(첫 줄)
const HERO_POSTER_MAX = 8 // 실제 포스터 이미지 상한

/**
 * 홈 맨 위(첫 화면)다. 비로그인은 포스터 콜라주가 깔린 둥근 배너 패널(HeroBanner, 시안 A) 안에
 * [보조 라벨(넓은 화면만)] · 한 줄 가치 제안(h1) · 짧은 설명 · 시작 버튼 2개 순서로 둔다.
 * B안(2026-10-27 사용자 결정): 첫 화면에서 인기 순위 포스터가 바로 보이도록 이 묶음을 압축했고, 3단계 안내(StartSteps)는
 * 인기 순위 줄 아래로 옮겼다(HomeScreen이 그린다).
 * 검색창은 모든 화면 공통으로 머리글에 있다(홈 1차안 피드백: 본문의 큰 검색창을 없애고 머리글로 통일).
 *
 * 정보 위계(3초 안에 "이게 뭐고 무엇을 하면 되는지"):
 * 1. 배너 안 h1이 서비스가 하는 일을 한 문장으로 말한다("어느 달에 어디서" = 구독 순서 계산).
 * 2. 바로 아래 설명이 범위(국내 OTT 7곳·무료·비영리)를 말한다.
 * 3. 설명 아래 시작 버튼 2개(로그인 / 인기 작품 둘러보기)가 배너 안에 있다. 할 일을 순서대로 보이는 3단계 카드는 인기 순위 줄 아래에 있다.
 * 4. "로그인 없이도 위 검색창에서 찾을 수 있다"는 한 줄은 3단계 카드 맨 아래에 둔다(비로그인에게만, 한 곳에만 둔다).
 * 이전(B안 전체 폭 띠): 1~4가 머리글 아래 전체 폭 띠 안에 있었고 3은 "시작 버튼이 제목 옆", 4는 "시작 버튼 바로 아래 한 줄"이었다.
 * 로그인한 사용자(반복 사용자)는 1~4를 이미 알고 있어 배너 없이 제목만 작게 남기고, 그 아래에 내 플랜(memberSlot)을 바로 보인다.
 * 내 플랜을 제목 아래에 넣는 이유: 화면의 첫 제목(h1)보다 소제목(h2)이 먼저 나오면 낭독기의 제목 목록 순서가 어색해진다.
 *
 * 문구 수위: "계산해 보여 드려요"처럼 계산 결과를 제안으로 말하고 "가장 싸게", "보장" 같은 단정은 쓰지 않는다(frontend.md 표현 수위).
 */
export function HomeIntro({ isMember, memberSlot, heroPosterPaths = [] }: HomeIntroProps) {
  // 1. 로그인한 사용자: 작은 제목 + 내 플랜. 배너를 두지 않는다(이전 모양 그대로)
  if (isMember) {
    return (
      <section aria-labelledby="home-title" className="grid gap-4">
        <h1 id="home-title" className="text-xl font-bold tracking-tight">
          찜한 작품, 어느 달에 어디서 볼지 계산해 드려요
        </h1>
        {memberSlot}
      </section>
    )
  }

  // 2. 처음 온 사람: 포스터 콜라주 배너(시안 A)
  return <HeroBanner posterPaths={heroPosterPaths} />
}

/**
 * 비로그인 홈 맨 위 배너(시안 A "포스터 콜라주 히어로", 2026-10-27 사용자 결정).
 * 바꾼 이유(사용자 피드백): 이전 소개는 머리글과 같은 남청색 전체 폭 띠라 "X로 닫는 공지 배너"처럼 보였다.
 *
 * 머리글과 구분하는 장치(색만으로 나누지 않는다):
 * 1. 전체 폭 띠가 아니라 본문 폭 안의 둥근 패널(rounded-2xl)이고, 머리글과의 사이에 main 위 여백(32px)이 있다.
 * 2. 바탕이 단색이 아니라 포스터 사진 + 그라디언트라 머리글(단색 card)과 재질이 다르다.
 * 3. 테두리(border 토큰) 1px로 면의 끝이 보인다.
 *
 * 글자 대비: 글자는 덮개가 background 토큰 100%(넓은 화면 왼쪽 40%)이거나 88% 이상인 곳에만 놓는다.
 * background(밝기 0.125) 위 foreground(0.96)는 약 18:1, muted-foreground(0.77)는 약 9:1이다. 덮개가 88%여도 아래 포스터가
 * 아무리 밝아도 바탕 밝기가 크게 오르지 않아 4.5:1을 넉넉히 넘는다(추정 계산, 실제 포스터 위 실측은 하지 않았다).
 *
 * 모바일(640px 미만)은 시안보다 줄였다(사용자 요청 "모바일은 줄여서"): 콜라주는 첫 줄 5칸만 위쪽 띠로 보이고,
 * 보조 라벨을 빼고, 제목·여백을 줄이고, 시작 버튼 두 개를 한 줄 두 칸으로 둔다. 첫 화면에 인기 순위 1위 포스터가 더 보이게 하려는 것이다.
 */
function HeroBanner({ posterPaths }: { posterPaths: (string | null)[] }) {
  // 1. 쓸 포스터 주소: 경로가 없거나(null) 올바르지 않은 값은 건너뛰고, 상한(HERO_POSTER_MAX)까지만 쓴다
  const posterUrls = posterPaths
    .map((path) => getTmdbImageUrl(path, 'w342'))
    .filter((url): url is string => url !== null)
    .slice(0, HERO_POSTER_MAX)

  return (
    // isolate: 콜라주(-z-20)·덮개(-z-10)를 이 패널 안에서만 뒤로 보낸다(패널 밖 바탕 아래로 숨지 않게 새 쌓임 맥락을 만든다)
    // group: 마우스를 올렸을 때 콜라주가 살짝 뜨는 효과(아래 motion-safe)에 쓴다
    <section
      aria-labelledby="home-title"
      className="group relative isolate overflow-hidden rounded-2xl border border-border bg-background"
    >
      {/* 2. 포스터 콜라주(꾸밈이라 낭독기에서 숨긴다). 넓은 화면은 오른쪽에 비스듬히 세 줄, 모바일은 위쪽에 한 줄 */}
      <div
        aria-hidden="true"
        className={cn(
          'absolute -z-20 grid grid-cols-5',
          // 모바일: 패널 위쪽 밖에서 시작해 첫 줄 아래쪽만 띠처럼 보인다. 좌우를 넓혀 기울여도 모서리 빈틈이 안 보이게 한다
          '-top-12 -right-10 -left-10 -rotate-6 gap-2',
          // 넓은 화면(시안 A): 오른쪽 64% 폭, -10도
          'sm:-top-[38%] sm:-right-[6%] sm:left-auto sm:w-[64%] sm:-rotate-10 sm:gap-3',
          // 움직임은 동작 줄이기를 끄지 않은 사람에게만(시안 A의 hover 들뜸)
          'motion-safe:transition-[translate] motion-safe:duration-700 motion-safe:group-hover:-translate-y-2',
        )}
      >
        {Array.from({ length: HERO_TILE_COUNT }, (_, index) => (
          <HeroTile
            // 칸은 위치가 고정된 자리(작품과 1:1이 아니다)라 자리 번호를 key로 쓴다
            key={`hero-slot-${index}`}
            index={index}
            posterUrl={posterUrls[index] ?? null}
          />
        ))}
      </div>

      {/* 3. 어두운 덮개. 넓은 화면: 왼쪽(글자 쪽) 40%까지 background 100% → 오른쪽으로 옅어진다.
          모바일: 위쪽 15%(375px에서 약 38px)까지만 포스터가 비치고, 그 아래(글자가 시작하는 48px부터)는 90% 이상 → 40%부터 100%다 */}
      <div
        aria-hidden="true"
        className={cn(
          'absolute inset-0 -z-10',
          'bg-linear-to-b from-background/20 via-background/90 via-15% to-background to-40%',
          // 넓은 화면: 글자 칸(최대 672px ≈ 패널의 65%)이 시안보다 넓어 짙은 구간을 늘렸다. 55%까지 100%, 70%에서 80%
          // (제목 끝이 60% 근처라 덮개 90% 이상 위에 있다). 시안 A: from-40% / via-background/82 via-56%
          'sm:bg-linear-to-r sm:from-background sm:from-55% sm:via-background/80 sm:via-70% sm:to-background/30 sm:to-100%',
        )}
      />
      {/* 넓은 화면의 아래쪽 그늘: 포스터 아래 끝이 패널 테두리에서 뚝 끊기지 않게 한다 */}
      <div
        aria-hidden="true"
        className="absolute inset-0 -z-10 hidden bg-linear-to-t from-background/60 to-transparent to-40% sm:block"
      />

      {/* 4. 글자 묶음. 모바일 pt-12(48px): 콜라주 띠가 보이는 높이다(56px에서 줄여 1위 포스터를 8px 더 보인다). 넓은 화면은 시안 A보다 위아래 여백을 8px씩 줄였다(48px → 40px) */}
      {/* 넓은 화면 글자 칸 max-w-2xl(672px): 제목(30px)이 한 줄에 들어가는 폭이다. 시안 A(max-w 600px, 36px 두 줄)보다 배너가 약 45px 낮다 */}
      <div className="grid max-w-2xl gap-2 px-4 pt-12 pb-4 sm:gap-3 sm:px-10 sm:py-10">
        {/* 보조 라벨: 넓은 화면에서만 보인다. 모바일은 한 줄(약 26px)을 아끼려고 뺐다(같은 말이 소개 문장에 있다) */}
        <p className="hidden text-sm font-bold tracking-wide text-primary sm:block">
          국내 OTT 7곳 · 3개월 구독 플랜
        </p>
        {/* 제목: 아래 인기 순위 제목(h2, text-lg sm:text-xl)보다 두 단계 이상 커서 "서비스 소개"와 "목록 제목"이 다른 층으로 읽힌다.
            모바일 text-xl(20px): 이전 24px보다 줄여 두 줄 높이를 아낀다. 넓은 화면 text-3xl(30px): 이전 B안과 같은 크기이고 한 줄에 들어간다(시안 A 36px은 두 줄) */}
        <h1
          id="home-title"
          className="text-xl leading-snug font-extrabold tracking-tight break-keep sm:text-3xl sm:leading-tight"
        >
          찜한 작품, 어느 달에 어디서 볼지 계산해 드려요
        </h1>
        {/* 처음 온 사람이 서비스를 이해하는 한 문장이라 반드시 남긴다(문구는 이전과 같다) */}
        <p className="text-sm text-muted-foreground break-keep sm:text-base">
          국내 OTT 7곳의 제공 정보와 내 월 예산·시청 시간으로 3개월 구독 순서를 짜 주는 무료 비영리
          서비스예요.
        </p>
        <div className="mt-1 sm:mt-2">
          <StartButtons />
        </div>
      </div>
    </section>
  )
}

/**
 * 콜라주 한 칸(세로 포스터 비율 2:3). 포스터가 있으면 이미지를, 없으면(모자람·받는 중·실패) 토큰 색 그라디언트만 보인다.
 * 이미지 뒤에도 같은 그라디언트가 깔려 있어, 이미지가 늦게 오거나 깨져도(alt=""라 깨진 그림 표시가 없다) 빈칸이 아니라 색 칸으로 보인다.
 */
function HeroTile({ index, posterUrl }: { index: number; posterUrl: string | null }) {
  const column = index % HERO_COLUMNS
  const isMobileVisible = index < HERO_MOBILE_TILE_COUNT
  return (
    <div
      className={cn(
        'aspect-[2/3] overflow-hidden rounded-md bg-card bg-linear-160 to-transparent sm:rounded-lg',
        // 칸마다 그라디언트 색을 번갈아 쓴다(primary 하늘색 / priority-must-bg 금빛, 둘 다 기존 토큰. 로고 날개 두 색과 같다)
        index % 3 === 1 ? 'from-priority-must-bg/40' : 'from-primary/45',
        // 2·4번째 열을 아래로 내려 격자보다 "모아 놓은" 느낌을 준다
        (column === 1 || column === 3) && 'mt-6 sm:mt-11',
        // 모바일은 첫 줄만 보인다
        !isMobileVisible && 'hidden sm:block',
      )}
    >
      {posterUrl && (
        <img
          src={posterUrl}
          alt=""
          aria-hidden="true"
          decoding="async"
          // 모바일에서도 보이는 첫 줄은 바로 받고, 나머지는 lazy라 모바일(숨김)에서는 요청이 나가지 않는다
          loading={isMobileVisible ? 'eager' : 'lazy'}
          className="size-full object-cover"
        />
      )}
    </div>
  )
}

// 이전(B안 전체 폭 띠, 2026-10-27 시안 A로 교체): 회원·비회원을 한 컴포넌트에서 나눠 그렸다
// export function HomeIntro({ isMember, memberSlot }: HomeIntroProps) {
//   return (
//     // B안(첫 화면에 인기 순위 포스터가 바로 보이게): 제목 한 줄 + 소개 한 문장 + 시작 버튼 2개만 남기고 높이를 줄였다.
//     // 3단계 안내 상자(StartSteps)는 HomeScreen이 인기 순위 줄 아래에 따로 그린다.
//     // 넓은 화면(1024px 이상)에서는 글자 묶음 왼쪽 · 버튼 오른쪽 한 줄로 두어 세로 높이를 한 번 더 아낀다
//     // 구간 구분(B안 후속, 사용자 피드백 "소개와 인기 순위가 한 묶음처럼 보인다"):
//     // 비로그인의 소개 구간을 화면 전체 폭의 띠로 만든다. 바탕은 card 토큰(바탕보다 한 단계 밝은 남청색)이고 아래에 1px 구분선(border 토큰)이 있다.
//     // 색 차이가 작아도 "선 + 띠의 끝 + 여백"이라는 형태로 경계가 보인다(색만으로 나누지 않음). 모서리·테두리가 없는 전체 폭이라
//     // 둥근 테두리 상자인 3단계 안내(StartSteps)와 모양이 겹치지 않는다.
//     // 전체 폭으로 늘리는 방법: main은 max-w-5xl이라 요소 자체를 넓힐 수 없다. 대신 같은 색 그림자(box-shadow)를 좌우로 100vmax 퍼뜨리고
//     // clip-path로 위아래는 자른다. 그림자는 스크롤 영역에 포함되지 않아 w-screen과 달리 가로 스크롤이 생기지 않는다.
//     // 두 번째 그림자(아래로 1px 내린 border 색)가 전체 폭 구분선이 된다. 아래 clip을 -1px로 둬 그 1px만 보이게 한다.
//     // -mt-8 pt-8: main의 위쪽 여백(RootLayout py-8)까지 띠가 올라가 머리글 바로 밑부터 이어지게 한다(글자 위치는 그대로).
//     // 회원(isMember)은 제목이 작고 내 플랜 카드가 이어져 띠를 두지 않는다(이전 모양 그대로).
//     // 이전: <section aria-labelledby="home-title" className="grid gap-4">
//     <section
//       aria-labelledby="home-title"
//       className={cn(
//         'grid gap-4',
//         !isMember &&
//           '-mt-8 bg-card pt-8 pb-6 shadow-[0_0_0_100vmax_var(--color-card),0_1px_0_100vmax_var(--color-border)] [clip-path:inset(0_-100vmax_-1px)]',
//       )}
//     >
//       <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between lg:gap-8">
//         <div className="grid min-w-0 gap-1.5">
//           {/* 비로그인 제목은 다시 한 단계 키우고 더 굵게 쓴다(text-2xl sm:text-3xl, font-extrabold). 아래 인기 순위 제목(h2, text-lg sm:text-xl, font-bold)과
//               크기 단계가 두 칸 벌어져 "서비스 소개"와 "목록 제목"이 다른 층으로 읽힌다.
//               이전(B안): 'text-xl sm:text-2xl' + font-bold — h2와 4px 차이라 같은 층의 제목처럼 보였다 */}
//           <h1
//             id="home-title"
//             className={cn(
//               'tracking-tight',
//               isMember ? 'text-xl font-bold' : 'text-2xl font-extrabold sm:text-3xl',
//             )}
//           >
//             찜한 작품, 어느 달에 어디서 볼지 계산해 드려요
//           </h1>
//           {!isMember && (
//             // 처음 온 사람이 서비스를 이해하는 한 문장이라 반드시 남긴다. 7곳 이름은 바로 아래 서비스 칩에 다 보여 문장에서는 뺐다
//             // 1024px 이상은 버튼이 오른쪽에 있어 글자 칸이 약 610px이다. 16px이면 두 줄로 꺾여 24px을 더 써서, 그 폭에서만 14px로 한 줄에 둔다
//             // (제목을 키운 만큼 첫 화면의 1위 카드가 밀리지 않게 하려는 것이다). 이전(B안): 'max-w-2xl text-sm text-muted-foreground sm:text-base'
//             <p className="max-w-2xl text-sm text-muted-foreground sm:text-base lg:text-sm">
//               국내 OTT 7곳의 제공 정보와 내 월 예산·시청 시간으로 3개월 구독 순서를 짜 주는 무료
//               비영리 서비스예요.
//             </p>
//           )}
//           {/* 이전 소개 문장(7곳 이름을 모두 나열해 모바일에서 4줄이었다):
//           <p className="max-w-2xl text-muted-foreground">
//             국내 OTT 7곳(넷플릭스·디즈니+·애플 TV+·티빙·웨이브·왓챠·쿠팡플레이)의 제공 정보와 내 월
//             예산·시청 시간으로 3개월 구독 순서를 짜 주는 무료 비영리 서비스예요.
//           </p> */}
//         </div>
//         {!isMember && <StartButtons />}
//       </div>
//
//       {isMember && memberSlot}
//       {/* 이전: {isMember ? memberSlot : <StartSteps />} — 3단계 상자가 제목 바로 아래에 있어 첫 화면을 거의 다 차지했다 */}
//       {/* 이전(1차안 첫 판): 여기에 본문 큰 검색창(TitleSearchForm variant="hero")과 비로그인 안내가 있었다. 검색창은 머리글로 옮겼다 */}
//     </section>
//   )
// }

// 시작 버튼 두 개(비로그인에게만). 3단계 상자 안에 있던 것을 제목 옆으로 옮겼다(B안). 높이 h-11(44px)은 터치 크기 기준이다.
// 모바일은 첫 버튼의 좌우 여백을 px-4로 줄여 375px 화면에서 두 버튼이 한 줄에 들어가게 한다(Playwright 실측: px-5면 2px 넘쳐 두 줄이 되어 약 52px을 더 차지했다).
// 글자가 더 넓은 대체 글꼴이면 flex-wrap으로 자연스럽게 두 줄로 내려간다
//
// 시안 A 배너(2026-10-27) 안에서의 배치:
// - 모바일(640px 미만): 패널 안 글자 칸이 375px 화면에서 약 309px라 두 버튼(합쳐 약 340px)이 한 줄에 들어가지 않는다.
//   두 줄로 쌓으면 52px이 늘어 첫 화면의 인기 순위 1위 포스터가 그만큼 밀린다. 그래서 한 줄을 두 칸(grid-cols-2)으로 나누고
//   칸 안에서 글자가 두 줄로 꺾이게 했다(min-h-11 = 44px 터치 크기 유지, 두 줄일 때 약 52px). 문구는 바꾸지 않았다.
// - 넓은 화면: 이전처럼 글자 길이만큼의 버튼 두 개를 한 줄에 둔다.
// - 보조 버튼 바탕: 포스터 위에 놓일 수 있어 투명 대신 background 토큰 70%를 깐다(글자 대비를 사진과 무관하게 유지).
// 이전(B안 띠 안): <div className="flex shrink-0 flex-wrap items-center gap-2">, 버튼 className "h-11 px-4 sm:px-5" / "h-11 border-input bg-transparent"
function StartButtons() {
  return (
    <div className="grid grid-cols-2 gap-2 sm:flex sm:flex-wrap sm:items-center">
      {/* 로그인 뒤 내 설정(2단계)으로 이어지게 from을 넘긴다. 찜은 회원 기능이라 시작은 로그인이다 */}
      <Button
        asChild
        size="lg"
        className="h-auto min-h-11 px-3 py-1.5 text-center leading-snug whitespace-normal sm:h-11 sm:px-5 sm:whitespace-nowrap"
      >
        <Link to="/login" state={{ from: '/settings' }}>
          무료로 플랜 계산 시작하기
        </Link>
      </Button>
      <Button
        asChild
        variant="outline"
        size="lg"
        className="h-auto min-h-11 border-input bg-background/70 px-3 py-1.5 text-center leading-snug whitespace-normal sm:h-11 sm:px-5 sm:whitespace-nowrap"
      >
        <Link to="/ranking">먼저 인기 작품 둘러보기</Link>
      </Button>
    </div>
  )
}

// 3단계 안내 카드(비로그인에게만). B안에서 인기 순위 줄 아래로 옮겼다(HomeScreen이 그린다). 시작 버튼은 제목 옆(StartButtons)으로 옮겼다
export function StartSteps() {
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
      {/* 이전: 시작 버튼 두 개가 여기 있었다. B안에서 제목 옆(StartButtons)으로 옮겼다(같은 링크가 두 번 나오지 않게 여기서는 뺐다)
      <div className="flex flex-wrap items-center gap-2">
        <Button asChild size="lg" className="h-11 px-5">
          <Link to="/login" state={{ from: '/settings' }}>
            무료로 플랜 계산 시작하기
          </Link>
        </Button>
        <Button asChild variant="outline" size="lg" className="h-11 border-input bg-transparent">
          <Link to="/ranking">먼저 인기 작품 둘러보기</Link>
        </Button>
      </div> */}
      {/* 비로그인에게만 보이는 안내(이 카드는 비로그인에게만 그려진다). 회원·관리자에게는 필요 없는 말이라 홈 어디에도 두지 않는다 */}
      <p className="text-sm text-muted-foreground">
        로그인하지 않아도 화면 위 검색창에서 작품을 찾고 어디서 볼 수 있는지 확인할 수 있어요.
      </p>
    </div>
  )
}
