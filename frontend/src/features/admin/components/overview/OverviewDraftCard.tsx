import type { OverviewDraft } from '@/api/generated/model'
import { DraftGradePill } from '@/features/admin/components/overview/DraftGradePill'
import { GuardFailureTags } from '@/features/admin/components/overview/GuardFailureTags'
import { LlmWriteButton } from '@/features/admin/components/overview/LlmWriteButton'
import { MEDIA_TYPE_LABELS, getDraftGrade } from '@/features/admin/overviewRules'
import { formatDateTime } from '@/lib/format'

interface OverviewDraftSidesProps {
  draft: OverviewDraft // 원문과 번역문을 나란히 보일 초안
  idPrefix: string // 칸 제목 id의 앞부분(같은 화면에 초안이 여럿이라 겹치지 않게 한다)
}

/**
 * 영어 원문과 한국어 번역 초안을 나란히 보인다(md 이상은 2칸, 좁은 화면은 위아래로 쌓는다).
 * lang 속성을 칸마다 달아 낭독기가 영어·한국어 발음을 바꿔 읽게 하고, 긴 줄거리도 줄바꿈되게 한다(가로 스크롤 금지).
 */
export function OverviewDraftSides({ draft, idPrefix }: OverviewDraftSidesProps) {
  return (
    <div className="grid gap-3 md:grid-cols-2">
      <div className="grid content-start gap-1">
        <h4 id={`${idPrefix}-original`} className="text-xs font-semibold text-muted-foreground">
          영어 원문
        </h4>
        <p
          lang="en"
          aria-labelledby={`${idPrefix}-original`}
          className="text-sm leading-relaxed break-words whitespace-pre-wrap"
        >
          {draft.original}
        </p>
      </div>
      <div className="grid content-start gap-1">
        <h4 id={`${idPrefix}-translated`} className="text-xs font-semibold text-muted-foreground">
          한국어 번역 초안
        </h4>
        <p
          lang="ko"
          aria-labelledby={`${idPrefix}-translated`}
          className="text-sm leading-relaxed break-words whitespace-pre-wrap"
        >
          {draft.translated}
        </p>
      </div>
    </div>
  )
}

interface OverviewDraftCardProps {
  draft: OverviewDraft // 표본 한 편
  isDeleting: boolean // 이 초안을 삭제하는 중인지(버튼 비활성)
  onDelete: (draft: OverviewDraft) => void // [초안 삭제]를 눌렀다(확인 없이 바로 삭제한다)
}

/**
 * 성공 탭의 "무작위 표본" 카드 한 장이다: 제목·구분·검사 결과 알약·만든 시각과 모델, 원문/번역문 나란히, [초안 삭제].
 * 표본은 성공 초안에서만 뽑히지만, 알약을 항상 보여 어떤 검사 결과인지 글자와 모양으로 확인할 수 있게 한다.
 */
export function OverviewDraftCard({ draft, isDeleting, onDelete }: OverviewDraftCardProps) {
  return (
    <article
      aria-labelledby={`sample-${draft.draftId}-title`}
      className="grid gap-3 rounded-lg border border-border bg-background p-3"
    >
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="grid min-w-0 gap-1">
          <h3 id={`sample-${draft.draftId}-title`} className="text-base font-semibold break-keep">
            {draft.title}
            <span className="ml-2 text-xs font-normal text-muted-foreground">
              {MEDIA_TYPE_LABELS[draft.mediaType]} · #{draft.titleId}
            </span>
          </h3>
          <p className="text-xs text-muted-foreground">
            만든 시각 {formatDateTime(draft.createdAt) ?? '-'} · 모델 {draft.model}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <DraftGradePill grade={getDraftGrade(draft)} />
          <LlmWriteButton
            type="button"
            variant="outline"
            size="sm"
            isBusy={isDeleting}
            onClick={() => onDelete(draft)}
          >
            초안 삭제
          </LlmWriteButton>
        </div>
      </div>
      {!draft.isGuardPassed && <GuardFailureTags codes={draft.guardFailures} />}
      <OverviewDraftSides draft={draft} idPrefix={`sample-${draft.draftId}`} />
    </article>
  )
}
