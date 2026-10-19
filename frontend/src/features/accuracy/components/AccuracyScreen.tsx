import type { ReactNode } from 'react'
import { CheckIcon, TriangleAlertIcon } from 'lucide-react'
import { DataInsufficientTag } from '@/components/common/DataInsufficientTag'
import { SourceAttribution } from '@/components/common/SourceAttribution'

/*
 * 아래 수치는 모두 docs/PRD.md 8.2 "데이터 근거"의 검증 결과를 그대로 옮긴 것이다.
 * 검증을 다시 하거나 PRD 8.2가 바뀌면(Task 103) 화면 코드는 두고 이 상수들만 고친다.
 *
 * 2026-10-19 재검증(사용자 결정): 이전 결과(35건 중 30/33 일치)는 표본을 뽑은 방식의 기록이 없어 버리고,
 * 우리 DB의 "구독 제공" 판정에서 서비스마다 10건씩 무작위로 뽑아 사용자가 앱에서 직접 확인한 결과(70건 중 66건)만 싣는다.
 */

// 합산 결과(PRD 8.2 "검증 방법"의 결과 문장)
const OVERALL_RESULT = {
  matched: 66, // 일치한 건수
  checked: 70, // 확인한 건수(서비스 7곳 × 10건)
  percentLabel: '약 94%', // 66/70을 반올림한 값. 계산하지 않고 PRD 문구를 그대로 쓴다
  populationLabel: '약 6만 건', // 우리 DB의 "구독 제공" 판정 수(영화 한 건·드라마 시즌 한 건 단위, 서비스 간 중복 포함, 59,649건)
}

// 서비스별 결과 표시 3가지. 색만으로 나누지 않고 아이콘 + 글자를 함께 쓴다(frontend.md 화면 표시 규칙)
type ServiceResultKind = 'MATCHED' | 'MISMATCH' | 'INSUFFICIENT'

interface ServiceAccuracy {
  service: string // 서비스 이름
  matched: number // 일치 건수
  checked: number // 확인한(유효) 건수
  kind: ServiceResultKind // 결과 표시 종류
  note?: string // 비고. 없으면 표시하지 않는다
}

// 서비스별 일치 표(PRD 8.2 표와 같은 순서·수치·비고)
const SERVICE_RESULTS: ServiceAccuracy[] = [
  {
    service: '넷플릭스',
    matched: 10,
    checked: 10,
    kind: 'MATCHED',
    note: '한글 자막·더빙이 없지만 볼 수 있는 작품 1건 포함',
  },
  { service: '디즈니+', matched: 10, checked: 10, kind: 'MATCHED' },
  { service: '애플TV+', matched: 10, checked: 10, kind: 'MATCHED' },
  { service: '티빙', matched: 10, checked: 10, kind: 'MATCHED' },
  { service: '웨이브', matched: 10, checked: 10, kind: 'MATCHED' },
  {
    service: '왓챠',
    matched: 6,
    checked: 10,
    kind: 'MISMATCH',
    note: '구독 제공이라 했지만 영화 4건이 확인되지 않음(서비스 중이 아님 3건, 앱에 없음 1건)',
  },
  {
    // 쿠팡플레이는 10/10이라도 구독 확인 작품이 드라마 시즌 21건뿐이라 "일치"로 보이면 과신을 부른다. 그래서 "데이터 부족"으로 표시한다(PRD 8.2 한계)
    service: '쿠팡플레이',
    matched: 10,
    checked: 10,
    kind: 'INSUFFICIENT',
    note: '구독 확인 드라마 시즌 21건 중 10건',
  },
]

// 검증 방법(PRD 8.2 "검증 방법"을 짧게 다듬음)
const METHODS = [
  '“구독 제공”으로 판정한 약 6만 건(영화 한 건, 드라마 시즌 한 건 단위, 서비스 간 중복 포함) 중에서 서비스마다 10건씩 총 70건을 무작위로 뽑아, 실제 앱에서 구독으로 볼 수 있는지 확인했습니다(정밀도).',
  '구독(정액제)으로 볼 수 있는지만 봤고, 한글 자막·더빙이 있는지는 보지 않았습니다.',
  '앱에는 있는데 데이터에서 빠진 작품(재현율)은 측정하지 않았습니다.',
  '표본이 서비스당 10건으로 작아, 합산보다 서비스별 수치로 보는 것이 정확합니다.',
]

