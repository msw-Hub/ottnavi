import { useState } from 'react'
import { TriangleAlertIcon } from 'lucide-react'
import { cn } from 'cn'
import { toast } from 'sonner'
import { isApiError } from '@/api/http'
import { DemoLockedButton } from '@/components/common/DemoLockedButton'
import { DEMO_READONLY_NOTE_ID } from '@/components/common/DemoReadOnlyHint'
import { Label } from '@/components/ui/label'
import { TableCell, TableRow } from '@/components/ui/table'
import { MappingStateBadge } from '@/features/admin/components/MappingStateBadge'
// 이전: import { PhaseLaterTag } from '@/features/admin/components/PhaseLaterTag' (AD 선택 태그를 뺐다)
import { useSaveProviderMapping } from '@/features/admin/hooks/useAdminProviders'
import type { AdminProvider, ProviderTier } from '@/api/generated/model'
import {
  buildSaveRequest,
  CHOICE_IGNORED,
  CHOICE_UNMAPPED,
  isMappingChanged,
  isServiceChoice,
  toChoiceValue,
} from '@/features/admin/providerMapping'
import { getErrorMessage } from '@/lib/errorMessages'
import { formatDateTime } from '@/lib/format'
import { josa, withJosa } from '@/lib/josa'

// select 안의 서비스 선택지
export interface ServiceChoice {
  ottServiceId: number // 서비스 ID
  name: string // 서비스 이름. 이름을 못 불러왔으면 "서비스 N"
}

interface ProviderMappingRowProps {
  provider: AdminProvider // 한 행(TMDB 제공처 하나)
  services: ServiceChoice[] // 매핑 서비스 select의 서비스 선택지
  variant: 'table' | 'card' // 넓은 화면의 표 행(<tr>) / 좁은 화면의 카드(<li>)
}

// select에 쓰는 공통 모양(shadcn Input과 같은 테두리·포커스)
const SELECT_CLASS =
  'h-8 w-full rounded-lg border border-input bg-background px-2 text-sm text-foreground outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-50'

/**
 * 제공처 한 곳의 매핑 행이다(SCR-15). 같은 내용을 표 행과 카드 두 모양으로 그린다(variant).
 *
 * 행마다 입력값(서비스 선택, 등급)을 이 컴포넌트가 들고 있다. 서버에 저장된 값(provider)과 다를 때만 [저장]이 켜지고
 * "저장 안 됨" 글자가 보인다. 저장에 성공하면 목록을 다시 불러오고, 부모가 key에 저장된 값을 넣어 두었기 때문에
 * 이 컴포넌트가 새로 만들어져 입력값이 저장된 값으로 맞춰진다(상태를 prop에서 다시 복사하는 effect가 필요 없다).
 *
 * 실패하면 사용자가 고른 값은 그대로 두고 행 아래에 오류 한 줄(아이콘 + 글자)을 보인다. 다시 눌러 재시도할 수 있게 하려는 것이다.
 * 등급은 서비스에 연결했을 때만 의미가 있어서, "선택 안 함"·"관련 없음"이면 비활성이고 "—"로 보인다.
 */
