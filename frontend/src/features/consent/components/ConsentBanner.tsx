import { useId } from 'react'
import { Link } from 'react-router'
import { ChartColumnIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'

interface ConsentBannerProps {
  onAccept: () => void // [동의]를 눌렀을 때. 동의 저장과 GA 불러오기는 부르는 쪽(frontend-dev)이 한다
  onReject: () => void // [거부]를 눌렀을 때. 거부 저장(6개월 뒤 다시 묻기)은 부르는 쪽이 한다
}

/*
 * 두 버튼이 함께 쓰는 모양. 거부와 동의를 "같은 크기·같은 색·같은 굵기"로 맞춘다.
 * 왜 같게 하나: 동의 버튼만 강조색으로 칠하고 거부를 흐린 글자로 두면 동의 쪽으로 손이 가게 만드는 다크 패턴이 된다.
 * 사용자 결정(동의 항목은 방문 통계 하나, 거부해도 이용 제한 없음)과도 맞지 않는다.
 * - 높이 h-11(44px): WCAG 2.2 터치 대상 최소(2.5.8, 24px)를 넉넉히 넘고, 모바일 엄지로 누르기 편한 크기다
 * - 모양(밝은 띠로 바꾼 뒤, 사용자 피드백 2026-10-31): 두 버튼 모두 "바탕 없음 + 짙은 남색 2px 테두리 + 짙은 남색 글자"다.
 *   하늘색 띠(--primary 0.79) 위 짙은 남색(--primary-foreground 0.15)은 대비 약 9:1로 본문 기준 4.5:1을 넘고,
 *   테두리도 3:1(WCAG 1.4.11 비텍스트 대비)을 넘어 버튼 경계가 보인다
 * - 포커스 표시: 원본 링 색(--ring)은 띠 바탕(--primary)과 같은 하늘색이라 띠 위에서는 보이지 않는다.
 *   그래서 이 띠 안에서만 링 색을 짙은 남색으로 바꾼다
 * - ghost 변형을 바탕으로 쓰는 이유: outline 변형은 dark: 변형(이 앱은 항상 켜져 있다, index.css의 @custom-variant dark)에서
 *   어두운 바탕(bg-input/30)을 깔아 밝은 띠 위에서 버튼이 검은 덩어리로 보인다. ghost는 바탕을 칠하지 않아 덮어쓸 것이 적다
 * 이전 값(어두운 카드 띠일 때): variant="outline" + 'h-11 min-w-24 flex-1 text-sm font-semibold sm:flex-none'
 */
const CHOICE_BUTTON_CLASS =
  'h-11 min-w-24 flex-1 border-2 border-primary-foreground bg-transparent text-sm font-semibold text-primary-foreground hover:bg-primary-foreground/10 hover:text-primary-foreground focus-visible:border-primary-foreground focus-visible:ring-primary-foreground/60 sm:flex-none dark:hover:bg-primary-foreground/10'

/**
 * 방문 통계(Google Analytics) 수집 동의를 묻는 화면 하단 안내 띠다(표현만 한다).
 *
 * 상태·저장·GA 연동은 이 컴포넌트가 하지 않는다. 언제 보일지(아직 고르지 않았거나 거부 후 6개월이 지났을 때)는
 * 부르는 쪽이 정하고, 이 컴포넌트는 버튼이 눌렸다는 사실만 onAccept·onReject로 알린다.
 *
 * 배치 방식: position: sticky + bottom: 0.
 * - fixed로 띄우면 페이지 맨 아래(푸터의 출처 표기·처리방침 링크)가 띠 뒤에 가려져, 띠 높이만큼 아래 여백을 따로 계산해야 한다
 * - sticky는 스크롤하는 동안 화면 아래에 붙어 있다가, 페이지 끝에 닿으면 원래 자리(푸터 다음)에 놓인다.
 *   그래서 푸터가 가려지지 않고 여백 계산도 필요 없다
 * - 조건: RootLayout의 바깥 세로 flex 상자(min-h-screen)의 "마지막 자식"으로 둬야 이렇게 동작한다(부모가 화면 전체 높이를 차지해야 한다)
 *
 * 접근성 판단:
 * - role="dialog"가 아니라 이름 붙은 region(section + aria-labelledby)으로 둔다. 화면을 막지 않고(모달 아님),
 *   고르지 않아도 서비스를 계속 쓸 수 있으므로 포커스를 가두거나 처음에 강제로 옮기지 않는다.
 *   화면 낭독기 사용자는 랜드마크 목록에서 "방문 통계 수집 동의"로 찾아갈 수 있다
 * - 버튼은 실제 <button>이라 Tab·Enter·Space로 누를 수 있다. 버튼 순서는 화면 순서와 같다(거부 → 동의)
 * - 두 선택지는 색이 아니라 글자(거부/동의)로 구분된다
 *
 * 색(사용자 피드백 2026-10-31: 어두운 카드색이라 푸터·하단바처럼 보여 눈에 띄지 않았다):
 * - 띠 바탕을 강조색 하늘(--primary)로 칠하고 글자는 짙은 남색(--primary-foreground)으로 둔다. 어두운 화면 전체에서 유일하게 밝은 면이라
 *   푸터(바탕색 + 회색 글자)와 바로 구분된다. 대비는 약 9:1이다
 * - 위쪽에 짙은 남색 4px 테두리를 둬 화면과 띠의 경계를 또렷하게 하고, 왼쪽에 반전 원(남색 바탕 + 하늘색 차트 아이콘)을 둬
 *   "알려 드리는 안내"라는 성격을 색이 아닌 모양으로도 알린다(아이콘은 장식이라 낭독기에는 숨긴다)
 * - 새 토큰은 만들지 않았다. --primary와 --primary-foreground는 원래 "하늘 바탕 위 남색 글자" 짝으로 정해진 토큰이다
 * 이전 값: 'border-t bg-card text-card-foreground shadow-lg', 본문 글자 text-muted-foreground
 *
 * 모바일(375px) 높이: 위아래 여백 + 제목 1줄 + 본문 3~4줄 + 버튼 1줄로 약 190px(화면 812px의 약 23%)이다.
 * 글자 크게 보기 등으로 띠가 커져도 화면 절반을 넘지 않게 max-h-[50dvh]로 막고, 넘치면 띠 안에서 스크롤된다.
 */
export function ConsentBanner({ onAccept, onReject }: ConsentBannerProps) {
  // 제목 id. region의 이름(aria-labelledby)으로 쓴다. useId는 한 화면에 띠가 둘 그려져도 id가 겹치지 않게 한다
  const titleId = useId()
  const descriptionId = useId()

  return (
    <section
      aria-labelledby={titleId}
      aria-describedby={descriptionId}
      className="sticky bottom-0 z-40 max-h-[50dvh] overflow-y-auto border-t-4 border-primary-foreground bg-primary text-primary-foreground shadow-lg"
    >
      {/*
        안쪽 폭은 머리글·본문과 같은 max-w-5xl + px-4로 맞춰 띠의 글자가 본문과 같은 세로선에서 시작한다.
        아래 여백은 iPhone 홈 막대(safe-area)만큼 더 띄워 버튼이 막대에 붙지 않게 한다(max로 최소 12px 보장).
        넓은 화면(sm 이상)에서는 글과 버튼을 한 줄에 놓아 띠 높이를 줄인다.
      */}
      <div className="mx-auto flex max-w-5xl flex-col gap-3 px-4 pt-3 pb-[max(0.75rem,env(safe-area-inset-bottom))] sm:flex-row sm:items-center sm:gap-6 sm:py-4">
        {/* 아이콘 원 + 글. 아이콘 원은 맨 위 줄(제목)에 맞춰 위쪽 정렬한다 */}
        <div className="flex min-w-0 items-start gap-3">
          {/* 반전 원(남색 바탕 + 하늘색 아이콘): 밝은 띠 위에서 가장 진한 점이라 시선이 먼저 간다. 장식이라 낭독기에는 숨긴다 */}
          <span
            aria-hidden="true"
            className="mt-0.5 inline-flex size-9 shrink-0 items-center justify-center rounded-full bg-primary-foreground text-primary"
          >
            <ChartColumnIcon strokeWidth={2.5} className="size-5" />
          </span>
          <div className="grid min-w-0 gap-1 break-keep">
            <h2 id={titleId} className="text-base font-bold">
              방문 통계 수집 동의
            </h2>
            {/*
              본문 글자도 제목과 같은 짙은 남색이다(하늘 바탕 위 약 9:1). 회색(muted) 글자는 밝은 바탕에서 대비가 모자라 쓰지 않는다.
              제목은 굵기(bold)와 크기로 본문과 구분한다
            */}
            <p id={descriptionId} className="text-sm leading-6">
              방문자 수와 이용 현황을 파악하려고 Google Analytics를 쓰며, 동의하시면 쿠키가
              저장됩니다. 거부해도 서비스 이용에 제한이 없고, 광고에는 쓰지 않습니다.{' '}
              {/*
              처리방침 링크: 띠 바탕이 강조색(하늘)이라 강조색 글자는 보이지 않는다. 그래서 같은 남색 글자에 굵기 + 밑줄로 링크임을 알린다.
              포커스 링도 하늘색 대신 남색이다. 이전: 'text-primary underline ... focus-visible:ring-ring/50'
            */}
              <Link
                to="/privacy"
                className="rounded-sm font-semibold underline underline-offset-3 focus-visible:ring-3 focus-visible:ring-primary-foreground/60 focus-visible:outline-none"
              >
                개인정보처리방침
              </Link>
            </p>
          </div>
        </div>

        {/* 버튼 묶음. 모바일은 두 버튼이 가로로 반씩 나눠 같은 폭, 넓은 화면은 오른쪽에 같은 폭으로 붙는다 */}
        <div className="flex shrink-0 gap-2">
          <Button type="button" variant="ghost" className={CHOICE_BUTTON_CLASS} onClick={onReject}>
            거부
          </Button>
          <Button type="button" variant="ghost" className={CHOICE_BUTTON_CLASS} onClick={onAccept}>
            동의
          </Button>
        </div>
      </div>
    </section>
  )
}
