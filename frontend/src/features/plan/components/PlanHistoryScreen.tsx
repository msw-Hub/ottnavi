import type { ReactNode } from 'react'
import { ArrowLeftIcon, CircleCheckBigIcon, ListChecksIcon } from 'lucide-react'
import { Link, useSearchParams } from 'react-router'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { LoadingState } from '@/components/common/LoadingState'
import { SourceAttribution } from '@/components/common/SourceAttribution'
import { Button } from '@/components/ui/button'
import { PlanChangeEntry } from '@/features/plan/components/PlanChangeEntry'
import {
  HISTORY_SAMPLE_SERVICE_NAMES,
  PLAN_HISTORY_SAMPLE,
} from '@/features/plan/historySampleData'
import { isMockMode } from '@/lib/mockScenario'

// 목업 모드에서 상태 화면을 확인하는 주소 파라미터 이름과 값(?historyPreview=…). 1차안 검토용이다
const HISTORY_PREVIEW_PARAM = 'historyPreview'
type HistoryPreview = 'changes' | 'empty' | 'loading' | 'error'
const HISTORY_PREVIEWS: HistoryPreview[] = ['changes', 'empty', 'loading', 'error']

/**
 * SCR-11 플랜 변경 내역 화면의 본문이다(Task 068 1차안). pages/PlanHistoryPage는 이 컴포넌트를 연결만 한다(pages는 얇게, frontend.md).
 *
 * 이 화면이 하는 일: 매월 재계산으로 저장한 플랜이 바뀌었을 때 "무엇이, 왜 바뀌었는지"를 최신순으로 보여 준다.
 * 바뀌지 않은 달은 기록이 없고, 사용자가 직접 다시 계산해 저장한 것은 넣지 않는다(사용자 결정 3·4).
 *
 * 위에서 아래 순서와 이유(정보 위계):
 * 1. 제목·한 줄 설명: 이 기록이 언제 생기는지(매월 재계산, 바뀐 때만)를 먼저 알려 "왜 비어 있지?"를 막는다.
 * 2. "다 봤음" 안내 띠: 다 본 작품을 체크하지 않으면 다음 재계산에 다시 들어가 불필요한 구독이 추천될 수 있다.
 *    변경 내역을 보는 순간이 "계산이 내 상황과 어긋났다"를 느끼는 때라 여기서 알리고, 바로 찜 목록으로 갈 수 있게 한다.
 * 3. 변경 내역 목록(최신순, 항목마다 접고 펼침): 접힌 상태에서도 날짜·한 줄 요약·이유 칩이 보여 훑어 읽을 수 있다.
 * 4. 맨 아래 출처(JustWatch)와 "확인된 범위" 안내. 데이터 기준일은 기록마다 달라 각 항목 안에 둔다.
 *
 * 데이터: 1차안은 정적 샘플(historySampleData.ts)을 넘긴다. API가 생기면 이 파일에서 샘플 대신 조회 훅 결과를 넘기면 된다.
 * 상태 화면 확인(목업 모드 전용): 주소에 ?historyPreview=empty|loading|error를 붙이면 그 상태로 보인다(changes 또는 없음 = 기록 있음).
 * 운영에서는 이 파라미터를 읽지 않는다.
 */
