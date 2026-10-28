import { useId, useState } from 'react'
import {
  ArrowDownIcon,
  ArrowRightIcon,
  CalendarClockIcon,
  CalendarPlusIcon,
  ChevronDownIcon,
  CircleCheckIcon,
  EqualIcon,
  MoonIcon,
  // PiggyBankIcon, // 절감액 변화 삭제로 쓰지 않는다(2026-10-17)
  TrendingDownIcon,
  TrendingUpIcon,
} from 'lucide-react'
import { DataAsOf } from '@/components/common/DataAsOf'
import type {
  PlanChangeHistoryEntry,
  PlanChangeProduct,
  PlanChangeReasonType,
  PlanMonthChange,
  PlanTitleChange,
  PlanTitlePlacement,
} from '@/api/generated/model'
import {
  CHANGE_REASON_DISPLAY,
  getAmountDelta,
  TITLE_CHANGE_DISPLAY,
} from '@/features/plan/planHistoryDisplay'
import {
  formatAssignmentTitle,
  formatMonthLabel,
  PLAN_TYPE_LABELS,
} from '@/features/plan/planDisplay'
import { formatDate, formatWon } from '@/lib/format'
import { cn } from '@/lib/utils'

interface PlanChangeEntryProps {
  entry: PlanChangeHistoryEntry // 변경 내역 한 건
  serviceNames: Map<number, string> // 서비스 ID → 서비스 이름
  defaultExpanded?: boolean // 처음 그릴 때 펼쳐 둘지. 목록이 가장 최신 1건에만 true를 넘긴다(2026-10-17 사용자 결정)
}

/**
 * 변경 내역 목록의 한 건이다(SCR-11). 접혀 있을 때는 "언제 · 무엇이 바뀌었는지 한 줄 · 이유 칩"만 보이고,
 * 누르면 바뀐 이유 → 달별 구독 변화 → 작품 변화 → 데이터 기준일 순으로 펼쳐진다(절감액 변화는 2026-10-17에 뺐다).
 *
 * 접힘 구조를 이렇게 만든 이유(WAI-ARIA 아코디언 패턴):
 * - 제목(h2) 안에 버튼을 두고 aria-expanded로 펼침 여부를, aria-controls로 펼쳐지는 영역을 알린다.
 *   화면 낭독기 사용자는 제목 단위로 항목을 건너뛰며 훑고, 버튼에서 "펼침/접힘" 상태를 듣는다.
 * - 이유 칩까지 버튼 안에 넣어 카드 윗부분 어디를 눌러도 펼쳐지게 했다(손가락으로 누를 면적을 넓힘).
 * - 펼침 여부는 이 항목 안에서만 쓰는 화면 상태라 useState로 둔다(frontend.md 상태 관리 원칙).
 */
