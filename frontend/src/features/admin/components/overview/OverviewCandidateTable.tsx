import { useId, useState } from 'react'
import { ChevronDownIcon, ChevronRightIcon, FileTextIcon, LoaderCircleIcon } from 'lucide-react'
import type { OverviewCandidate } from '@/api/generated/model'
import { Button } from '@/components/ui/button'
import { AdminStatusPill } from '@/features/admin/components/AdminStatusPill'
import { LlmWriteButton } from '@/features/admin/components/overview/LlmWriteButton'
import { MEDIA_TYPE_LABELS } from '@/features/admin/overviewRules'

// 후보 목록 위에 놓이는 "지금 만들 수 없는 이유" 문장의 id(행마다 있는 [번역 초안 만들기]가 aria-describedby로 가리킨다)
export const CANDIDATE_BLOCKED_REASON_ID = 'llm-candidate-blocked-reason'

interface CandidateRowProps {
  candidate: OverviewCandidate
  pendingTitleId: number | null // 지금 번역 중인 작품 ID. 없으면 null
  blockedReason: string | null // 만들 수 없는 이유(키 없음, 일괄 실행 중, 다른 작품 번역 중). 있으면 모든 행의 버튼이 비활성
  cooldownMessage: string | null // 이 작품이 방금 쿨다운(429)으로 거절됐을 때 버튼 옆에 보일 짧은 문장
  onCreate: (candidate: OverviewCandidate) => void
}

// 후보 한 줄. 영어 줄거리는 2줄만 보이고 [펼치기]로 전문을 본다(펼침 여부는 줄마다 자기 상태)
function CandidateRow({
  candidate,
  pendingTitleId,
  blockedReason,
  cooldownMessage,
  onCreate,
}: CandidateRowProps) {
  const [isExpanded, setIsExpanded] = useState(false)
  const overviewId = useId()
  const isThisPending = pendingTitleId === candidate.titleId

  return (
    <li className="grid gap-2 py-3 first:pt-0 last:pb-0 md:grid-cols-[minmax(0,1fr)_auto] md:items-start md:gap-4">
      <div className="grid min-w-0 gap-1.5">
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <span className="font-semibold break-keep">{candidate.title}</span>
          <span className="text-xs text-muted-foreground">
            {MEDIA_TYPE_LABELS[candidate.mediaType]} · #{candidate.titleId}
          </span>
          <span className="text-xs text-muted-foreground tabular-nums">
            찜 {candidate.wishlistCount.toLocaleString('ko-KR')}명
          </span>
          {candidate.hasDraft && (
            <AdminStatusPill icon={FileTextIcon} label="초안 있음" tone="outline" size="sm" />
          )}
        </div>
        <p
          id={overviewId}
          lang="en"
          className={
            isExpanded
              ? 'text-sm break-words whitespace-pre-wrap'
              : 'line-clamp-2 text-sm break-words text-muted-foreground'
          }
        >
          {candidate.overview}
        </p>
        <div>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            aria-expanded={isExpanded}
            aria-controls={overviewId}
            onClick={() => setIsExpanded((prev) => !prev)}
          >
            {isExpanded ? (
              <ChevronDownIcon aria-hidden="true" />
            ) : (
              <ChevronRightIcon aria-hidden="true" />
            )}
            {isExpanded ? '접기' : '펼치기'}
            <span className="sr-only"> 영어 줄거리 전문</span>
          </Button>
        </div>
      </div>
      <div className="grid justify-items-start gap-1 md:justify-items-end">
        <LlmWriteButton
          type="button"
          size="sm"
          variant={candidate.hasDraft ? 'outline' : 'default'}
          blockedReason={blockedReason}
          blockedReasonId={CANDIDATE_BLOCKED_REASON_ID}
          isBusy={isThisPending}
          title={candidate.hasDraft ? '기존 초안을 새 번역으로 바꿉니다' : undefined}
          aria-label={`${candidate.title} ${candidate.hasDraft ? '다시 만들기' : '번역 초안 만들기'}`}
          onClick={() => onCreate(candidate)}
        >
          {isThisPending ? (
            <>
              <LoaderCircleIcon aria-hidden="true" className="loading-spin" />
              번역하는 중… (최대 1분)
            </>
          ) : candidate.hasDraft ? (
            '다시 만들기'
          ) : (
            '번역 초안 만들기'
          )}
        </LlmWriteButton>
        {cooldownMessage && (
          <p role="status" className="text-xs text-muted-foreground">
            {cooldownMessage}
          </p>
        )}
      </div>
    </li>
  )
}

interface OverviewCandidateTableProps {
  candidates: OverviewCandidate[] // 이 쪽의 번역 후보들
  pendingTitleId: number | null // 지금 번역 중인 작품 ID
  blockedReason: string | null // 만들 수 없는 이유(있으면 목록 위에 글자로도 보인다)
  cooldownTitleId: number | null // 쿨다운 안내를 보일 작품 ID
  cooldownMessage: string // 쿨다운 안내 문장
  onCreate: (candidate: OverviewCandidate) => void
}

/**
 * 번역 후보 목록이다(찜 많은 순, 같으면 인기순). 줄마다 제목·구분·찜 수·영어 줄거리(펼치기)·초안 유무·[번역 초안 만들기].
 * 표 대신 줄 목록인 이유는 OverviewDraftTable과 같다(좁은 화면에서 가로 스크롤을 만들지 않기 위해).
 * 단건 생성은 Gemini를 부르는 동기 호출이라 최대 1분쯤 걸린다. 번역 중에는 모든 행의 버튼이 비활성이라
 * 다른 작품이나 일괄 번역과 겹쳐 호출 상한을 쓰지 않는다.
 */
export function OverviewCandidateTable({
  candidates,
  pendingTitleId,
  blockedReason,
  cooldownTitleId,
  cooldownMessage,
  onCreate,
}: OverviewCandidateTableProps) {
  return (
    <div className="grid gap-3">
      {blockedReason && (
        <p id={CANDIDATE_BLOCKED_REASON_ID} className="text-sm text-muted-foreground">
          {blockedReason}
        </p>
      )}
      <ul className="divide-y divide-border">
        {candidates.map((candidate) => (
          <CandidateRow
            key={candidate.titleId}
            candidate={candidate}
            pendingTitleId={pendingTitleId}
            blockedReason={blockedReason}
            cooldownMessage={cooldownTitleId === candidate.titleId ? cooldownMessage : null}
            onCreate={onCreate}
          />
        ))}
      </ul>
    </div>
  )
}
