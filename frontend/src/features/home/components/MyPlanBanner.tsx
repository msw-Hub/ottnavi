import { CalendarRangeIcon, ChevronRightIcon } from 'lucide-react'
import { Link } from 'react-router'
import { Button } from '@/components/ui/button'
import type { HomePlanSummary } from '@/features/home/mockTypes'
import { formatDate, formatWon } from '@/lib/format'

interface MyPlanBannerProps {
  summary: HomePlanSummary | null // 저장한 현재 플랜 요약. 없으면 null → 계산 시작 안내
}

/**
 * 로그인한 사용자에게 홈 맨 위에 보이는 "내 플랜" 자리다(반복 사용자가 홈에서 바로 플랜으로 가는 길).
 *
 * - 플랜이 있으면: 이번 달 구독할 서비스와 금액을 한 줄로, 꼭 볼 작품을 몇 편 볼 수 있다고 계산됐는지와 계산일을 둘째 줄로 보이고 "플랜 보기"로 보낸다.
 * - 플랜이 없으면: 계산을 시작하라는 안내와 "플랜 계산하기"·"찜 목록" 버튼을 보인다.
 * 금액은 lib/format의 formatWon으로만 쓴다(frontend.md 숫자 표시). 결과는 "계산됐어요"처럼 계산 결과로 말하고 확정처럼 쓰지 않는다.
 * 1차안은 정적 샘플이라 불러오는 중·오류 모양은 없다. API로 바꿀 때 이 자리 높이를 유지하는 회색 칸(불러오는 중)을 함께 둔다.
 */
export function MyPlanBanner({ summary }: MyPlanBannerProps) {
  return (
    <section
      aria-labelledby="home-my-plan"
      className="grid gap-3 rounded-xl border border-border bg-card p-4 sm:flex sm:items-center sm:justify-between sm:gap-6 sm:p-5"
    >
      <div className="grid min-w-0 gap-1">
        <h2
          id="home-my-plan"
          className="flex items-center gap-1.5 text-sm font-semibold text-muted-foreground"
        >
          <CalendarRangeIcon aria-hidden="true" className="size-4" />내 플랜
          {summary && <span className="font-normal">· {summary.planTypeLabel}</span>}
        </h2>
        {summary ? (
          <>
            <p className="text-lg font-bold">
              {summary.monthLabel}에는 {summary.serviceNames.join(' + ')} 구독{' '}
              <span className="tabular-nums">· {formatWon(summary.monthlyCost)}</span>
            </p>
            <p className="text-sm text-muted-foreground tabular-nums">
              꼭 볼 작품 {summary.mustWatchTotal}편 중 {summary.mustWatchDone}편을 3개월 안에 볼 수
              있다고 계산됐어요 · 계산일 {formatDate(summary.calculatedAt) ?? '정보 없음'}
            </p>
          </>
        ) : (
          <>
            <p className="text-lg font-bold">아직 저장한 플랜이 없어요</p>
            <p className="text-sm text-muted-foreground">
              찜한 작품과 월 예산·시청 시간으로 3개월 구독 순서를 계산해 보세요.
            </p>
          </>
        )}
      </div>
      <div className="flex shrink-0 flex-wrap gap-2">
        {summary ? (
          <Button asChild size="lg" className="h-11 px-5">
            <Link to="/plan">
              플랜 보기
              <ChevronRightIcon aria-hidden="true" />
            </Link>
          </Button>
        ) : (
          <>
            <Button asChild size="lg" className="h-11 px-5">
              <Link to="/plan">플랜 계산하기</Link>
            </Button>
            <Button
              asChild
              variant="outline"
              size="lg"
              className="h-11 border-input bg-transparent"
            >
              <Link to="/wishlist">찜 목록</Link>
            </Button>
          </>
        )}
      </div>
    </section>
  )
}
