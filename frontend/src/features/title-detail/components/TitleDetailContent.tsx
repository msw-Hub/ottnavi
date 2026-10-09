import type { OttService } from '@/api/generated/model'
import { AvailableServiceBadges } from '@/components/common/AvailableServiceBadges'
import { DataAsOf } from '@/components/common/DataAsOf'
import { EstimatedTag } from '@/components/common/EstimatedTag'
import { PosterImage } from '@/components/common/PosterImage'
import { ServiceStatusRow } from '@/components/common/ServiceStatusRow'
import { SourceAttribution } from '@/components/common/SourceAttribution'
import { CastList } from '@/features/title-detail/components/CastList'
import { SeasonSection } from '@/features/title-detail/components/SeasonSection'
import { WishlistControl } from '@/features/title-detail/components/WishlistControl'
import { MEDIA_TYPE_LABELS } from '@/features/title-detail/constants'
import type { TitleDetail } from '@/features/title-detail/mockTypes'
import { formatDate, formatMinutes } from '@/lib/format'
import { buildServiceStatusEntries } from '@/lib/serviceStatus'

interface TitleDetailContentProps {
  detail: TitleDetail // 불러온 작품 상세
  services: OttService[] // 서비스 목록 API의 7개 서비스
}

/**
 * 작품 상세를 다 불러온 뒤의 본문이다(포스터·제목·줄거리·서비스별 상태·시즌·출연진·출처).
 * 로딩·오류·주소 오류 상태는 TitleDetailScreen이 맡고, 이 컴포넌트는 성공한 데이터를 그리기만 한다.
 */
export function TitleDetailContent({ detail, services }: TitleDetailContentProps) {
  const isTv = detail.mediaType === 'tv'
  // 개봉일/방영일은 "2021년 9월 17일" 모양의 일반 날짜 포맷 함수로 만든다(기준일용 formatDataAsOf와 의미를 구분)
  const releaseDateText = formatDate(detail.releaseDate)
  const statusEntries = buildServiceStatusEntries(services, detail.availabilities)

  return (
    <article className="flex flex-col gap-8">
      <div className="grid gap-6 sm:grid-cols-[14rem_1fr]">
        {/* 상세에서는 제목을 옆에 크게 보이므로 제목이 없는 PosterImage를 쓴다(PosterCard를 쓰면 제목이 두 번 나온다) */}
        <PosterImage title={detail.title} posterUrl={detail.posterUrl} className="w-40 sm:w-full" />

        <div className="grid min-w-0 content-start gap-4">
          <header className="grid gap-1">
            <h1 className="text-2xl font-semibold">{detail.title}</h1>
            {detail.originalTitle !== detail.title && (
              <p className="text-sm text-muted-foreground">원제 {detail.originalTitle}</p>
            )}
          </header>

          {/* 영화는 작품 상단에 찜 버튼 하나를 둔다(드라마는 시즌마다 시즌 요약 줄에 있다) */}
          {!isTv && (
            <WishlistControl
              mediaType={detail.mediaType}
              tmdbId={detail.tmdbId}
              seasonNumber={null}
              targetName={detail.title}
            />
          )}

          <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5 text-sm">
            <dt className="text-muted-foreground">구분</dt>
            <dd>{MEDIA_TYPE_LABELS[detail.mediaType]}</dd>
            <dt className="text-muted-foreground">{isTv ? '첫 방영일' : '개봉일'}</dt>
            <dd>{releaseDateText ?? '정보 없음'}</dd>
            {/* 드라마의 시간은 시즌별 총 시청 시간으로 아래에서 보여 주므로 영화일 때만 러닝타임 줄을 둔다 */}
            {!isTv && (
              <>
                <dt className="text-muted-foreground">러닝타임</dt>
                <dd>
                  {detail.runtimeMin === null ? '정보 없음' : formatMinutes(detail.runtimeMin)}
                  {detail.isRuntimeEstimated && (
                    <>
                      {' '}
                      <EstimatedTag />
                    </>
                  )}
                </dd>
              </>
            )}
            <dt className="text-muted-foreground">장르</dt>
            <dd>{detail.genres.length > 0 ? detail.genres.join(' · ') : '정보 없음'}</dd>
          </dl>

          <Overview overview={detail.overview} language={detail.overviewLang} />
        </div>
      </div>

      <section className="grid gap-3">
        <h2 className="text-lg font-semibold">서비스별 제공 상태</h2>
        {isTv && (
          <p className="text-sm text-muted-foreground">
            드라마는 시즌 중 하나라도 제공되면 &ldquo;있음&rdquo;으로 보입니다. 시즌별 제공처는
            아래에서 확인하세요.
          </p>
        )}
        {/* 먼저 "어디서 볼 수 있나"를 칩으로 요약하고, 있음/없음/모름 7줄은 필요할 때만 펼쳐 본다(기본 닫힘) */}
        <AvailableServiceBadges entries={statusEntries} />
        <details>
          <summary className="list-item cursor-pointer rounded-sm py-2.5 text-sm font-medium text-muted-foreground marker:text-muted-foreground hover:text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring">
            서비스별 자세히 보기
          </summary>
          <ServiceStatusRow entries={statusEntries} />
        </details>
      </section>

      {isTv && (
        <SeasonSection tmdbId={detail.tmdbId} seasons={detail.seasons} services={services} />
      )}

      <CastList cast={detail.cast} />

      {/* 제공처 정보가 보이는 화면이라 JustWatch 출처와 데이터 기준일을 둔다. 포스터·작품 정보의 출처(TMDB)도 함께 밝힌다 */}
      <div className="grid gap-1.5 border-t border-border pt-4">
        <SourceAttribution />
        <p className="text-sm text-muted-foreground">작품 정보와 포스터 출처: TMDB</p>
        <DataAsOf value={detail.dataAsOf} />
        <p className="text-sm text-muted-foreground">
          제공 여부는 확인된 범위이며, 실제 서비스 상황과 다를 수 있습니다.
        </p>
      </div>
    </article>
  )
}