export function PlanChangeEntry({
  entry,
  serviceNames,
  defaultExpanded = false,
}: PlanChangeEntryProps) {
  // 처음 값만 defaultExpanded를 따르고, 이후에는 사용자가 누른 대로 바뀐다(useState 초깃값은 첫 렌더에만 쓰인다)
  const [isExpanded, setIsExpanded] = useState(defaultExpanded)
  // 펼쳐지는 영역의 id. 한 화면에 항목이 여러 개라 겹치지 않게 useId로 만든다
  const panelId = useId()
  const changedDate = formatDate(entry.changedAt)
  // 이유 칩은 종류별로 한 번만 보인다(같은 종류가 두 번 있어도 칩은 하나)
  const reasonTypes = [...new Set(entry.reasons.map((reason) => reason.reasonType))]

  return (
    <article className="rounded-xl border border-border bg-card">
      <h2 className="text-base">
        <button
          type="button"
          aria-expanded={isExpanded}
          aria-controls={panelId}
          onClick={() => setIsExpanded((value) => !value)}
          // 카드 윗부분 전체가 버튼이다. 포커스 테두리(ring)는 카드 모서리와 같은 둥글기로 안쪽에 그린다
          className="flex w-full items-start gap-3 rounded-xl p-4 text-left focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none sm:p-5"
        >
          <span className="grid min-w-0 flex-1 gap-2">
            <span className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-muted-foreground tabular-nums">
              <time dateTime={entry.changedAt} className="font-semibold text-foreground">
                {changedDate ?? '날짜 정보 없음'}
              </time>
              <span>재계산</span>
              <span aria-hidden="true">·</span>
              <span>{PLAN_TYPE_LABELS[entry.planType]} 플랜</span>
            </span>
            {/* 한 줄 요약이 가장 먼저 읽혀야 할 정보라 가장 크고 굵게 둔다(정보 위계) */}
            <span className="text-lg leading-snug font-bold break-keep">{entry.summary}</span>
            <span className="flex flex-wrap items-center gap-1.5">
              <span className="sr-only">바뀐 이유:</span>
              {reasonTypes.map((reasonType) => (
                <ReasonChip key={reasonType} reasonType={reasonType} />
              ))}
            </span>
          </span>
          {/* 펼침 표시: 글자(자세히/접기) + 방향 아이콘. 상태는 aria-expanded가 알리므로 글자는 낭독기에서 숨긴다(중복 낭독 방지) */}
          <span
            aria-hidden="true"
            className="flex shrink-0 items-center gap-1 pt-0.5 text-sm font-semibold text-primary"
          >
            <span className="max-sm:hidden">{isExpanded ? '접기' : '자세히'}</span>
            <ChevronDownIcon
              className={cn('size-5 motion-safe:transition-transform', isExpanded && 'rotate-180')}
            />
          </span>
        </button>
      </h2>

      {/* 펼친 영역. 접혀 있을 때는 DOM에서 빼서(조건부 렌더링) 탭 이동·낭독에 걸리지 않게 한다 */}
      {isExpanded && (
        <div id={panelId} className="grid gap-6 border-t border-border px-4 pt-4 pb-5 sm:px-5">
          <ReasonSection entry={entry} />
          <MonthChangeSection monthChanges={entry.monthChanges} />
          <TitleChangeSection titleChanges={entry.titleChanges} serviceNames={serviceNames} />
          {/* 절감액 변화는 뺐다(2026-10-17 사용자 결정, 아래 SavingsSection 주석)
          <SavingsSection previous={entry.previousSavings} current={entry.currentSavings} /> */}
          {/* 이 재계산에 쓴 데이터의 기준일. 기록마다 다르므로 항목 안에 둔다(화면 아래 출처 묶음과 별개) */}
          <DataAsOf value={entry.dataAsOf} />
        </div>
      )}
    </article>
  )
}

// 이유 칩: 아이콘 + 글자. 네 종류 모두 같은 중립 색이라 색이 아니라 아이콘과 글자로 구분된다
function ReasonChip({ reasonType }: { reasonType: PlanChangeReasonType }) {
  const { label, icon: Icon } = CHANGE_REASON_DISPLAY[reasonType]
  return (
    <span className="inline-flex items-center gap-1 rounded-full border border-border bg-muted px-2.5 py-0.5 text-xs font-semibold whitespace-nowrap text-foreground">
      <Icon aria-hidden="true" className="size-3.5 shrink-0" />
      {label}
    </span>
  )
}

// 펼친 영역의 소제목. 같은 모양을 네 번 쓰므로 한곳에 둔다
// 본문 크기(text-base)·기본 글자색으로 올려 소구간 안의 설명·칩(text-sm)보다 한 단계 위로 읽히게 했다(styles/tokens.css R4).
// 이전: text-sm font-semibold text-muted-foreground (설명 글자와 크기가 같고 색이 흐려 소구간 시작이 묻혔다)
function SectionHeading({ children }: { children: string }) {
  return <h3 className="text-base font-semibold">{children}</h3>
}

// 1. 바뀐 이유: 칩(종류) + 서버가 만든 한 줄 설명
function ReasonSection({ entry }: { entry: PlanChangeHistoryEntry }) {
  return (
    <section className="grid gap-2">
      <SectionHeading>바뀐 이유</SectionHeading>
      <ul className="grid gap-2">
        {entry.reasons.map((reason) => (
          <li
            key={`${reason.reasonType}-${reason.description}`}
            className="grid justify-items-start gap-1.5 rounded-lg border border-border bg-background px-3 py-2.5"
          >
            <ReasonChip reasonType={reason.reasonType} />
            <p className="break-keep">{reason.description}</p>
          </li>
        ))}
      </ul>
    </section>
  )
}

