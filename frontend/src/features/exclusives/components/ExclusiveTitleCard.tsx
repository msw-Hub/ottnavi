import { Link } from 'react-router'
import { PosterImage } from '@/components/common/PosterImage'
// import { ProviderStatusBadge } from '@/components/common/ProviderStatusBadge' // 카드의 "있음" 배지를 다시 넣을 때 푼다
import type { ExclusiveTitle } from '@/api/generated/model'
import { getTmdbImageUrl } from '@/lib/tmdbImage'
import { cn } from '@/lib/utils'

// 작품 구분 글자(features/search/constants의 MEDIA_TYPE_LABELS와 같은 글자. 기능 폴더끼리 import하지 않아 따로 둔다)
const MEDIA_TYPE_TEXT = { movie: '영화', tv: '드라마' } as const

// 순위 숫자를 그대로 적는 한계. 이보다 크면 "999+"로 줄인다(네 자리는 128px 카드 모서리에 들어가지 않는다)
const MAX_RANK_DISPLAY = 999

interface ExclusiveTitleCardProps {
  title: ExclusiveTitle // 보여 줄 독점 작품
  rank: number // 목록 안 순서(1부터, 최근 공개작 먼저 → 인기도순). 지금까지 불러온 목록에서의 위치라 다음 쪽이 붙어도 25, 26…으로 이어진다
  // serviceName: string // 지금 고른 서비스 이름. 카드의 "있음" 배지 줄을 다시 넣을 때 함께 되살린다
}

/**
 * 독점 목록의 작품 카드다(세로 구조: 포스터(왼쪽 위에 인기 순위) → 제목 → "영화/드라마 · 연도 · 시즌").
 *
 * 홈 카드(features/home의 HomeTitleCard)와 뼈대가 같지만 찜 버튼이 없고, 대신 포스터 왼쪽 위에 인기 순위 숫자가 있다.
 * 1. 이 서비스의 "있음" 배지는 넣지 않았다. 탭과 목록 요약 줄이 서비스를 이미 말해, 모든 카드에 같은 배지가 반복될 뿐이다.
 * 2. 다른 서비스 확인 결과("다른 6곳: 없음 5 · 모름 1")는 카드에서 뺐다(2차안, 사용자 의견: 카드마다 반복돼 정보가 많다).
 *    "모름도 독점에 포함된다"는 뜻은 화면 위 산정 기준 상자가 한 번만 알린다. 응답의 comparedServiceCount·unknownElsewhereCount는 지금 쓰지 않는다.
 * 3. 드라마는 독점으로 확인된 시즌만 적는다(독점 판정이 시즌 단위라 일부 시즌만 독점일 수 있다).
 *
 * 카드 전체를 누를 수 있게 한 방법은 홈·검색 카드와 같다: 제목 링크의 ::after를 카드 크기로 늘린다.
 * 낭독기에는 제목 링크 하나로 읽히고 탭 순서도 하나다. 포커스가 오면 카드 전체에 링을 그린다.
 */
export function ExclusiveTitleCard({ title, rank }: ExclusiveTitleCardProps) {
  const detailPath = `/titles/${title.mediaType}/${title.tmdbId}`
  // 이전: 다른 곳 확인 결과 줄에서 "없음" 수를 보이려고 계산했다(2차안에서 줄을 빼며 함께 주석 처리)
  // const notAvailableCount = title.comparedServiceCount - title.unknownElsewhereCount

  return (
    <article className="group relative grid content-start gap-2 rounded-lg has-[[data-card-link]:focus-visible]:ring-2 has-[[data-card-link]:focus-visible]:ring-ring has-[[data-card-link]:focus-visible]:ring-offset-2 has-[[data-card-link]:focus-visible]:ring-offset-background">
      {/* 포스터는 TMDB 상대 경로(posterPath)를 w342 크기 주소로 바꿔 쓴다. 경로가 없으면 null → "포스터 없음" 대체 표시 */}
      <PosterImage title={title.title} posterUrl={getTmdbImageUrl(title.posterPath, 'w342')} />
      <RankNotch rank={rank} />

      <div className="grid min-w-0 content-start gap-1">
        <h3 className="line-clamp-2 text-sm leading-snug font-semibold">
          <Link
            to={detailPath}
            data-card-link=""
            title={title.title}
            className="text-inherit no-underline after:absolute after:inset-0 after:content-[''] group-hover:underline focus-visible:outline-none"
          >
            {/* 낭독기용 순위. 화면의 큰 숫자는 aria-hidden이라, 링크 이름이 "1번째 오징어 게임"처럼 읽히게 여기서 글자로 준다 */}
            {/* 띄어쓰기를 span 밖에 둔 이유: span 안 끝 공백은 이름 계산에서 잘려 "1번째심야 정비소"처럼 붙어 읽힌다 */}
            {/* 정렬이 "최근 6개월 안 공개작 먼저 → 같은 묶음 안 인기도순"으로 바뀌어 숫자가 인기 순위가 아니라 목록 안 위치가 됐다.
                그래서 "N번째"로 읽힌다. 이전: <span className="sr-only">인기 {rank}위</span> */}
            <span className="sr-only">{rank}번째</span> {title.title}
          </Link>
        </h3>
        <p className="text-xs text-muted-foreground tabular-nums">
          {MEDIA_TYPE_TEXT[title.mediaType]} · {title.releaseYear ?? '연도 미상'}
          {/* 시즌은 "시즌 1·2"처럼 가운뎃점으로 잇는다. 쉼표보다 좁은 카드에서 덜 끊긴다 */}
          {title.exclusiveSeasonNumbers && title.exclusiveSeasonNumbers.length > 0 && (
            <> · 시즌 {title.exclusiveSeasonNumbers.join('·')}</>
          )}
        </p>

        {/* 이전: 카드마다 "{서비스} [✓ 있음]" 배지를 보였다. 탭과 목록 요약 줄이 이미 서비스를 말하고 모든 카드가 같은 글자라
            정보 없이 줄만 늘어 뺐다(1차안 판단). 다시 넣으려면 아래 주석을 푼다
        <p className="flex flex-wrap items-center gap-x-1.5 gap-y-1 text-xs text-muted-foreground">
          <span>{serviceName}</span>
          <ProviderStatusBadge status="AVAILABLE" className="py-0 pr-2 pl-1.5 text-xs leading-5" />
        </p> */}

        {/* 이전: 다른 곳 확인 결과 줄. 카드마다 같은 모양의 숫자가 반복돼 정보가 많다는 사용자 의견으로 뺐다(2차안).
            "모름도 포함"은 산정 기준 상자가 한 번만 알린다. 다시 넣으려면 위 notAvailableCount 계산과 함께 푼다
        <p className="text-xs leading-snug text-muted-foreground tabular-nums">
          {title.unknownElsewhereCount === 0 ? (
            <>다른 {title.comparedServiceCount}곳 모두 없음</>
          ) : (
            <>
              다른 {title.comparedServiceCount}곳: 없음 {notAvailableCount} ·{' '}
              <strong className="font-semibold text-foreground">
                모름 {title.unknownElsewhereCount}
              </strong>
            </>
          )}
        </p> */}
      </div>
    </article>
  )
}

