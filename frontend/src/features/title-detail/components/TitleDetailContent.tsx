import type { OttService, TitleDetail } from '@/api/generated/model'
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
import { formatDate, formatMinutes } from '@/lib/format'
import { buildServiceStatusEntries } from '@/lib/serviceStatus'
import { getTmdbImageUrl } from '@/lib/tmdbImage'

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
  // 계약은 TMDB 상대 경로(posterPath)를 주고, 이미지 주소 조립은 프론트가 한다(frontend.md). 포스터가 없으면 null이라 대체 표시가 나온다
  const posterUrl = getTmdbImageUrl(detail.posterPath, 'w500')

  return (
    // 구간(머리 → 제공 상태 → 시즌·찜 → 출연진 → 출처) 사이를 gap-section(40/48px) 하나로 띄운다(styles/tokens.css R1·R2).
    // 이전: <article className="flex flex-col gap-8"> (32px)
    <article className="flex flex-col gap-section">
      <div className="grid gap-6 sm:grid-cols-[14rem_1fr]">
        {/* 상세에서는 제목을 옆에 크게 보이므로 제목이 없는 PosterImage를 쓴다(PosterCard를 쓰면 제목이 두 번 나온다) */}
        <PosterImage title={detail.title} posterUrl={posterUrl} className="w-40 sm:w-full" />

        <div className="grid min-w-0 content-start gap-4">
          <header className="grid gap-1">
            <h1 className="text-2xl font-semibold">{detail.title}</h1>
            {detail.originalTitle !== detail.title && (
              <p className="text-sm text-muted-foreground">원제 {detail.originalTitle}</p>
            )}
          </header>

          {/* 이전: 영화는 제목 바로 아래(작품 상단)에 찜 버튼을 두었다. 드라마는 아래 시즌 카드 오른쪽 위에 있어
              작품 종류에 따라 찜 버튼 위치가 달라 헷갈린다는 운영 피드백이 있어, 영화도 같은 자리(서비스별 제공 상태 아래 카드 오른쪽 위)로 옮겼다(MovieWatchSection).
          {!isTv && (
            <WishlistControl
              mediaType={detail.mediaType}
              tmdbId={detail.tmdbId}
              seasonNumber={null}
              targetName={detail.title}
              posterUrl={posterUrl}
            />
          )} */}

          <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5 text-sm">
            <dt className="text-muted-foreground">구분</dt>
            <dd>{MEDIA_TYPE_LABELS[detail.mediaType]}</dd>
            <dt className="text-muted-foreground">{isTv ? '첫 방영일' : '개봉일'}</dt>
            <dd>{releaseDateText ?? '정보 없음'}</dd>
            {/* 이전: 영화일 때 여기에 러닝타임 줄을 두었다. 드라마가 시즌 카드에 총 시간을 보이듯 영화도 아래 찜 카드에 러닝타임을 보이므로
                같은 값이 두 번 나오지 않게 뺐다.
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
            )} */}
            <dt className="text-muted-foreground">장르</dt>
            <dd>{detail.genres.length > 0 ? detail.genres.join(' · ') : '정보 없음'}</dd>
          </dl>

          <Overview overview={detail.overview} language={detail.overviewLang} />
        </div>
      </div>

      {/* 구간 제목 h2 text-xl bold, 제목 묶음과 내용 사이 16px(gap-section-inner)로 맞췄다(R3·R4).
          이 화면의 구간은 aria-labelledby가 없는 이름 없는 section이라(낭독기 영역 목록에 새로 나타나지 않게) PageSection 대신 클래스만 바꿨다.
          이전: <section className="grid gap-3"><h2 className="text-lg font-semibold"> */}
      <section className="grid gap-section-inner">
        {/* 제목과 설명은 4px로 붙여 "이 제목의 설명"으로 읽히게 한다(R3). 이전에는 h2와 p가 section의 직접 자식이라 12px 떨어져 있었다 */}
        <div className="grid gap-1">
          <h2 className="text-xl font-bold">서비스별 제공 상태</h2>
          {isTv && (
            <p className="text-sm text-muted-foreground">
              드라마는 시즌 중 하나라도 제공되면 &ldquo;있음&rdquo;으로 보입니다. 시즌별 제공처는
              아래에서 확인하세요.
            </p>
          )}
        </div>
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
        <SeasonSection
          tmdbId={detail.tmdbId}
          title={detail.title}
          posterUrl={posterUrl}
          seasons={detail.seasons}
          services={services}
        />
      )}

      {/* 영화의 찜 자리: 드라마의 시즌 구역과 같은 위치·같은 카드 모양(이름 + 오른쪽 위 찜 버튼 + 시간 줄) */}
      {!isTv && <MovieWatchSection detail={detail} posterUrl={posterUrl} />}

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