export function ProviderMappingRow({ provider, services, variant }: ProviderMappingRowProps) {
  const save = useSaveProviderMapping()
  const [choice, setChoice] = useState(() => toChoiceValue(provider))
  const [tier, setTier] = useState<ProviderTier>(provider.tier)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const isChanged = isMappingChanged(provider, choice, tier)
  const canEditTier = isServiceChoice(choice)
  const idBase = `provider-${variant}-${provider.providerId}`

  // 선택지 이름을 찾는다(저장 성공 토스트에 쓴다)
  const choiceLabel = (value: string) => {
    if (value === CHOICE_UNMAPPED) return '미매핑'
    if (value === CHOICE_IGNORED) return '관련 없음'
    return services.find((s) => String(s.ottServiceId) === value)?.name ?? `서비스 ${value}`
  }

  function handleSave() {
    setErrorMessage(null)
    const before = choiceLabel(toChoiceValue(provider))
    const after = choiceLabel(choice)
    save.mutate(
      { providerId: provider.providerId, ...buildSaveRequest(choice, tier) },
      {
        onSuccess: () =>
          toast.success(
            `${withJosa(provider.tmdbName, '을/를')} '${before}'에서 '${after}'${josa(after, '으로/로')} 연결했습니다.`,
          ),
        onError: (error) =>
          setErrorMessage(isApiError(error) ? error.userMessage : getErrorMessage(null)),
      },
    )
  }

  // 서비스 select. 보이는 라벨은 카드에만 있고, 이름은 두 모양 모두 "{원본 라벨} 매핑 서비스"다(보이는 글자 "매핑 서비스"를 포함한다)
  const serviceSelect = (
    <select
      id={`${idBase}-service`}
      aria-label={`${provider.tmdbName} 매핑 서비스`}
      value={choice}
      onChange={(event) => setChoice(event.target.value)}
      disabled={save.isPending}
      className={SELECT_CLASS}
    >
      <option value={CHOICE_UNMAPPED}>선택 안 함(미매핑)</option>
      {services.map((service) => (
        <option key={service.ottServiceId} value={String(service.ottServiceId)}>
          {service.name}
        </option>
      ))}
      <option value={CHOICE_IGNORED}>관련 없음(쓰지 않음)</option>
    </select>
  )

  const tierControl = canEditTier ? (
    <div className="grid gap-1">
      <select
        id={`${idBase}-tier`}
        aria-label={`${provider.tmdbName} 등급`}
        value={tier}
        onChange={(event) => setTier(event.target.value as ProviderTier)}
        disabled={save.isPending}
        className={SELECT_CLASS}
      >
        <option value="STANDARD">STANDARD(광고 없음)</option>
        {/* 이전: "AD(광고형, 2단계부터 계산)" 문구와 AD 선택 시 <PhaseLaterTag className="w-fit" /> 태그가 있었다(광고형 판정은 이미 쓰인다) */}
        <option value="AD">AD(광고형)</option>
      </select>
    </div>
  ) : (
    // aria-label은 역할 없는 span에는 낭독기가 읽지 않을 수 있어, 눈에는 "—"만 보이고 낭독기에는 문장이 읽히게 나눈다
    <span className="text-muted-foreground">
      <span aria-hidden="true">—</span>
      <span className="sr-only">{provider.tmdbName} 등급: 해당 없음</span>
    </span>
  )

  // 데모 계정이면 값을 바꿔 [저장]이 켜져도 눌러서 저장되지 않는다(Task 109). 이유는 화면 위 조회 전용 안내가 설명한다.
  // 바꾼 값이 없을 때는 데모 여부와 상관없이 원래처럼 꺼져 있다(disabled)
  const saveButton = (
    <DemoLockedButton
      type="button"
      size="sm"
      disabled={!isChanged || save.isPending}
      onClick={handleSave}
      lockedDescriptionId={DEMO_READONLY_NOTE_ID}
      className={variant === 'card' ? 'w-full' : undefined}
    >
      {save.isPending ? '저장 중…' : '저장'}
      <span className="sr-only"> ({provider.tmdbName})</span>
    </DemoLockedButton>
  )

  const unsavedNote = isChanged && (
    <span className="text-xs font-semibold text-info">저장 안 됨</span>
  )

  const errorLine = errorMessage && (
    <p role="alert" className="flex items-center gap-1.5 text-sm text-destructive">
      <TriangleAlertIcon aria-hidden="true" className="size-4 shrink-0" />
      {errorMessage}
    </p>
  )

  // 미매핑 행 강조: 왼쪽 굵은 막대(색). "미매핑" 글자 배지가 항상 함께 있어 색만으로 전달하지 않는다
  const isUnmapped = provider.mappingState === 'UNMAPPED'

  if (variant === 'table') {
    return (
      <>
        <TableRow
          // 왼쪽 막대를 border-l이 아니라 안쪽 그림자로 그린다: 표 마지막 행은 border-0이 적용돼 border-l 막대가 사라졌었다(마지막 미매핑 행만 막대 없음)
          className={cn(isUnmapped && 'shadow-[inset_4px_0_0_0_var(--attention-border)]')}
        >
          <TableCell>
            <div className="grid justify-items-start gap-1">
              <MappingStateBadge state={provider.mappingState} />
              {unsavedNote}
            </div>
          </TableCell>
          <TableCell className="tabular-nums">{provider.providerId}</TableCell>
          <TableCell>{provider.tmdbName}</TableCell>
          <TableCell className="min-w-44">{serviceSelect}</TableCell>
          <TableCell className="min-w-44">{tierControl}</TableCell>
          <TableCell className="tabular-nums">
            {formatDateTime(provider.firstSeenAt) ?? '-'}
          </TableCell>
          <TableCell className="text-right">{saveButton}</TableCell>
        </TableRow>
        {errorLine && (
          <TableRow data-static-row className="hover:bg-transparent">
            <TableCell colSpan={7}>{errorLine}</TableCell>
          </TableRow>
        )}
      </>
    )
  }

  return (
    <li
      className={cn(
        'grid gap-3 rounded-xl border border-l-4 border-border bg-card p-4',
        isUnmapped && 'border-l-attention-border',
      )}
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="font-semibold">{provider.tmdbName}</p>
        <MappingStateBadge state={provider.mappingState} />
      </div>
      <p className="text-sm text-muted-foreground">
        provider_id <span className="tabular-nums">{provider.providerId}</span> · 최초 발견{' '}
        <span className="tabular-nums">{formatDateTime(provider.firstSeenAt) ?? '-'}</span>
      </p>
      <div className="grid gap-1.5">
        <Label htmlFor={`${idBase}-service`}>매핑 서비스</Label>
        {serviceSelect}
      </div>
      <div className="grid gap-1.5">
        {canEditTier ? (
          <Label htmlFor={`${idBase}-tier`}>등급</Label>
        ) : (
          <span className="text-sm font-medium">등급</span>
        )}
        {tierControl}
      </div>
      {unsavedNote}
      {saveButton}
      {errorLine}
    </li>
  )
}