interface OverviewProps {
  overview: string | null // 줄거리 본문. 아예 없으면 null
  language: TitleDetail['overviewLang'] // 줄거리 언어. 한국어 줄거리가 없으면 'en'(영어로 대체)
}

/**
 * 줄거리를 보여 준다. 한국어 줄거리가 없어 영어로 대체한 경우 "영어 원문"임을 글자로 밝힌다(PRD FR-03).
 *
 * 영어 문단에 lang="en"을 붙이는 이유: 화면 낭독기가 한국어 발음 규칙으로 영어를 읽지 않고 영어로 읽게 하기 위함이다.
 * 번역해서 보여 주지 않는 이유: 자동 번역은 오역 가능성이 있고, TMDB가 준 원문을 그대로 보이는 것이 출처 면에서 안전하다.
 */
function Overview({ overview, language }: OverviewProps) {
  return (
    <section className="grid gap-2">
      <h2 className="flex flex-wrap items-center gap-2 text-lg font-semibold">
        줄거리
        {language === 'en' && (
          // EstimatedTag처럼 별도 공통 컴포넌트까지 만들 만큼 여러 곳에서 쓰이지 않아 이 파일에만 둔다
          <span className="inline-flex items-center rounded-lg border border-border bg-muted px-1.5 text-xs leading-5 font-bold">
            영어 원문
          </span>
        )}
      </h2>
      {overview === null ? (
        <p className="text-sm text-muted-foreground">줄거리 정보가 없습니다.</p>
      ) : (
        <>
          <p lang={language === 'en' ? 'en' : undefined} className="leading-relaxed">
            {overview}
          </p>
          {language === 'en' && (
            <p className="text-sm text-muted-foreground">
              한국어 줄거리가 없어 영어 원문을 그대로 보여 드립니다.
            </p>
          )}
        </>
      )}
    </section>
  )
}
