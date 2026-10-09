import { useState } from 'react'
import type { OttService } from '@/api/generated/model'
import { AvailableServiceBadges } from '@/components/common/AvailableServiceBadges'
import { EstimatedTag } from '@/components/common/EstimatedTag'
import { Button } from '@/components/ui/button'
import { WishlistControl } from '@/features/title-detail/components/WishlistControl'
import type { TitleSeason } from '@/features/title-detail/mockTypes'
import { formatMinutes } from '@/lib/format'
import { buildServiceStatusEntries } from '@/lib/serviceStatus'

// 처음에 보이는 시즌 줄 수. 시즌이 많은 드라마(예: 12시즌)도 화면이 길어지지 않게 나머지는 "시즌 더 보기"로 연다
const INITIAL_VISIBLE_SEASONS = 5

interface SeasonSectionProps {
  tmdbId: number // 드라마의 TMDB ID(시즌 단위 찜 요청에 쓴다)
  seasons: TitleSeason[] // 드라마의 시즌들
  services: OttService[] // 서비스 목록 API의 7개 서비스
}

// 시즌의 "있음" 서비스 ID들을 정렬해 하나의 문자열로 만든다(시즌끼리 제공처가 같은지 비교하는 열쇠)
function toAvailableKey(season: TitleSeason): string {
  return season.availabilities
    .filter((availability) => availability.status === 'AVAILABLE')
    .map((availability) => availability.ottServiceId)
    .sort((a, b) => a - b)
    .join(',')
}

/**
 * 드라마의 시즌별 총 시청 시간·시즌 단위 찜을 줄 목록으로 보여 준다(PRD SCR-02, FR-09).
 *
 * 시즌마다 나누는 이유: 찜과 필요 시간 계산의 단위가 시즌이고, 시즌에 따라 볼 수 있는 서비스가 다를 수 있기 때문이다.
 * 접이식을 없앤 이유: 줄마다 펼칠 만한 정보가 "볼 수 있는 곳"뿐이라 클릭 한 번이 아깝다. 줄 자체가 정보를 다 보여 준다.
 * 시즌별 7개 서비스 상태 표를 따로 두지 않은 이유: 시즌 줄의 칩이 "어디서 볼 수 있나"를 이미 알려 주고,
 * 작품 전체의 있음/없음/모름 표(서비스별 자세히 보기)가 위에 있다. 시즌마다 7줄을 또 두면 시즌 12개일 때 화면이 매우 길어진다.
 * 제공처 칩을 시즌 제공처가 서로 다를 때만 보이는 이유: 전 시즌이 같으면 줄마다 같은 칩이 반복되어 소음일 뿐이고,
 * 위 "볼 수 있는 곳" 요약과 같은 정보다. 이 값은 seasons에서 계산해 쓰는 파생 값이라 상태로 두지 않는다.
 */