export function PlanHistoryScreen() {
  const [searchParams, setSearchParams] = useSearchParams()

  // 1. 목업 모드에서만 상태 확인용 파라미터를 읽는다. 모르는 값이면 기록 있음으로 본다
  const rawPreview = isMockMode() ? searchParams.get(HISTORY_PREVIEW_PARAM) : null
  const preview: HistoryPreview = HISTORY_PREVIEWS.includes(rawPreview as HistoryPreview)
    ? (rawPreview as HistoryPreview)
    : 'changes'
  // 2. 기록 있음이면 샘플 3건, 빈 상태면 빈 목록
  const entries = preview === 'empty' ? [] : PLAN_HISTORY_SAMPLE

  // 3. 상태별 본문(로딩 → 오류 → 빈 상태 → 목록)
  let content: ReactNode
  if (preview === 'loading') {
    content = <LoadingState message="변경 내역을 불러오고 있어요" />
  } else if (preview === 'error') {
    content = (
      <ErrorState
        title="변경 내역을 불러오지 못했어요"
        message="잠시 후 다시 시도해 주세요."
        // 1차안: "다시 시도"는 확인용 파라미터를 지워 기록 있음 화면으로 돌아간다. 실제 화면은 조회 훅의 refetch를 넘긴다
        onRetry={() => {
          const next = new URLSearchParams(searchParams)
          next.delete(HISTORY_PREVIEW_PARAM)
          setSearchParams(next)
        }}
      />
    )
  } else if (entries.length === 0) {
    content = (
      <EmptyState
        title="아직 변경 내역이 없어요"
        description="매월 다시 계산한 결과가 저장한 플랜과 달라지면 여기에 기록돼요. 지금까지는 다시 계산된 적이 없거나 결과가 계속 같았어요."
        action={
          <Button asChild variant="outline" className="mt-1 h-11">
            <Link to="/plan">내 현재 플랜 보기</Link>
          </Button>
        }
      />
    )
  } else {
    content = (
      <div className="grid gap-3">
        <p className="text-sm text-muted-foreground tabular-nums">
          변경 {entries.length}건 · 최신순
        </p>
        <ol className="grid gap-3">
          {/* 가장 최신 1건(목록 첫 항목)만 펼쳐 둔다: 화면을 연 사람이 가장 궁금한 "이번에 무엇이 왜 바뀌었나"를 한 번 누르지 않고 바로 읽게 하고,
              지난 기록은 접어 두어 목록이 길어지지 않게 한다(2026-10-17 사용자 결정). 서버가 최신순으로 준다는 전제다 */}
          {entries.map((entry, index) => (
            <li key={entry.changeId}>
              <PlanChangeEntry
                entry={entry}
                serviceNames={HISTORY_SAMPLE_SERVICE_NAMES}
                defaultExpanded={index === 0}
              />
            </li>
          ))}
        </ol>
      </div>
    )
  }

  return (
    <section className="flex flex-col gap-5">
      <div className="grid gap-2">
        {/* 결과 화면에서 들어오는 하위 화면이라 되돌아가는 길을 제목 위에 둔다(머리글 메뉴는 늘리지 않는다, 사용자 결정 5) */}
        <Link
          to="/plan"
          className="inline-flex min-h-11 w-fit items-center gap-1.5 rounded-md text-sm font-semibold text-primary focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
        >
          <ArrowLeftIcon aria-hidden="true" className="size-4" />내 현재 플랜
        </Link>
        <h1 className="text-2xl font-semibold">플랜 변경 내역</h1>
        <p className="break-keep text-muted-foreground">
          매월 새 데이터로 저장한 플랜을 다시 계산해요. 플랜이 바뀐 때만 무엇이, 왜 바뀌었는지
          기록해요.
        </p>
      </div>

      <WatchedTipBanner />

      {content}

      <div className="grid gap-1.5 border-t border-border pt-4">
        <SourceAttribution />
        <p className="text-sm break-keep text-muted-foreground">
          제공 여부와 요금은 확인된 범위이며, 실제와 다를 수 있습니다. 데이터 기준일은 기록마다 따로
          적었어요.
        </p>
      </div>
    </section>
  )
}

/**
 * "다 봤음" 체크를 권하는 안내 띠다(문장은 사용자 확정). 오류·경고가 아니라 참고 안내라 info 색 + 체크 아이콘을 쓴다
 * (결과 화면의 "저장하면 바뀌어요" 안내 띠와 같은 모양). 바로 행동할 수 있게 찜 목록 링크를 함께 둔다.
 */
function WatchedTipBanner() {
  return (
    <aside
      aria-label="다 봤음 체크 안내"
      className="flex flex-col gap-2 rounded-xl border border-info bg-info-bg px-4 py-3 text-info sm:flex-row sm:items-center sm:justify-between"
    >
      <p className="flex items-start gap-2">
        <CircleCheckBigIcon aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
        <span className="font-semibold break-keep">
          다 봤음으로 체크하면 보다 정확한 구독계산이 가능해요
        </span>
      </p>
      {/* 링크도 info 글자색을 그대로 써서 띠 안에서 대비(약 9:1)를 지킨다. 밑줄로 링크임을 색 없이도 알린다 */}
      <Link
        to="/wishlist"
        className="inline-flex min-h-11 shrink-0 items-center gap-1.5 self-start rounded-md text-sm font-bold underline underline-offset-3 focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none sm:self-auto"
      >
        <ListChecksIcon aria-hidden="true" className="size-4" />찜 목록에서 체크하기
      </Link>
    </aside>
  )
}