// 한계(PRD 8.2 "한계" 6개를 짧게 다듬음). 쿠팡플레이 항목은 "데이터 부족" 표시와 함께 보이도록 따로 둔다
const LIMITS = [
  '서비스당 10건만 확인한 표본이라 서비스별 정확도를 정밀하게 말하기엔 작습니다.',
  '왓챠의 불일치 4건은 모두 영화였고 개봉 연도는 1991~2021년으로 제각각이었습니다. 원인은 확인하지 못했습니다.',
  '시즌마다 제공처가 다른 경우의 정확도는 1개 작품에서만 확인했습니다.',
  '재현율을 재지 않아, 다른 서비스 쪽 데이터가 빠지면 독점이 아닌 작품이 독점으로 보일 수 있습니다.',
  '표시되는 제목이 앱의 제목과 달라 검색에 안 잡히는 작품이 있을 수 있습니다(예: 앱에서는 101달마시안인 작품이 101마리 강아지로 표시).',
  '데이터는 하루 단위로 갱신되며, 최대 몇 시간 늦게 반영될 수 있습니다.',
]
const COUPANG_LIMIT =
  '쿠팡플레이는 영화를 판정하지 못하고 드라마만 확인돼 담긴 범위가 매우 좁습니다(앱 카탈로그와 비교하지 않음). 10/10도 구독 확인 드라마 시즌 21건 중 10건을 본 결과라 의미가 적습니다.'

interface AccuracySectionProps {
  id: string // 제목(h2)의 id. 구역의 이름으로 연결한다
  title: string // 구역 제목
  children: ReactNode
}

/*
 * 이 화면의 한 구역(제목 + 본문 카드). About 화면의 구역과 같은 모양이다.
 * 공통으로 올리지 않고 여기 둔 이유: 기능 폴더끼리 import하지 않는 규칙이 있고, 몇 줄짜리라 복제 비용이 작다.
 */
