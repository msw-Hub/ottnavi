import { useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import type { OttService, TitleSearchItem } from '@/api/generated/model'
// 이전(H-2 전): 서비스 배지 줄과 찜 버튼에 쓰던 부품. 카드가 로고·한 줄 정보로 바뀌어 쓰지 않는다
// import { DataInsufficientTag } from '@/components/common/DataInsufficientTag'
// import { ProviderStatusBadge } from '@/components/common/ProviderStatusBadge'
// import { CardWishlistAction } from '@/features/home/components/CardWishlistAction'
import { PosterImage } from '@/components/common/PosterImage'
import { getTmdbImageUrl } from '@/lib/tmdbImage'
import { cn } from '@/lib/utils'

// 작품 구분 글자(features/search/constants의 MEDIA_TYPE_LABELS와 같은 글자. 기능 폴더끼리 import하지 않아 따로 둔다)
const MEDIA_TYPE_TEXT = { movie: '영화', tv: '드라마' } as const

// 포스터 위 OTT 로고의 최대 개수. 넘치면 "+N"으로 줄인다(128px 카드에서 24px 로고 3개 + "+N"이 한 줄에 들어간다)
const MAX_SERVICE_LOGOS = 3
// 이전(H-2 전): "볼 수 있는 곳" 글자 칩의 최대 개수
// const MAX_SERVICE_CHIPS = 2

interface HomeTitleCardProps {
  title: TitleSearchItem // 보여 줄 작품(작품 검색 API와 같은 모양)
  services: OttService[] // 서비스 7곳(상태 목록의 서비스 ID를 이름으로 바꾸는 데 쓴다)
  focusServiceId?: number // 이 목록이 한 서비스 기준이면 그 서비스 ID. 그 서비스 로고를 맨 앞에 둔다(이전: 그 서비스의 제공 상태 배지 줄을 보였다)
  rank?: number // 순위 목록이면 순위(1부터). 주면 포스터 왼쪽에 큰 숫자로 보인다
  rankLabel?: string // 순위 앞에 낭독기가 읽을 말(예: "인기", "찜"). 화면에는 숫자만 보인다
  meta?: ReactNode // 카드 맨 아래 보조 한 줄(예: "찜 214명", "10월 15일 확인")
  leadServiceId?: number // 로고에서 맨 앞에 둘 서비스 ID(예: 새로 볼 수 있게 된 줄의 그 서비스). 로고가 3개까지만 보여 뒤로 밀려 숨지 않게 한다
  isServiceInfoHidden?: boolean // true면 정보 줄의 제공 상태 글자("제공 정보 수집 전" 등)를 붙이지 않는다. 로고는 그대로 보인다(이전: 배지·칩 줄 전체를 숨겼다)
  isFeatured?: boolean // true면 큰 카드(홈 첫 줄 "서비스별 인기 순위"). 순위 숫자·포스터 해상도·제목 글자를 키운다. 폭은 ScrollRailItem의 isFeatured가 정한다
  // true면 포스터 오른쪽 위의 "볼 수 있는 곳" OTT 로고를 그리지 않는다. 홈 "서비스 독점 작품" 줄 전용: 위 서비스 탭이 이미 어느 서비스인지 말하고
  // 모든 카드가 그 서비스 하나라 로고가 반복될 뿐이다(운영 피드백). 로고 자리는 포스터 위에 떠 있는(absolute) 요소라 빼도 카드 크기·정렬은 그대로다
  isLogoHidden?: boolean
}

/**
 * 홈·랭킹·탐색이 함께 쓰는 작품 카드다(세로 구조: [순위] 포스터(오른쪽 위 OTT 로고) → 제목 한 줄(길면 말줄임) → 정보 한 줄).
 *
 * H-2(사용자 확정)로 바뀐 점:
 * 1. 포스터 오른쪽 위의 찜 버튼 대신 "볼 수 있는 곳" OTT 로고(최대 3개 + "+N")를 얹는다(AvailableServiceLogos).
 * 2. H-3(사용자 확정 B안): 제목은 항상 한 줄이고 길면 말줄임(…)이다. 모든 카드의 정보 줄이 같은 높이에서 시작한다.
 *    이전(H-2): 제목 칸을 두 줄 높이로 고정했다(한 줄 제목 아래에 빈칸이 생겨 어색했다).
 * 3. 제목 아래를 한 줄(부가정보 · 제공 상태 글자 · 영화/드라마 · 연도)로 합쳤다(CardInfoLine).
 * 이전: 찜 버튼(CardWishlistAction), 한 서비스 기준 목록의 있음/없음/모름 배지 줄, "볼 수 있는 곳" 글자 칩 줄, 부가정보 줄이 따로 있었다.
 *
 * 카드 전체를 누를 수 있게 한 방법: 제목 링크(<a>)의 ::after를 카드 크기로 늘려 카드 위를 덮는다(검색 카드와 같다).
 * 낭독기에는 제목 링크 하나로 읽히고 탭 순서도 하나다(이제 카드 안에 따로 누르는 버튼이 없다).
 * 키보드 포커스 표시: 제목 링크(data-card-link)에 포커스가 오면 카드 전체에 링을 그린다.
 *
 * 순위 숫자는 장식이 아니라 정보라 낭독기에도 읽힌다("인기 1위"). "위"와 앞말은 화면에서 숨기고(sr-only) 숫자만 크게 보인다.
 * 큰 카드(isFeatured)는 숫자를 포스터 왼쪽 위에 겹치고(시각 전용, aria-hidden), 낭독용 "인기 1위"는 제목 바로 앞 숨은 글자로 읽힌다.
 */
export function HomeTitleCard({
  title,
  services,
  focusServiceId,
  rank,
  rankLabel = '',
  meta,
  leadServiceId,
  isServiceInfoHidden = false,
  isFeatured = false,
  isLogoHidden = false,
}: HomeTitleCardProps) {
  const detailPath = `/titles/${title.mediaType}/${title.tmdbId}`
  const hasRank = rank !== undefined
  // 포스터는 TMDB 상대 경로(posterPath)를 크기 주소로 바꿔 쓴다. 경로가 없으면 null → "포스터 없음" 대체 표시
  // 보통 카드(최대 160px)는 w342면 고해상도 화면(2배)에서도 충분하다. 큰 카드는 데스크톱에서 212px이라 2배 화면에 424px이 필요해
  // w342로는 흐려지므로 w500을 쓴다(이전: 모든 카드가 w342)
  const posterUrl = getTmdbImageUrl(title.posterPath, isFeatured ? 'w500' : 'w342')
  // 큰 카드(홈 첫 줄)는 순위 숫자를 포스터 왼쪽 위에 겹친다(B안). 숫자 칸만큼의 가로 폭을 포스터에 돌려줘 포스터가 더 커진다.
  // 랭킹·탐색 화면과 홈의 다른 줄은 isFeatured를 넘기지 않아 이전처럼 [숫자 | 포스터] 두 칸 그대로다
  const isRankOverlaid = hasRank && isFeatured
  const hasRankColumn = hasRank && !isFeatured

  return (
    <article
      className={cn(
        'group relative grid content-start gap-2 rounded-lg has-[[data-card-link]:focus-visible]:ring-2 has-[[data-card-link]:focus-visible]:ring-ring has-[[data-card-link]:focus-visible]:ring-offset-2 has-[[data-card-link]:focus-visible]:ring-offset-background',
        // 순위가 있으면 [큰 숫자 | 포스터] 두 칸. 숫자 칸 폭을 3.25rem으로 고정해 1위와 10위의 포스터 크기가 같게 한다
        // (이전: grid-cols-[auto_minmax(0,1fr)] — 숫자 폭만큼 칸이 늘어 두 자리 순위의 포스터가 작아졌다)
        hasRankColumn && 'grid-cols-[3.25rem_minmax(0,1fr)] gap-x-1',
        // 이전(B안 전): hasRank && 'grid-cols-[3.25rem_minmax(0,1fr)] gap-x-1',
        // 이전(B안 전): 큰 카드는 640px 이상에서 숫자 칸을 4.5rem으로 넓혔다. 지금은 숫자를 포스터 위에 겹쳐 숫자 칸이 없다
        // hasRank && isFeatured && 'sm:grid-cols-[4.5rem_minmax(0,1fr)]',
      )}
    >
      {hasRankColumn && (
        // text-5xl(48px) 굵은 숫자: TVING 등 OTT의 TOP 줄처럼 순위가 먼저 눈에 들어오게 한다. 큰 글자라 대비 기준은 3:1이고
        // 글자색(foreground)은 바탕 대비 약 18:1이다. tabular-nums로 1과 10의 숫자 폭을 맞춘다. 포스터 아래쪽에 붙인다(self-end)
        // 두 자리 순위(10위~)는 고정 칸에 들어가게 한 단계 작게(text-4xl) 쓴다. 오른쪽 맞춤으로 숫자가 포스터에 붙는다
        <p
          className={cn(
            'row-start-1 self-end text-right leading-none font-black tracking-tighter text-foreground tabular-nums',
            rank >= 10 ? 'text-4xl' : 'text-5xl',
            // 이전(B안 전): 큰 카드(640px 이상)는 이 칸의 숫자를 한 자리 72px, 두 자리 60px로 키웠다. 지금 큰 카드는 숫자를 포스터 위에 겹친다
            // isFeatured && (rank >= 10 ? 'sm:text-6xl' : 'sm:text-7xl'),
          )}
        >
          <span className="sr-only">{rankLabel} </span>
          {rank}
          <span className="sr-only">위</span>
        </p>
      )}

      <div className={cn('relative', hasRankColumn && 'col-start-2 row-start-1')}>
        {/* 이전(B안 전): <div className={cn('relative', hasRank && 'col-start-2 row-start-1')}> */}
        <PosterImage title={title.title} posterUrl={posterUrl} />
        {isRankOverlaid && (
          // 포스터 위에 겹친 큰 순위 숫자(시각 표시 전용). 낭독기용 "인기 1위"는 제목 바로 앞의 숨은 글자가 맡아 두 번 읽히지 않게 aria-hidden으로 둔다.
          // 대비: 어떤 포스터 위에서도 숫자가 읽히게 두 겹으로 막는다.
          // 1. 왼쪽 위 모서리를 바탕색(background, 거의 검정)으로 비스듬히 어둡게 한다(90% → 70% → 투명). 숫자 글자 자리는 바탕이 70% 이상 덮여
          //    흰 포스터 위라도 숫자 뒤가 어두운 남청색에 가깝다(글자색 foreground와 대비 약 9:1 이상, 큰 글자 기준 3:1을 넉넉히 넘는다).
          // 2. 숫자 자체에 바탕색 그림자(text-shadow-lg)를 둘러 가장자리를 한 번 더 또렷하게 한다.
          // 색만으로 순위를 전하지 않는다: 순위는 숫자 글자 자체다. rounded-tl-lg는 포스터 모서리 둥글기와 맞춘 것이다
          <div aria-hidden="true" className="pointer-events-none absolute inset-0">
            <div className="absolute inset-0 rounded-lg bg-linear-to-br from-background/90 via-background/70 via-20% to-transparent to-55%" />
            <span
              className={cn(
                'absolute top-1 left-2.5 leading-none font-black tracking-tighter text-foreground tabular-nums text-shadow-background text-shadow-lg sm:top-1.5 sm:left-3',
                // 모바일 60px · 640px 이상 72px. 두 자리 순위(10위~)는 오른쪽 위 로고와 겹치지 않게 한 단계 작게 쓴다
                rank >= 10 ? 'text-5xl sm:text-6xl' : 'text-6xl sm:text-7xl',
              )}
            >
              {rank}
            </span>
          </div>
        )}
        {/* H-2(사용자 확정): 찜 버튼 자리에 "볼 수 있는 곳" OTT 로고를 얹는다. 영화 빠른 찜은 홈 카드에서 빠지고 상세 화면에서 한다.
            이전:
            <div className="absolute top-1 right-1">
              <CardWishlistAction
                title={title.title}
                mediaType={title.mediaType}
                tmdbId={title.tmdbId}
                posterPath={title.posterPath}
                detailPath={detailPath}
              />
            </div> */}
        {/* 독점 줄(isLogoHidden)은 로고를 그리지 않는다. 낭독기의 "볼 수 있는 곳" 목록도 함께 빠지지만 서비스 이름은 바로 위 탭이 알려 준다.
            이전: 조건 없이 항상 그렸다
            <AvailableServiceLogos
              title={title}
              services={services}
              leadServiceId={leadServiceId ?? focusServiceId}
              isFeatured={isFeatured}
            /> */}
        {!isLogoHidden && (
          <AvailableServiceLogos
            title={title}
            services={services}
            leadServiceId={leadServiceId ?? focusServiceId}
            isFeatured={isFeatured}
          />
        )}
      </div>

      <div className={cn('grid min-w-0 content-start gap-1', hasRankColumn && 'col-start-2')}>
        {/* 이전(B안 전): hasRank && 'col-start-2' */}
        {isRankOverlaid && (
          // 겹친 숫자는 aria-hidden이라, 낭독기에는 제목 바로 앞에서 "인기 1위"를 읽어 준다(이어서 제목 → "인기 1위, ○○")
          <p className="sr-only">
            {rankLabel} {rank}위
          </p>
        )}
        {/* 큰 카드는 640px 이상에서 제목을 한 단계 키운다(text-base). 포스터만 커지고 제목이 작으면 위계가 어색하다.
            H-3(사용자 확정 B안): 제목은 모든 카드에서 항상 한 줄이고, 길면 끝을 말줄임(…)으로 줄인다(truncate).
            모든 카드의 제목·정보 줄 높이가 같아 줄이 가지런하고, 한 줄 제목 아래에 빈칸도 생기지 않는다.
            말줄임은 보이는 모양만 자르는 것이라 DOM 글자는 전체 제목 그대로다: 낭독기는 전체를 읽고, 마우스 사용자는 링크 title로 전체를 본다.
            이전(H-2): 'line-clamp-2 min-h-[2lh] text-sm leading-snug font-semibold' — 제목 칸을 두 줄 높이로 고정해
            한 줄 제목 아래에 큰 빈칸이 생겼다.
            이전(H-2 전): 'line-clamp-2 text-sm leading-snug font-semibold' — 한 줄 제목 카드만 정보 줄이 위로 올라가 줄이 들쭉날쭉했다 */}
        <h3
          className={cn(
            'truncate text-sm leading-snug font-semibold',
            isFeatured && 'sm:text-base',
          )}
        >
          <Link
            to={detailPath}
            data-card-link=""
            title={title.title}
            className="text-inherit no-underline after:absolute after:inset-0 after:content-[''] group-hover:underline focus-visible:outline-none"
          >
            {title.title}
          </Link>
        </h3>
        {/* H-2: 제목 아래를 한 줄로 합친다. 순서는 [줄별 부가정보] · [제공 상태 글자] · [영화/드라마 · 연도]다.
            줄마다 다른 부가정보(찜 214명, 10월 16일 확인)를 맨 앞에 둔 이유: 그 줄을 보는 이유가 되는 정보라 좁은 카드에서 말줄임이 생겨도 남아야 한다.
            길면 truncate로 한 줄 말줄임한다. 글자는 DOM에 그대로 있어 낭독기는 전체 문장을 읽고, 마우스 사용자는 title로 전체를 본다.
            제공 상태 글자는 로고가 없을 때만 붙는다(우리 DB에 없으면 "제공 정보 수집 전", 있음인 곳이 없으면 "확인된 제공처 없음").
            이전: "영화/드라마 · 연도" 줄, 제공 상태 줄(서비스 배지 또는 "볼 수 있는 곳" 칩), 부가정보 줄이 따로 최대 세 줄이었다 */}
        <CardInfoLine
          title={title}
          meta={meta}
          statusText={isServiceInfoHidden ? null : getMissingProviderText(title)}
        />
        {/* 이전:
        <p className="text-xs text-muted-foreground tabular-nums">
          {MEDIA_TYPE_TEXT[title.mediaType]} · {title.releaseYear ?? '연도 미상'}
        </p>
        {isServiceInfoHidden ? null : title.availabilities.length === 0 ? (
          <p className="text-xs text-muted-foreground">제공 정보 수집 전</p>
        ) : focusServiceId !== undefined ? (
          <FocusServiceStatus title={title} services={services} serviceId={focusServiceId} />
        ) : (
          <AvailableServiceChips title={title} services={services} leadServiceId={leadServiceId} />
        )}
        {meta && <p className="text-xs font-semibold text-foreground tabular-nums">{meta}</p>} */}
      </div>
    </article>
  )
}

// "있음"으로 확인된 서비스를 서비스 표시 순서대로 고른다. leadServiceId가 있으면 그 서비스를 맨 앞에 둔다(안정 정렬이라 나머지 순서는 그대로다)
function findAvailableServices(
  title: TitleSearchItem,
  services: OttService[],
  leadServiceId?: number,
): OttService[] {
  // 1. "있음"으로 확인된 서비스만 고른다
  const availableServices = services.filter((service) =>
    title.availabilities.some(
      (entry) => entry.ottServiceId === service.ottServiceId && entry.status === 'AVAILABLE',
    ),
  )
  // 2. 맨 앞에 둘 서비스가 있으면 앞으로 옮긴다(로고가 3개까지만 보여 "+N" 뒤로 숨지 않게)
  if (leadServiceId !== undefined) {
    availableServices.sort(
      (a, b) => Number(b.ottServiceId === leadServiceId) - Number(a.ottServiceId === leadServiceId),
    )
  }
  return availableServices
}

// 로고가 없을 때 정보 줄에 붙일 제공 상태 글자. 로고가 하나라도 있으면 null(로고가 정보를 대신한다)
function getMissingProviderText(title: TitleSearchItem): string | null {
  // 우리 DB에 아직 없는 작품(인기 순위는 TMDB 결과를 그대로 담아 이런 작품이 나올 수 있다): 있음/없음/모름 어느 쪽으로도 단정하지 않는다
  if (title.availabilities.length === 0) return '제공 정보 수집 전'
  // 제공 정보는 있지만 "있음"인 곳이 없다: "없다"고 단정하지 않고 "확인된 곳이 없다"고 쓴다(이전 칩 줄의 "확인된 제공처가 없습니다"를 줄인 말)
  if (!title.availabilities.some((entry) => entry.status === 'AVAILABLE')) {
    return '확인된 제공처 없음'
  }
  return null
}

interface AvailableServiceLogosProps {
  title: TitleSearchItem
  services: OttService[]
  leadServiceId?: number // 맨 앞에 둘 서비스(새로 들어온 서비스, 고른 서비스 탭)
  isFeatured: boolean // 큰 카드면 로고를 한 단계 키운다
}

/**
 * 포스터 오른쪽 위에 얹는 "볼 수 있는 곳" OTT 로고 묶음이다(H-2 사용자 확정: 찜 버튼 자리를 로고로 바꿈).
 *
 * - "있음"으로 확인된 서비스만 로고로 보인다. 최대 3개, 나머지는 "+N"으로 줄인다(128px 카드에서도 한 줄에 들어가는 개수).
 * - 접근 가능한 이름: 목록 자체가 "볼 수 있는 곳"이고, 로고마다 서비스 이름이 대체 텍스트(alt)다. 로고만으로 뜻을 전하지 않는다.
 * - 로고 주소는 서비스 목록 API의 logoPath(TMDB 상대 경로)를 getTmdbImageUrl로 바꾼다. w92는 32px 로고를 2배 화면에서도 선명하게 보이는 가장 작은 크기다.
 * - 포스터 그림 위에서도 로고가 보이게 반투명 바탕(bg-background/70)과 흐림을 깐다. 순위 숫자(왼쪽 위)와 겹치지 않게 오른쪽 위에 둔다.
 * - 있음인 곳이 없으면 아무것도 그리지 않고, 정보 줄이 글자("확인된 제공처 없음")로 알린다.
 * 누를 수 있는 요소가 아니라(카드 전체가 상세 링크) 늘어난 제목 링크가 그 위를 덮어도 괜찮다.
 */
function AvailableServiceLogos({
  title,
  services,
  leadServiceId,
  isFeatured,
}: AvailableServiceLogosProps) {
  const availableServices = findAvailableServices(title, services, leadServiceId)
  if (availableServices.length === 0) return null

  const visibleServices = availableServices.slice(0, MAX_SERVICE_LOGOS)
  const hiddenCount = availableServices.length - visibleServices.length
  // 로고 한 칸 크기: 보통 카드 24px, 큰 카드(640px 이상) 32px
  const logoSizeClass = isFeatured ? 'size-6 sm:size-8' : 'size-6'

  return (
    <ul
      aria-label="볼 수 있는 곳"
      className="absolute top-1.5 right-1.5 flex items-center gap-1 rounded-lg bg-background/70 p-1 backdrop-blur-sm"
    >
      {visibleServices.map((service) => (
        <li key={service.ottServiceId} className={cn('shrink-0', logoSizeClass)}>
          <ServiceLogo service={service} />
        </li>
      ))}
      {hiddenCount > 0 && (
        // "+N"은 글자라 색에 기대지 않는다. 낭독기에는 "+2곳 더"로 읽힌다
        <li
          className={cn(
            'grid shrink-0 place-items-center rounded-md border border-input text-xs font-semibold text-foreground tabular-nums',
            logoSizeClass,
          )}
        >
          +{hiddenCount}
          <span className="sr-only">곳 더</span>
        </li>
      )}
    </ul>
  )
}

// 서비스 로고 하나. 로고가 없거나 불러오지 못하면 서비스 이름 첫 글자를 대신 보인다(낭독기에는 이름 전체가 읽힌다)
function ServiceLogo({ service }: { service: OttService }) {
  // 불러오기에 실패한 주소. PosterImage와 같은 방식으로, 같은 자리에 다른 주소가 오면 다시 시도한다(실패 여부가 아니라 실패한 주소를 기억)
  const [failedUrl, setFailedUrl] = useState<string | null>(null)
  const logoUrl = getTmdbImageUrl(service.logoPath, 'w92')
  const imageUrl = logoUrl !== failedUrl ? logoUrl : null

  if (imageUrl) {
    return (
      <img
        src={imageUrl}
        alt={service.name}
        loading="lazy"
        onError={() => setFailedUrl(imageUrl)}
        className="block size-full rounded-md object-cover"
      />
    )
  }
  // 글자 대체: 카드 바탕(card) 위 글자색(foreground)이라 대비가 충분하다. 첫 글자는 장식이라 숨기고 이름 전체를 숨은 글자로 둔다
  return (
    <span className="grid size-full place-items-center rounded-md border border-input bg-card text-xs font-bold text-foreground">
      <span aria-hidden="true">{service.name.slice(0, 1)}</span>
      <span className="sr-only">{service.name}</span>
    </span>
  )
}

interface CardInfoLineProps {
  title: TitleSearchItem
  meta?: ReactNode // 줄별 부가정보(찜 214명, 10월 16일 확인). 맨 앞, 굵게
  statusText: string | null // 로고가 없을 때의 제공 상태 글자(제공 정보 수집 전 / 확인된 제공처 없음)
}

// 제목 아래 한 줄: [부가정보] · [제공 상태 글자] · [영화/드라마 · 연도]. 넘치면 한 줄 말줄임(truncate)
function CardInfoLine({ title, meta, statusText }: CardInfoLineProps) {
  const typeYearText = `${MEDIA_TYPE_TEXT[title.mediaType]} · ${title.releaseYear ?? '연도 미상'}`
  // 마우스를 올리면 잘린 부분까지 보이게 전체 문장을 title로 준다(부가정보가 글자일 때만 합칠 수 있다)
  const fullText = [typeof meta === 'string' ? meta : null, statusText, typeYearText]
    .filter(Boolean)
    .join(' · ')

  return (
    <p title={fullText} className="truncate text-xs text-muted-foreground tabular-nums">
      {meta && (
        <>
          <span className="font-semibold text-foreground">{meta}</span>
          {' · '}
        </>
      )}
      {statusText && (
        <>
          <span>{statusText}</span>
          {' · '}
        </>
      )}
      <span>{typeYearText}</span>
    </p>
  )
}

// 이전(H-2 전): 서비스 배지·"볼 수 있는 곳" 글자 칩 부품. 로고(AvailableServiceLogos)와 한 줄 정보(CardInfoLine)로 바꿔 쓰지 않는다
// interface CardStatusProps {
//   title: TitleSearchItem
//   services: OttService[]
// }
//
// // 한 서비스 기준 목록: "넷플릭스 [✓ 있음]"처럼 서비스 이름과 3상태 배지를 함께 보인다
// function FocusServiceStatus({
//   title,
//   services,
//   serviceId,
// }: CardStatusProps & { serviceId: number }) {
//   const service = services.find((entry) => entry.ottServiceId === serviceId)
//   const status =
//     title.availabilities.find((entry) => entry.ottServiceId === serviceId)?.status ?? 'UNKNOWN'
//   if (!service) return null
//
//   return (
//     <p className="flex flex-wrap items-center gap-x-1.5 gap-y-1 text-xs text-muted-foreground">
//       <span>{service.name}</span>
//       {/* 카드가 좁아(약 128px) 공통 배지를 한 단계 작게(text-xs) 쓴다. 모양(채움·실선·점선)과 아이콘·글자는 그대로다 */}
//       <ProviderStatusBadge status={status} className="py-0 pr-2 pl-1.5 text-xs leading-5" />
//       {service.dataQuality === 'INSUFFICIENT' && <DataInsufficientTag />}
//     </p>
//   )
// }
//
// // 여러 서비스 목록: "있음"인 서비스 이름만 칩으로(최대 2개 + "+N"). 하나도 없으면 글자로 알린다(검색 카드와 같은 규칙)
// // leadServiceId가 있으면 그 서비스를 맨 앞에 둔다(나머지는 서비스 표시 순서 그대로)
// function AvailableServiceChips({
//   title,
//   services,
//   leadServiceId,
// }: CardStatusProps & { leadServiceId?: number }) {
//   // 1. "있음"으로 확인된 서비스만 고른다
//   const availableServices = services.filter((service) =>
//     title.availabilities.some(
//       (entry) => entry.ottServiceId === service.ottServiceId && entry.status === 'AVAILABLE',
//     ),
//   )
//   // 2. 맨 앞에 둘 서비스가 있으면 앞으로 옮긴다(안정 정렬이라 나머지 순서는 바뀌지 않는다)
//   if (leadServiceId !== undefined) {
//     availableServices.sort(
//       (a, b) => Number(b.ottServiceId === leadServiceId) - Number(a.ottServiceId === leadServiceId),
//     )
//   }
//   const availableNames = availableServices.map((service) => service.name)
//
//   if (availableNames.length === 0) {
//     return <p className="text-xs text-muted-foreground">확인된 제공처가 없습니다</p>
//   }
//
//   const visibleNames = availableNames.slice(0, MAX_SERVICE_CHIPS)
//   const hiddenCount = availableNames.length - visibleNames.length
//   return (
//     // 칩의 바탕·글자는 "있음" 토큰(초록 채움 + 짙은 글자)이다. 칩마다 서비스 이름 글자가 있어 색만으로 뜻을 전하지 않는다
//     <ul aria-label="볼 수 있는 곳" className="flex flex-wrap gap-1">
//       {visibleNames.map((name) => (
//         <li
//           key={name}
//           className="rounded-md bg-status-available-bg px-1.5 py-0.5 text-xs leading-tight font-semibold text-status-available"
//         >
//           {name}
//         </li>
//       ))}
//       {hiddenCount > 0 && (
//         <li className="rounded-md border border-border px-1.5 py-0.5 text-xs leading-tight font-semibold text-foreground">
//           +{hiddenCount}
//           <span className="sr-only">곳 더</span>
//         </li>
//       )}
//     </ul>
//   )
// }
