import { CheckCheckIcon, ChevronDownIcon, MonitorPlayIcon, Undo2Icon, XIcon } from 'lucide-react'
import { Link } from 'react-router'
import { EstimatedTag } from '@/components/common/EstimatedTag'
import { type WishlistPriority } from '@/components/common/displayTypes'
import { PosterImage } from '@/components/common/PosterImage'
import type { ServiceStatusEntry } from '@/components/common/ServiceStatusRow'
import { PriorityBadge, PrioritySegmented } from '@/components/common/WishlistPriority'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import type { WishlistItem } from '@/api/generated/model'
import { formatDate, formatMinutes } from '@/lib/format'
import { getTmdbImageUrl } from '@/lib/tmdbImage'
import { cn } from '@/lib/utils'

export interface WishlistCardProps {
  item: WishlistItem // 찜 목록의 한 줄
  statusEntries: ServiceStatusEntry[] // 서비스별 제공 상태(서비스 이름과 합쳐진 모양)
  detailSearch: string // 작품 상세로 이동할 때 이어 붙일 주소 파라미터(location.search, 목업 확인용 파라미터 보존)
  isBusy: boolean // 이 항목에 대한 변경 요청이 진행 중인지. true면 조작을 잠시 막는다
  isExpanded: boolean // 이 카드의 서비스별 상태 패널이 펼쳐져 있는지(상태는 목록 화면이 들고 있다)
  panelId: string // 펼쳐지는 패널의 id. 펼침 버튼의 aria-controls가 가리킨다
  onToggleExpanded: () => void // "자세히 보기/접기"를 눌렀을 때
  onChangePriority: (priority: WishlistPriority) => void // 우선순위를 바꿨을 때
  onToggleWatched: () => void // "다 봤음" 표시 또는 되돌리기를 눌렀을 때
  onRemove: () => void // 오른쪽 위 X(찜 해제)를 눌렀을 때
}

/**
 * 서비스별 제공 상태를 접힌 상태에서도 읽히는 요약 글자로 만든다. 예: "넷플릭스, 티빙에서 볼 수 있음 · 4곳 없음 · 2곳 모름".
 * 볼 수 있는 서비스는 이름을, 없음·모름은 개수만 적는다(이름까지 적으면 줄이 길어져 요약의 의미가 없어진다).
 * 볼 수 있는 곳이 하나도 없으면 "볼 수 있는 곳이 확인되지 않았어요"로 시작한다("없다"고 단정하지 않는 표현).
 * 개수가 0인 항목은 생략한다.
 */
function buildSummaryText(entries: ServiceStatusEntry[]): string {
  const availableNames = entries
    .filter((entry) => entry.status === 'AVAILABLE')
    .map((entry) => entry.service.name)
  const unavailableCount = entries.filter((entry) => entry.status === 'NOT_AVAILABLE').length
  const unknownCount = entries.filter((entry) => entry.status === 'UNKNOWN').length

  const parts = [
    availableNames.length > 0
      ? `${availableNames.join(', ')}에서 볼 수 있음`
      : '볼 수 있는 곳이 확인되지 않았어요',
  ]
  if (unavailableCount > 0) parts.push(`${unavailableCount}곳 없음`)
  if (unknownCount > 0) parts.push(`${unknownCount}곳 모름`)
  return parts.join(' · ')
}