/**
 * 포스터 왼쪽 위 모서리의 인기 순위 숫자다(넷플릭스 "TOP 10"의 큰 숫자 느낌을 이 화면 크기에 맞게 옮겼다).
 *
 * 모양: 포스터 모서리를 페이지 바탕색(--background)으로 도려낸 듯한 칸에 굵은 큰 숫자를 넣는다.
 * - 넷플릭스처럼 포스터 "옆"에 숫자를 두면 카드마다 폭이 늘어 한 줄에 보이는 카드 수가 줄어든다. 그래서 포스터 안 모서리에 겹친다.
 * - 숫자 뒤를 포스터가 아니라 바탕색 칸으로 막은 이유: 반투명·윤곽선 숫자는 밝은 포스터 위에서 대비가 무너진다.
 *   바탕(밝기 0.125) 위 foreground(0.96) 숫자는 포스터 색과 상관없이 대비가 약 18:1로 늘 같다.
 *   칸이 바탕과 같은 색이라 "포스터 위 배지"보다 "포스터가 한 모서리 잘린 모양"으로 보여, 순위가 포스터 정보가 아니라 목록 정보라는 것이 드러난다.
 * - 가리는 넓이: 한두 자리 숫자 기준 모바일 약 32×30px(포스터 128×192px의 4% 안팎). 포스터 제목 글자는 대개 아래쪽이라 식별을 해치지 않는다.
 * 자릿수: 1~99는 큰 글자, 100~999는 한 단계 작은 글자로 같은 칸 높이를 지킨다. 1000 이상은 "999+"로 줄인다.
 * 낭독기: 숫자는 장식(aria-hidden)이고, 같은 뜻을 제목 링크 안 sr-only "N번째"가 읽힌다.
 * pointer-events-none: 숫자 칸을 눌러도 카드 전체 링크(제목 링크의 ::after)가 눌리게 한다.
 */
function RankNotch({ rank }: { rank: number }) {
  const rankText = rank > MAX_RANK_DISPLAY ? `${MAX_RANK_DISPLAY}+` : String(rank)
  const isLong = rankText.length >= 3 // 세 자리 이상이면 글자를 줄여 칸이 포스터 폭의 절반을 넘지 않게 한다

  return (
    <span
      aria-hidden="true"
      className={cn(
        // rounded-tl-lg는 포스터 모서리 둥글기와 같게, rounded-br-lg는 도려낸 칸의 안쪽 모서리다
        'pointer-events-none absolute top-0 left-0 z-10 flex min-w-8 items-center justify-center rounded-tl-lg rounded-br-lg bg-background px-1.5 pt-0.5 pb-1 leading-none font-black text-foreground tabular-nums select-none sm:min-w-9',
        // 1~2자리: 모바일 24px → 640px 이상 30px. 3자리 이상: 18px → 20px(세로 칸 높이는 min-h로 맞춘다)
        isLong ? 'min-h-7.5 text-lg sm:min-h-9 sm:text-xl' : 'text-2xl sm:text-3xl',
      )}
    >
      {rankText}
    </span>
  )
}