// 2. 달별 구독 변화
function MonthChangeSection({ monthChanges }: { monthChanges: PlanMonthChange[] }) {
  return (
    <section className="grid gap-2">
      <SectionHeading>달별 구독 변화</SectionHeading>
      {monthChanges.length === 0 ? (
        <p className="text-sm text-muted-foreground">
          구독할 서비스는 그대로예요. 작품을 보는 달이나 서비스만 바뀌었어요.
        </p>
      ) : (
        <ul className="grid gap-3">
          {monthChanges.map((change) => (
            <MonthChangeRow key={change.month} change={change} />
          ))}
        </ul>
      )}
    </section>
  )
}

/**
 * 한 달의 "이전 → 이후" 비교다. 넓은 화면은 좌우(이전 | → | 이후), 좁은 화면(375px)은 위아래로 쌓는다.
 * "이전/이후"는 위치나 색이 아니라 글자 머리표로 구분한다(낭독기도 그대로 읽는다). 이후 쪽만 테두리를 강조색으로 둬
 * 지금 기준이 어느 쪽인지 한눈에 보이게 하되, 강조는 보조 수단이다.
 */
function MonthChangeRow({ change }: { change: PlanMonthChange }) {
  // 이전 플랜에 없던 달(새로 생긴 셋째 달 등)인지. 이전 비용이 null이면 그렇다(계약 PlanMonthChange.previousCost)
  const isNewMonth = change.previousCost === null

  return (
    <li className="grid gap-3 rounded-lg border border-border bg-background p-3">
      <div className="flex items-center justify-between gap-2">
        <div className="flex flex-wrap items-center gap-2">
          <h4 className="text-base font-bold">{formatMonthLabel(change.month)}</h4>
          {/* 새로 생긴 달 표시: 달력+더하기 아이콘 + 글자. "바뀐 달"이 아니라 "처음 생긴 달"임을 접지 않고도 알 수 있게 달 이름 옆에 둔다 */}
          {isNewMonth && (
            <span className="inline-flex items-center gap-1 rounded-full border border-border bg-muted px-2 py-0.5 text-xs font-semibold whitespace-nowrap">
              <CalendarPlusIcon aria-hidden="true" className="size-3.5 shrink-0" />
              새로 생긴 달
            </span>
          )}
        </div>
        {/* 결과 화면(PlanMonthCard)의 "이번 달 결제 / 예상" 배지와 같은 모양: 실선+체크 / 점선+달력 + 글자 */}
        <span
          className={cn(
            'inline-flex shrink-0 items-center gap-1 rounded-full px-2 py-0.5 text-xs font-bold whitespace-nowrap',
            change.isConfirmed
              ? 'border-2 border-solid border-primary bg-primary text-primary-foreground'
              : 'border-2 border-dashed border-input text-foreground',
          )}
        >
          {change.isConfirmed ? (
            <CircleCheckIcon aria-hidden="true" className="size-3.5" />
          ) : (
            <CalendarClockIcon aria-hidden="true" className="size-3.5" />
          )}
          {/* 지난 기록이라 "이번 달"은 오늘 기준으로 오해될 수 있어 "결제 달"(그 재계산 때 곧 결제하던 달)로 쓴다 */}
          {change.isConfirmed ? '결제 달' : '예상'}
        </span>
      </div>

      <div className="grid items-center gap-2 sm:grid-cols-[1fr_auto_1fr]">
        <ProductSide
          label="이전"
          products={change.previousProducts}
          cost={change.previousCost}
          isCurrent={false}
        />
        {/* 화살표는 방향만 보여 주는 장식이다(뜻은 "이전/이후" 글자가 전한다). 좁은 화면은 아래, 넓은 화면은 오른쪽 */}
        <span aria-hidden="true" className="justify-self-center text-muted-foreground">
          <ArrowDownIcon className="size-5 sm:hidden" />
          <ArrowRightIcon className="size-5 max-sm:hidden" />
        </span>
        <ProductSide
          label="이후"
          products={change.currentProducts}
          cost={change.currentCost}
          isCurrent
        />
      </div>

      <DeltaLine label="이달 비용" previous={change.previousCost} current={change.currentCost} />
    </li>
  )
}

interface ProductSideProps {
  label: '이전' | '이후' // 어느 쪽인지 알리는 머리표
  products: PlanChangeProduct[] // 그 달 구독 상품(빈 배열 = 구독 안 함)
  cost: number | null // 그 달 비용(원). null = 그 플랜에 없던 달(이전 쪽에서만 생긴다)
  isCurrent: boolean // 이후(새 플랜) 쪽인지. 테두리·글자 굵기를 조금 강조한다
}