interface MovieWatchSectionProps {
  detail: TitleDetail // 영화 상세(제목·러닝타임·추정 여부)
  posterUrl: string | null // 찜 추가 팝업 썸네일
}

/**
 * 영화의 "시청 시간과 찜" 구역이다. 드라마의 시즌 구역(SeasonSection)과 같은 자리·같은 카드 모양으로 둔다.
 *
 * 왜 이렇게 했나: 드라마는 찜 단위가 시즌이라 찜 버튼이 시즌 카드 오른쪽 위에 있다. 영화만 제목 아래에 두면 작품 종류마다
 * 버튼 위치가 달라 찾기 어렵다(운영 피드백). 영화는 시즌이 하나뿐인 드라마처럼 보고, 카드 하나에 이름·찜 버튼·러닝타임을 둔다.
 * 카드 클래스·제목 줄 배치(이름 왼쪽, 버튼 오른쪽 위, leading-6 + py-2.5로 첫 줄 가운데를 버튼 높이 44px의 가운데에 맞춤)는 SeasonItem과 같다.
 * (이전: pt-2.5로 버튼 높이 44px의 글자선에 맞춤 — 줄 높이를 물려받아 약 1px 어긋났다)
 * 찜 동작은 WishlistControl을 그대로 쓴다(이전과 같은 targetName이라 버튼 이름 "기생충 찜하기"도 같다).
 */
function MovieWatchSection({ detail, posterUrl }: MovieWatchSectionProps) {
  return (
    // 구간 제목·간격은 공통 PageSection과 같은 규칙이다(h2 text-xl bold, 제목과 내용 사이 16px. styles/tokens.css R3·R4)
    // 이전: <section aria-labelledby="movie-watch-heading" className="grid gap-3"><h2 … className="text-lg font-semibold">
    <section aria-labelledby="movie-watch-heading" className="grid gap-section-inner">
      <h2 id="movie-watch-heading" className="text-xl font-bold">
        시청 시간과 찜
      </h2>
      <div className="grid gap-1.5 rounded-lg border border-border bg-card p-3 sm:p-4">
        {/* 제목 줄 정렬은 SeasonItem과 같다: 줄 높이 24px(leading-6) + 위아래 10px(py-2.5)로 첫 줄 가운데를 버튼(44px) 가운데 22px에 맞춘다.
            본문 줄 높이(1.6)를 물려받던 이전 값은 글자가 버튼보다 약 1px 낮고 바닥선이 1.5px 내려가 보였다(운영 피드백).
            긴 영화 제목이 두 줄로 꺾여도 items-start라 버튼은 첫 줄 옆에 머문다.
            이전: <h3 className="min-w-0 pt-2.5 font-semibold"> */}
        <div className="grid grid-cols-[1fr_auto] items-start gap-x-2">
          <h3 className="min-w-0 py-2.5 text-base leading-6 font-semibold">{detail.title}</h3>
          <WishlistControl
            mediaType={detail.mediaType}
            tmdbId={detail.tmdbId}
            seasonNumber={null}
            targetName={detail.title}
            posterUrl={posterUrl}
            panelAlign="end"
          />
        </div>
        <p className="text-sm text-muted-foreground tabular-nums">
          {/* 값만 따로 감싸 "러닝타임"(이름표)과 "2시간 13분"(값)이 서로 다른 글자 덩어리로 읽히게 한다 */}
          러닝타임{' '}
          <span>{detail.runtimeMin === null ? '정보 없음' : formatMinutes(detail.runtimeMin)}</span>
          {/* 추정 러닝타임은 정확한 값으로 오해하지 않도록 "추정"을 붙인다 */}
          {detail.isRuntimeEstimated && (
            <>
              {' '}
              <EstimatedTag />
            </>
          )}
        </p>
      </div>
    </section>
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
