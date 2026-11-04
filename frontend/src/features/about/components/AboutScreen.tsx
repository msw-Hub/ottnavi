import type { ReactNode } from 'react'
import { ProviderStatusBadge } from '@/components/common/ProviderStatusBadge'
import { SourceAttribution } from '@/components/common/SourceAttribution'
import type { AvailabilityStatus } from '@/components/common/displayTypes'

// TMDB 공식 사이트 주소(출처 링크)
const TMDB_URL = 'https://www.themoviedb.org/'

// TMDB가 API 이용 약관에서 요구하는 고지 문구 원문(docs/TASK003_POLICY_PRICING.md 1절, "확인됨").
// 번역하지 않고 원문 그대로 보여 주어야 약관을 지킨 것이 되므로 한국어 안내는 따로 아래에 둔다
const TMDB_NOTICE =
  'This product uses TMDB and the TMDB APIs but is not endorsed, certified, or otherwise approved by TMDB.'

// 지원하는 국내 OTT 7곳
const SUPPORTED_SERVICES = [
  '넷플릭스',
  '디즈니+',
  '애플 TV+',
  '티빙',
  '웨이브',
  '왓챠',
  '쿠팡플레이',
]

// 서비스 이용 흐름 소개(찜 → 3개월 플랜)
const HOW_IT_WORKS = [
  '보고 싶은 작품을 검색해 찜합니다.',
  '월 예산과 월 시청 시간을 입력합니다.',
  '어느 달에 어느 OTT를 구독하면 좋을지 3개월 플랜으로 확인합니다.',
]

// 제공 상태 3가지의 뜻(Task 026 공통 범례와 같은 설명)
const STATUS_GUIDE: { status: AvailabilityStatus; description: string }[] = [
  { status: 'AVAILABLE', description: '구독(정액제)으로 볼 수 있다고 확인된 경우' },
  { status: 'NOT_AVAILABLE', description: '구독으로 제공하지 않음(대여·구매만 있는 경우 포함)' },
  { status: 'UNKNOWN', description: '한국 데이터가 없거나 수집 범위 밖이라 알 수 없음' },
]

interface AboutSectionProps {
  id: string // 제목(h2)의 id. 구역의 이름으로 연결한다
  title: string // 구역 제목
  children: ReactNode
}

// About 화면의 한 구역. 제목과 본문을 묶는다
function AboutSection({ id, title, children }: AboutSectionProps) {
  return (
    <section
      aria-labelledby={id}
      className="grid gap-3 rounded-xl border border-border bg-card p-4 sm:p-5"
    >
      <h2 id={id} className="text-lg font-bold">
        {title}
      </h2>
      {children}
    </section>
  )
}

/**
 * SCR-04 About(서비스 소개) 화면이다. 정적 화면이라 데이터를 불러오지 않는다(그래서 로딩·오류·빈 상태가 없다).
 *
 * 꼭 들어가야 하는 것(PRD SCR-04, frontend.md 출처 표기):
 * - TMDB 로고와 고지문: TMDB 약관은 고지 문구 표시와, 자신의 로고보다 덜 눈에 띄는 TMDB 로고 사용을 요구한다.
 * - JustWatch 출처: 제공처 정보는 JustWatch 데이터를 TMDB가 제공한 것이다(SourceAttribution 재사용).
 * - 비영리 운영 안내: TMDB 무료 API는 비상업 용도라, 광고·유료화를 하지 않는다고 밝힌다.
 * - "확정"이 아니라 "확인된 범위"라는 표현.
 *
 * TMDB 로고는 공식 로고 페이지에서 받은 SVG(public/tmdb-logo.svg)를 그대로 쓴다(2026-11-03, 자리표시 박스를 교체했다).
 * 로고 세부 사용 조건(이용약관)은 사용자가 확인하며, 파일은 수정하지 않는다.
 */