/**
 * 이전 또는 이후 한쪽의 상품 목록과 비용.
 * 비용이 null이면(이전 플랜에 없던 달) "해당 없음"을 보인다. "구독 안 함"(달 아이콘, 실선)과 헷갈리지 않게
 * 아이콘(달력+더하기)·글자·점선 테두리를 모두 다르게 했다. 금액 줄은 없으므로 빼서 "0원"으로 오해되지 않게 한다.
 */
function ProductSide({ label, products, cost, isCurrent }: ProductSideProps) {
  if (cost === null) {
    return (
      <div className="grid content-start gap-1 rounded-lg border border-dashed border-input px-3 py-2">
        <p className="text-xs font-semibold text-muted-foreground">{label}</p>
        <p className="flex items-center gap-1.5 font-semibold">
          <CalendarPlusIcon aria-hidden="true" className="size-4 shrink-0" />
          해당 없음
        </p>
        <p className="text-sm break-keep text-muted-foreground">이전 플랜에 없던 달이에요</p>
      </div>
    )
  }

  return (
    <div
      className={cn(
        'grid content-start gap-1 rounded-lg border px-3 py-2',
        isCurrent ? 'border-primary' : 'border-border',
      )}
    >
      <p className="text-xs font-semibold text-muted-foreground">{label}</p>
      {products.length === 0 ? (
        <p className="flex items-center gap-1.5 font-semibold">
          <MoonIcon aria-hidden="true" className="size-4 shrink-0" />
          구독 안 함
        </p>
      ) : (
        <ul className="grid gap-0.5">
          {products.map((product) => (
            <li key={product.productId} className={cn('break-keep', isCurrent && 'font-semibold')}>
              {/* 번들도 상품 이름에 구성 서비스가 들어 있어 이름만 보인다(구성 서비스 줄은 정보 과다라 뺐다, 2026-10-17 사용자 결정) */}
              {product.productName}
            </li>
          ))}
        </ul>
      )}
      <p className="text-sm text-muted-foreground tabular-nums">{formatWon(cost)}</p>
    </div>
  )
}

interface DeltaLineProps {
  label: string // 무엇의 변화인지(예: "이달 비용")
  previous: number | null // 이전 금액(원). null = 이전 플랜에 없던 달이라 비교할 금액이 없음
  current: number // 이후 금액(원)
  previousText?: string // 이전 금액 대신 보일 글자(예: 절감 없음)
  currentText?: string // 이후 금액 대신 보일 글자
}

/**
 * "이달 비용 13,500원 → 9,900원 · 3,600원 줄었어요" 한 줄이다.
 * 방향은 아이콘(내려가는/올라가는 선, 같음 기호)과 글자로 알린다. 좋고 나쁨을 빨강·초록으로 칠하지 않는다(planHistoryDisplay 주석).
 * 이전 금액이 없으면(새로 생긴 달) "늘었어요"로 쓰지 않고 새 금액만 보인다. 없던 달을 0원으로 보고 차이를 내면 비용이 늘어난 것처럼 읽히기 때문이다.
 */
function DeltaLine({ label, previous, current, previousText, currentText }: DeltaLineProps) {
  if (previous === null) {
    return (
      <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm tabular-nums">
        <span className="text-muted-foreground">{label}</span>
        <span className="font-bold">{currentText ?? formatWon(current)}</span>
        <span className="text-muted-foreground">(새로 생긴 달이라 비교하지 않아요)</span>
      </p>
    )
  }
  const delta = getAmountDelta(previous, current)
  const Icon =
    delta.direction === 'down'
      ? TrendingDownIcon
      : delta.direction === 'up'
        ? TrendingUpIcon
        : EqualIcon
  const deltaText =
    delta.direction === 'same'
      ? '같아요'
      : `${formatWon(delta.amount)} ${delta.direction === 'down' ? '줄었어요' : '늘었어요'}`

  return (
    <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm tabular-nums">
      <span className="text-muted-foreground">{label}</span>
      <span>
        {previousText ?? formatWon(previous)}
        {/* 화살표는 낭독기에서 "에서"로 읽히게 바꾼다 */}
        <span aria-hidden="true"> → </span>
        <span className="sr-only">에서 </span>
        <span className="font-bold">{currentText ?? formatWon(current)}</span>
      </span>
      <span className="inline-flex items-center gap-1 font-semibold">
        <Icon aria-hidden="true" className="size-4 shrink-0" />
        {deltaText}
      </span>
    </p>
  )
}