function AccuracySection({ id, title, children }: AccuracySectionProps) {
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

/*
 * 서비스 한 줄의 결과 표시.
 * - 일치: 초록으로 채운 알약 + 체크 아이콘 + "일치"
 * - 불일치 있음: 노랑 바탕 알약 + 경고 삼각형 + "불일치 있음"
 * - 데이터 부족: 공통 DataInsufficientTag(데이터베이스 아이콘 + "데이터 부족")를 그대로 쓴다
 * 세 가지가 아이콘 모양과 글자로 모두 달라, 흑백으로 봐도 구분된다. 아이콘은 글자와 뜻이 같아 낭독기에서는 숨긴다.
 * 색 대비: success-bg 위 success 글자, attention-bg 위 attention-fg 글자 모두 tokens.css 기준 4.5:1 이상이다.
 */
function ServiceResultTag({ kind }: { kind: ServiceResultKind }) {
  if (kind === 'INSUFFICIENT') return <DataInsufficientTag />

  const isMatched = kind === 'MATCHED'
  const Icon = isMatched ? CheckIcon : TriangleAlertIcon
  return (
    <span
      className={
        isMatched
          ? 'inline-flex items-center gap-1 rounded-lg bg-success-bg px-1.75 py-px text-xs font-bold whitespace-nowrap text-success'
          : 'inline-flex items-center gap-1 rounded-lg bg-attention-bg px-1.75 py-px text-xs font-bold whitespace-nowrap text-attention-fg'
      }
    >
      <Icon aria-hidden="true" strokeWidth={3} className="size-3.5 shrink-0" />
      {isMatched ? '일치' : '불일치 있음'}
    </span>
  )
}

/**
 * SCR-05 데이터 정확도 안내 화면(`/accuracy`, 공개)이다.
 * TMDB(JustWatch) 제공처 데이터가 실제 앱과 얼마나 맞는지 직접 확인한 결과와 그 한계를 알려 준다.
 *
 * 정적 화면인 이유: PRD 11절 15번(R11-15) 추천안대로, 검증 결과가 한 번 측정한 고정값이라 데이터 호출이 없다.
 * 그래서 로딩·오류·빈 상태도 없다. 수치는 파일 위쪽 상수(PRD 8.2 출처)만 고치면 화면 전체가 바뀐다.
 *
 * 화면 순서(긴 화면을 싫어한다는 사용자 의견에 맞춰 핵심부터 짧게):
 * 1. 제목과 큰 합산 수치(66/70 일치, 약 94%) — 한눈에 결론
 * 2. 서비스별 일치 표 — 375px에서도 가로 스크롤이 없도록 2열(서비스·비고 / 결과)로 줄였다
 * 3. 검증 방법, 4. 한계 — 짧은 목록
 * 5. JustWatch 출처
 *
 * 표현 수위: 결과를 "정확하다"고 단정하지 않고 "확인된 범위"로 쓴다(frontend.md 표현 수위).
 * 그래서 합산 수치도 강조색 대신 기본 글자색으로 크게만 보이게 해 과한 확신을 주지 않게 했다.
 */
export function AccuracyScreen() {
  return (
    <div className="mx-auto grid max-w-3xl gap-5">
      <div className="grid gap-2">
        <h1 className="text-2xl font-semibold">데이터 정확도 안내</h1>
        <p className="text-muted-foreground">
          OTT내비의 제공처 정보가 실제 앱과 얼마나 맞는지 직접 확인한 결과입니다. 결과는
          &ldquo;확정&rdquo;이 아니라 <strong className="text-foreground">확인된 범위</strong>
          입니다.
        </p>
      </div>

      {/* 1. 합산 결과. 숫자는 크게, 설명은 한 줄로 */}
      <section
        aria-labelledby="accuracy-summary"
        className="flex flex-wrap items-center gap-x-6 gap-y-3 rounded-xl border border-border bg-card p-4 sm:p-5"
      >
        <h2 id="accuracy-summary" className="sr-only">
          검증 결과 요약
        </h2>
        <p className="flex items-baseline gap-2">
          {/* tabular-nums: 숫자 폭을 같게 해 "66/70"이 흔들리지 않고 또렷이 읽히게 한다 */}
          <span className="text-4xl font-bold tabular-nums sm:text-5xl">
            {OVERALL_RESULT.matched}/{OVERALL_RESULT.checked}
          </span>
          <span className="text-lg font-semibold">일치 · {OVERALL_RESULT.percentLabel}</span>
        </p>
        <p className="min-w-0 flex-1 basis-60 text-sm text-muted-foreground">
          &ldquo;구독 제공&rdquo;으로 판정한 {OVERALL_RESULT.populationLabel}(서비스 간 중복 포함)
          가운데 서비스마다 10건씩 {OVERALL_RESULT.checked}건을 무작위로 뽑아 실제 앱에서 확인한
          표본 검사입니다.
        </p>
      </section>

      {/* 2. 서비스별 결과 표 */}
      <AccuracySection id="accuracy-services" title="서비스별 결과">
        <table className="w-full text-[0.9375rem]">
          {/* 표 제목은 구역 제목(h2)과 겹쳐 눈에는 숨기고, 낭독기에만 표의 뜻을 알린다 */}
          <caption className="sr-only">서비스별 일치 건수와 비고</caption>
          <thead>
            <tr className="border-b border-border text-left text-sm text-muted-foreground">
              <th scope="col" className="pb-2 font-medium">
                서비스
              </th>
              <th scope="col" className="pb-2 text-right font-medium">
                일치 / 확인
              </th>
            </tr>
          </thead>
          <tbody>
            {SERVICE_RESULTS.map(({ service, matched, checked, kind, note }) => (
              <tr key={service} className="border-b border-border last:border-b-0">
                {/* 서비스 이름 아래에 비고를 작게 둔다. 3열로 나누면 375px에서 비고 칸이 너무 좁아진다 */}
                <th scope="row" className="py-2.5 pr-3 text-left align-top font-normal">
                  <span className="font-semibold">{service}</span>
                  {note && (
                    <span className="mt-0.5 block text-sm text-muted-foreground">{note}</span>
                  )}
                </th>
                <td className="py-2.5 text-right align-top whitespace-nowrap">
                  {/* 넓은 화면은 수치와 표시를 한 줄에 두어 행 높이를 줄이고, 좁은 화면은 위아래로 쌓아 폭을 아낀다 */}
                  <div className="flex flex-col items-end gap-1 sm:flex-row-reverse sm:items-center sm:gap-2.5">
                    {/* 데이터 부족인 서비스의 수치는 표본이 12건 안이라 의미가 적으므로 흐리게 보여 과신을 막는다(사용자 결정 2026-10-19) */}
                    <span
                      className={
                        kind === 'INSUFFICIENT'
                          ? 'font-bold tabular-nums text-muted-foreground'
                          : 'font-bold tabular-nums'
                      }
                    >
                      {matched}/{checked}
                    </span>
                    <ServiceResultTag kind={kind} />
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </AccuracySection>

      {/* 3. 검증 방법 */}
      <AccuracySection id="accuracy-method" title="어떻게 확인했나요">
        <ul className="grid list-disc gap-1.5 pl-5 text-[0.9375rem]">
          {METHODS.map((method) => (
            <li key={method}>{method}</li>
          ))}
        </ul>
      </AccuracySection>

      {/* 4. 한계 */}
      <AccuracySection id="accuracy-limits" title="알아 두실 한계">
        <ul className="grid list-disc gap-1.5 pl-5 text-[0.9375rem]">
          {LIMITS.map((limit) => (
            <li key={limit}>{limit}</li>
          ))}
          {/* 쿠팡플레이 항목은 표의 "데이터 부족" 표시와 같은 태그를 붙여 둘이 같은 이유임을 연결한다 */}
          <li>
            <DataInsufficientTag /> {COUPANG_LIMIT}
          </li>
        </ul>
      </AccuracySection>

      {/* 5. 출처. 제공처 정보를 다루는 화면이라 JustWatch 출처를 반드시 둔다 */}
      <SourceAttribution />
    </div>
  )
}