export function AboutScreen() {
  return (
    <div className="mx-auto grid max-w-3xl gap-5">
      <div className="grid gap-2">
        <h1 className="text-2xl font-semibold">OTT내비 소개</h1>
        <p className="text-muted-foreground">
          찜한 작품과 월 예산·시청 시간으로, 국내 OTT 7곳 중 어느 달에 어디를 구독할지 알려 주는
          서비스입니다.
        </p>
      </div>

      <AboutSection id="about-how" title="이렇게 사용합니다">
        <ol className="grid list-decimal gap-1.5 pl-5 text-[0.9375rem]">
          {HOW_IT_WORKS.map((step) => (
            <li key={step}>{step}</li>
          ))}
        </ol>
        <p className="text-sm text-muted-foreground">
          지원하는 서비스: {SUPPORTED_SERVICES.join(', ')}
        </p>
        <p className="text-sm text-muted-foreground">
          결과는 &ldquo;확정&rdquo;이 아니라 데이터로{' '}
          <strong className="text-foreground">확인된 범위</strong>의 정보입니다. 실제 서비스의 제공
          여부와 요금은 다를 수 있으니 가입 전에 각 서비스에서 한 번 더 확인해 주세요.
        </p>
      </AboutSection>

      <AboutSection id="about-status" title="제공 상태 읽는 법">
        <ul className="grid gap-2 text-[0.9375rem]">
          {STATUS_GUIDE.map(({ status, description }) => (
            <li key={status} className="flex items-start gap-3">
              <span className="min-w-19 flex-none">
                <ProviderStatusBadge status={status} />
              </span>
              <span className="pt-0.5 text-muted-foreground">{description}</span>
            </li>
          ))}
        </ul>
      </AboutSection>

      <AboutSection id="about-sources" title="데이터 출처">
        {/* 로고를 고지문 첫 줄 위쪽에 맞춘다(items-start). 가운데 정렬이면 모바일에서 고지문이 6~7줄로 길어질 때
            로고가 문단 한가운데 떠 보여 어느 문장의 로고인지 흐려진다.
            이전: className="flex flex-wrap items-center gap-4" */}
        <div className="flex flex-wrap items-start gap-4">
          {/* TMDB 공식 로고(themoviedb.org/about/logos-attribution에서 받은 SVG 그대로, 수정하지 않았다. 2026-11-03).
              약관이 "자신의 로고보다 덜 눈에 띄는 크기"를 요구한다. 헤더의 "OTT내비" 글자 로고(18px 굵은 글씨, 폭 약 65px)와 비교하면
              높이 40px(h-10, 폭 약 55px)의 3줄 컬러 로고는 더 커 보여서 높이 32px(h-8, 폭 약 44px)로 줄였다.
              안의 "THE·DB" 글자가 약 9px로 아직 읽히는 크기다. 너비는 원본 비율(185:133)을 따른다.
              윤곽(2026-11-03 사용자 요청): 로고 파일은 고치지 않고, 바깥에 테두리 있는 틀(border-border, 옅은 바탕 bg-muted)을 둘러
              로고의 경계가 어두운 카드 위에서 또렷이 보이게 한다. 틀 안 여백(px-2.5 py-1.5)만큼 전체 높이는 44px라
              로고(32px)보다 커 보이므로, 틀은 모서리를 둥글게 하되 눈에 띄는 색을 쓰지 않고 기존 테두리·바탕 토큰만 쓴다.
              mt-1은 틀이 생기며 필요 없어져 뺐다(틀 윗선이 고지문 첫 줄 윗선과 맞는다).
              이전: <img src="/tmdb-logo.svg" alt="TMDB" className="mt-1 h-8 w-auto flex-none" />
              이전(1차): <img src="/tmdb-logo.svg" alt="TMDB" className="h-10 w-auto flex-none" />
              이전: 승인 로고 파일이 오기 전까지의 자리표시 박스
              <div role="img" aria-label="TMDB 로고 자리 (승인 로고로 교체 예정)" className="grid h-14 w-28 flex-none place-items-center rounded-lg border border-dashed border-border bg-muted text-sm font-bold tracking-wide text-muted-foreground">TMDB</div> */}
          <span className="inline-flex flex-none items-center rounded-lg border border-border bg-muted px-2.5 py-1.5">
            <img src="/tmdb-logo.svg" alt="TMDB" className="h-8 w-auto" />
          </span>
          <div className="grid min-w-0 flex-1 gap-1.5">
            {/* 고지 문구는 원문 그대로(영어). 낭독기가 영어로 읽도록 lang="en"을 붙인다 */}
            <p lang="en" className="text-[0.9375rem]">
              {TMDB_NOTICE}
            </p>
            <p className="text-sm text-muted-foreground">
              이 서비스는{' '}
              <a
                href={TMDB_URL}
                target="_blank"
                rel="noopener noreferrer"
                className="text-primary underline underline-offset-3"
              >
                TMDB
                <span className="sr-only">(새 탭에서 열림)</span>
              </a>
              의 API를 사용하지만 TMDB의 보증이나 인증, 승인을 받은 서비스가 아닙니다.
            </p>
          </div>
        </div>
        <SourceAttribution />
      </AboutSection>

      <AboutSection id="about-nonprofit" title="비영리 운영 안내">
        <p className="text-[0.9375rem]">
          OTT내비는 수익을 목적으로 하지 않는 비영리 프로젝트입니다. 광고를 싣거나 이용료를 받지
          않습니다.
        </p>
      </AboutSection>
    </div>
  )
}