// 3. 작품 변화
function TitleChangeSection({
  titleChanges,
  serviceNames,
}: {
  titleChanges: PlanTitleChange[]
  serviceNames: Map<number, string>
}) {
  if (titleChanges.length === 0) return null
  return (
    <section className="grid gap-2">
      <SectionHeading>작품 변화</SectionHeading>
      <ul className="grid gap-2">
        {titleChanges.map((change) => (
          <TitleChangeRow
            key={`${change.changeType}-${change.title}-${change.seasonNumber ?? ''}`}
            change={change}
            serviceNames={serviceNames}
          />
        ))}
      </ul>
    </section>
  )
}

/**
 * 작품 한 개의 변화: [종류 칩] 작품 이름 / "10월 · 넷플릭스 → 12월 · 넷플릭스".
 * 종류 칩은 아이콘 + 글자이고, "제외"만 점선 테두리라 모양으로도 빠졌다는 점이 보인다(색 무관).
 */
function TitleChangeRow({
  change,
  serviceNames,
}: {
  change: PlanTitleChange
  serviceNames: Map<number, string>
}) {
  const { label, icon: Icon } = TITLE_CHANGE_DISPLAY[change.changeType]
  const isRemoved = change.changeType === 'REMOVED'

  return (
    <li className="grid gap-1 rounded-lg border border-border bg-background px-3 py-2.5">
      <p className="flex flex-wrap items-center gap-x-2 gap-y-1">
        <span
          className={cn(
            'inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs font-bold whitespace-nowrap',
            isRemoved ? 'border-dashed border-input' : 'border-solid border-border bg-muted',
          )}
        >
          <Icon aria-hidden="true" className="size-3.5 shrink-0" />
          {label}
        </span>
        <span className="font-semibold break-keep">{formatAssignmentTitle(change)}</span>
      </p>
      <p className="flex flex-wrap items-center gap-x-1.5 text-sm text-muted-foreground tabular-nums">
        <span>{formatPlacement(change.previous, serviceNames, '플랜에 없었음')}</span>
        <ArrowRightIcon aria-hidden="true" className="size-3.5 shrink-0" />
        <span className="sr-only">에서 </span>
        <span className="font-semibold text-foreground">
          {formatPlacement(change.current, serviceNames, '플랜에서 빠짐')}
        </span>
      </p>
    </li>
  )
}

// 작품의 자리를 "10월 · 넷플릭스"로 쓴다. 자리가 없으면(추가 전·제외 후) 대신할 글자를 쓴다
function formatPlacement(
  placement: PlanTitlePlacement | null,
  serviceNames: Map<number, string>,
  emptyText: string,
): string {
  if (placement === null) return emptyText
  return `${formatMonthLabel(placement.month)} · ${serviceNames.get(placement.ottServiceId) ?? '서비스'}`
}

/*
 * 4. 절감액 변화(삭제, 2026-10-17 사용자 결정). 두 플랜이 다루는 3개월이 한 달씩 달라 비교가 부정확해 화면에서 뺐다.
 * 참고용으로 코드를 남긴다(되살리려면 PiggyBankIcon import도 함께 되돌린다. 절감액 필드는 변경 내역 계약에 없다).
 *
 * // 0 이하는 아낀 것이 없으므로 결과 화면(PlanSummary)처럼 "절감 없음"으로 쓴다
 * function SavingsSection({ previous, current }: { previous: number; current: number }) {
 *   return (
 *     <section className="grid gap-1.5">
 *       <SectionHeading>절감액 변화</SectionHeading>
 *       <div className="flex items-start gap-2">
 *         <PiggyBankIcon aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
 *         <DeltaLine
 *           label="3개월 절감액"
 *           previous={previous}
 *           current={current}
 *           previousText={previous > 0 ? undefined : '절감 없음'}
 *           currentText={current > 0 ? undefined : '절감 없음'}
 *         />
 *       </div>
 *       <p className="text-sm break-keep text-muted-foreground">
 *         두 플랜이 다루는 3개월이 한 달씩 달라 비교는 참고용이에요. 확인된 범위의 데이터로 계산한 예상이에요.
 *       </p>
 *     </section>
 *   )
 * }
 */
