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
 * TMDB 로고는 아직 승인된 로고 파일이 없어 자리표시 박스로 두었다. 로고 사용 가이드(승인 파일·조건)는 확인 전이다
 * (docs/TASK003_POLICY_PRICING.md "미확인"). 승인 로고는 Task 088 등에서 가이드를 확인한 뒤 이 박스를 이미지로 교체한다.
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
        <div className="flex flex-wrap items-center gap-4">
          {/* 승인된 TMDB 로고 파일이 오기 전까지의 자리표시. 로고 교체 전에는 글자만 보인다(Task 088 등에서 이미지로 교체) */}
          <div
            role="img"
            aria-label="TMDB 로고 자리 (승인 로고로 교체 예정)"
            className="grid h-14 w-28 flex-none place-items-center rounded-lg border border-dashed border-border bg-muted text-sm font-bold tracking-wide text-muted-foreground"
          >
            TMDB
          </div>
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