/**
 * 찜 목록의 컴팩트 카드다(SCR-08). 접힌 채 항상 같은 구조라서 같은 행의 두 카드는 높이가 같다.
 *
 * - 왼쪽: 포스터(모바일 96px, sm 이상 112px). 오른쪽 정보 열의 높이만큼 늘어나 두 열이 거의 같은 높이가 된다.
 * - 오른쪽: 제목·시즌(+ 우상단 X) → 필요 시간(+추정, "이미 시청 가능") → 우선순위 3칸 세그먼트 →
 *   서비스 요약(두 줄까지, 줄임표 없이 높이를 두 줄로 고정) → ["자세히 보기"][다 봤음] 한 줄.
 *
 * 펼침 내용(있음/없음/모름 세 묶음)은 카드 안에 넣지 않는다. 카드 안에 넣으면 한쪽만 커져 옆 카드와 정렬이 깨지므로,
 * 행 컴포넌트(WishlistRow)가 그 카드가 속한 행 아래 전체 폭 패널로 그린다. 이 카드는 펼침 상태일 때 강조 테두리만 보인다.
 * 펼침 버튼은 aria-expanded / aria-controls를 가진 진짜 button이라 키보드(Enter·Space)로 열고 낭독기가 열림 상태를 읽는다.
 *
 * "다 봤음" 항목(watchedAt 있음)은 같은 카드를 흐리게 보인다: 포스터만 반투명·흑백으로 하고 글자는 muted-foreground를 쓴다.
 * 글자 전체에 opacity를 걸지 않는 이유: 투명도가 글자 대비를 낮춰 4.5:1 기준을 깨뜨리기 때문이다.
 * 다 봤음은 흐림 정도만으로 알리지 않고 "다 봤음 · 날짜" 글자와 아이콘을 함께 둔다. 모든 조작은 높이 44px 이상이다.
 */