export function SeasonSection({ tmdbId, seasons, services }: SeasonSectionProps) {
  // 모두 보기 여부는 이 화면 안에서만 의미가 있는 값이라 서버 상태도 URL도 아닌 useState로 둔다(frontend.md 상태 관리 원칙)
  const [isShowingAll, setIsShowingAll] = useState(false)

  const visibleSeasons = isShowingAll ? seasons : seasons.slice(0, INITIAL_VISIBLE_SEASONS)
  const hiddenCount = seasons.length - visibleSeasons.length

  const hasDifferentProviders = new Set(seasons.map(toAvailableKey)).size > 1
  const totalRuntimeMin = seasons.reduce((sum, season) => sum + season.totalRuntimeMin, 0)
  const isAnyRuntimeEstimated = seasons.some((season) => season.isRuntimeEstimated)

  return (
    <section className="grid gap-3">
      <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
        <h2 className="text-lg font-semibold">시즌별 제공처와 시청 시간</h2>
        {seasons.length > 0 && (
          <p className="text-sm text-muted-foreground tabular-nums">
            시즌 {seasons.length}개 · 총 {formatMinutes(totalRuntimeMin)}
            {/* 추정 러닝타임이 섞인 합계는 정확한 값으로 오해하지 않도록 "추정"을 붙인다 */}
            {isAnyRuntimeEstimated && (
              <>
                {' '}
                <EstimatedTag />
              </>
            )}
          </p>
        )}
      </div>
      {seasons.length === 0 ? (
        <p className="text-sm text-muted-foreground">시즌 정보가 없습니다.</p>
      ) : (
        <>
          {!hasDifferentProviders && seasons.length > 1 && (
            <p className="text-sm text-muted-foreground">
              모든 시즌의 제공처가 같습니다. 위 &ldquo;볼 수 있는 곳&rdquo;을 확인하세요.
            </p>
          )}
          <ul className="grid gap-3">
            {visibleSeasons.map((season) => (
              <li key={season.seasonNumber}>
                <SeasonItem
                  tmdbId={tmdbId}
                  season={season}
                  services={services}
                  isShowingProviders={hasDifferentProviders}
                />
              </li>
            ))}
          </ul>
          {hiddenCount > 0 && (
            <Button
              type="button"
              variant="outline"
              className="h-10 w-fit"
              onClick={() => setIsShowingAll(true)}
            >
              시즌 더 보기 (남은 {hiddenCount}개)
            </Button>
          )}
        </>
      )}
    </section>
  )
}

interface SeasonItemProps {
  tmdbId: number
  season: TitleSeason
  services: OttService[]
  isShowingProviders: boolean // 시즌마다 제공처가 달라 이 줄에 "볼 수 있는 곳" 칩을 보일지
}

/**
 * 시즌 하나의 줄이다. 위에서부터 ① 시즌 이름 + 오른쪽 위 찜 버튼 ② 방영 연도·회차·총 시간 ③ 제공처 칩(시즌끼리 다를 때만).
 *
 * 찜 버튼을 이름 줄 오른쪽 위에 고정한 이유: 시간 글자가 좁은 폭에서 줄바꿈되어도 버튼 위치가 흔들리지 않게 하기 위함이다.
 * h3에는 이름 글자만 둔다: 버튼이 안에 있으면 화면 낭독기의 제목 목록에 "시즌 2 찜하기"까지 읽혀 번잡하다. 버튼은 h3의 형제 요소다.
 * 안쪽 여백은 모바일 12px(p-3), 데스크톱 16px(sm:p-4)다.
 */
function SeasonItem({ tmdbId, season, services, isShowingProviders }: SeasonItemProps) {
  const statusEntries = buildServiceStatusEntries(services, season.availabilities)

  return (
    <div className="grid gap-1.5 rounded-lg border border-border bg-card p-3 sm:p-4">
      <div className="grid grid-cols-[1fr_auto] items-start gap-x-2">
        <h3 className="min-w-0 pt-2.5 font-semibold">{season.name}</h3>
        <WishlistControl
          mediaType="tv"
          tmdbId={tmdbId}
          seasonNumber={season.seasonNumber}
          targetName={season.name}
          panelAlign="end"
        />
      </div>
      <p className="text-sm text-muted-foreground tabular-nums">
        {season.airYear !== null && `${season.airYear}년 방영 · `}
        {season.episodeCount}회차 · 총 {formatMinutes(season.totalRuntimeMin)}
        {/* 추정 러닝타임이 섞인 총 시간은 정확한 값으로 오해하지 않도록 "추정"을 붙인다 */}
        {season.isRuntimeEstimated && (
          <>
            {' '}
            <EstimatedTag />
          </>
        )}
      </p>
      {isShowingProviders && (
        <div className="mt-1">
          <AvailableServiceBadges entries={statusEntries} label={`${season.name} 볼 수 있는 곳`} />
        </div>
      )}
    </div>
  )
}