export function WishlistCard({
  item,
  statusEntries,
  detailSearch,
  isBusy,
  isExpanded,
  panelId,
  onToggleExpanded,
  onChangePriority,
  onToggleWatched,
  onRemove,
}: WishlistCardProps) {
  const isWatched = item.watchedAt !== null
  const displayTitle = item.seasonName ? `${item.title} ${item.seasonName}` : item.title
  const detailPath = `/titles/${item.mediaType}/${item.tmdbId}${detailSearch}`
  const summaryText = buildSummaryText(statusEntries)

  return (
    // h-full: 같은 행의 두 카드가 grid 칸 높이를 꽉 채워 높이가 같아진다. 펼친 카드는 ring-2 + primary 색 테두리로 패널과 이어 보인다
    <Card
      size="sm"
      className={cn(
        'relative h-full flex-row gap-3 px-3 sm:gap-4',
        isBusy && 'opacity-70',
        isExpanded && 'ring-2 ring-primary',
      )}
    >
      {/* 포스터 대체 텍스트("{제목} 포스터")는 PosterImage가 붙인다. 다 본 작품은 반투명·흑백으로 한 단계 물러나게 한다.
          제목 링크와 같은 곳으로 가는 보조 링크라 키보드 탭 순서와 낭독기에서는 뺀다(중복 방지).
          self-stretch + 이미지 h-full: 포스터가 오른쪽 정보 열의 높이를 따라 늘어난다(2:3 비율 고정을 푼다) */}
      <Link
        to={detailPath}
        tabIndex={-1}
        aria-hidden="true"
        className="block w-20 shrink-0 self-stretch sm:w-28"
      >
        <PosterImage
          title={item.title}
          posterUrl={getTmdbImageUrl(item.posterPath, 'w185')}
          className={cn('aspect-auto h-full min-h-36', isWatched && 'opacity-60 grayscale')}
        />
      </Link>

      <div className="flex min-w-0 flex-1 flex-col gap-1.5">
        {/* pr-8: 오른쪽 위 X 버튼(44px)과 제목이 겹치지 않게 비워 둔다. 제목은 두 줄까지 보이고 넘치면 줄임표.
            h4: 찜 목록이 "계산에 쓸 작품(h2) → 우선순위 묶음(h3) → 카드(h4)"로 쌓여 한 단계 내렸다(이전 h3) */}
        <h4 className="line-clamp-2 pr-8 text-base leading-snug font-bold">
          <Link
            to={detailPath}
            className={cn(
              'rounded-sm text-inherit underline-offset-4 hover:underline focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none',
              isWatched && 'text-muted-foreground',
            )}
          >
            {item.title}
          </Link>
          {item.seasonName && (
            <span className="ml-1.5 text-sm font-semibold text-muted-foreground">
              {item.seasonName}
            </span>
          )}
        </h4>

        <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-muted-foreground tabular-nums">
          <span>필요 시간 {formatMinutes(item.requiredMin)}</span>
          {item.isRuntimeEstimated && <EstimatedTag />}
          {/* 이미 시청 가능: 구독 중이거나 무료로 이용 중인 서비스에 이 작품이 있다. 아이콘 + 글자 배지(색만으로 알리지 않음) */}
          {item.isWatchableNow && !isWatched && (
            <span className="inline-flex items-center gap-1 rounded-full border border-info bg-info-bg px-2 py-0.5 text-xs font-bold text-info">
              <MonitorPlayIcon aria-hidden="true" className="size-3.5 shrink-0" />
              이미 시청 가능
            </span>
          )}
        </p>

        {/*
          우선순위 줄. 이전에는 "보고 싶음"이 적힌 선택 상자(Select)라 세 단계가 같은 모양이었고, 눌러서 열어 봐야 다른 단계가 보였다.
          지금은 세 칸을 늘 펼쳐 둔 세그먼트라 지금 단계와 다른 단계가 한눈에 보이고 한 번에 바꾼다(아이콘·채움·굵기로 구분, 색만으로 구분하지 않음).
          이전 Select 코드:
          <Select value={item.priority} disabled={isBusy} onValueChange={(value) => onChangePriority(value as WishlistPriority)}>
            <SelectTrigger aria-label={`${displayTitle} 우선순위`} className="data-[size=default]:h-11 min-w-28"><SelectValue /></SelectTrigger>
            <SelectContent position="popper" side="bottom" align="start" sideOffset={4}>... SelectItem ...</SelectContent>
          </Select>
        */}
        {isWatched ? (
          // 다 봤음: 우선순위를 바꿀 일이 없어 고르는 칸 대신 읽기 전용 배지로 보인다
          <p className="flex min-h-11 flex-wrap items-center gap-x-2 gap-y-1 text-sm font-semibold text-muted-foreground">
            <span className="inline-flex items-center gap-1">
              <CheckCheckIcon aria-hidden="true" className="size-4" />다 봤음 ·{' '}
              {formatDate(item.watchedAt)}
            </span>
            <PriorityBadge priority={item.priority} className="text-xs" />
          </p>
        ) : (
          <PrioritySegmented
            legend={`${displayTitle} 우선순위`}
            value={item.priority}
            disabled={isBusy}
            onChange={onChangePriority}
          />
        )}

        {/* 서비스 요약: 두 줄까지 보이고 높이를 두 줄(min-h-10 = 20px x 2)로 고정해 요약이 짧든 길든 카드 높이가 같다 */}
        <p className="line-clamp-2 min-h-10 text-sm leading-5 text-foreground">{summaryText}</p>

        {/* 아래 줄: 자세히 보기(왼쪽) + 다 봤음(오른쪽). 다 봤음은 우선순위 줄에 세그먼트가 들어가 이 줄로 내려왔다 */}
        <div className="flex flex-wrap items-center justify-between gap-x-2">
          <Button
            type="button"
            variant="ghost"
            aria-expanded={isExpanded}
            aria-controls={panelId}
            onClick={onToggleExpanded}
            className="-ml-2.5 h-11 w-fit px-2.5 text-sm"
          >
            자세히 {isExpanded ? '접기' : '보기'}
            <ChevronDownIcon
              aria-hidden="true"
              className={cn(
                'size-4 transition-transform motion-reduce:transition-none',
                isExpanded && 'rotate-180',
              )}
            />
          </Button>

          <Button
            type="button"
            variant="outline"
            disabled={isBusy}
            onClick={onToggleWatched}
            aria-label={`${displayTitle} ${isWatched ? '다 봤음 취소' : '다 봤음으로 표시'}`}
            className="h-11 px-2.5 text-sm"
          >
            {isWatched ? (
              <>
                <Undo2Icon aria-hidden="true" />다 봤음 취소
              </>
            ) : (
              <>
                <CheckCheckIcon aria-hidden="true" />다 봤음
              </>
            )}
          </Button>
        </div>
      </div>

      {/* 찜 해제 X: 카드 오른쪽 위에 겹쳐 둔다. 44px 정사각형 터치 영역(size-11), 아이콘은 20px */}
      <Button
        type="button"
        variant="ghost"
        disabled={isBusy}
        onClick={onRemove}
        aria-label={`찜 해제: ${displayTitle}`}
        className="absolute top-1 right-1 size-11 text-muted-foreground hover:text-destructive"
      >
        <XIcon aria-hidden="true" className="size-5" />
      </Button>
    </Card>
  )
}
