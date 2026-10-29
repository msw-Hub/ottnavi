import { useState } from 'react'
import { toast } from 'sonner'
import type { OverviewDraft } from '@/api/generated/model'
import { isApiError } from '@/api/http'
import {
  useEditOverviewDraft,
  useInvalidateOverviewQueries,
} from '@/features/admin/hooks/useAdminOverviewTranslate'
import { getErrorMessage } from '@/lib/errorMessages'

// 지금 열려 있는 수정 칸 하나의 상태
export interface DraftEditSession {
  draftId: string // 고치는 초안
  baseline: string // 비교 기준 번역문(처음 번역문, 저장하면 저장된 번역문으로 바뀐다). 입력값과 다르면 "고친 내용 있음"이다
  value: string // 입력 칸의 지금 글
  saved: OverviewDraft | null // 마지막 저장 응답(서버가 다시 검사한 새 등급이 들어 있다). 저장 전이면 null
}

/**
 * 번역 초안 수정 칸의 상태를 관리한다. 수정 칸은 화면에서 한 번에 하나만 열린다(사용자 결정).
 *
 * 왜 상태를 부모(초안 검토 상자)가 들고 있게 훅으로 뺐나: 탭 이동·쪽 넘김·다른 항목의 [수정]처럼
 * 수정 칸을 버리게 되는 동작이 여러 곳에 있고, 그때마다 "고친 내용을 저장하지 않고 닫을까요?"를 물어야 해서
 * 한 곳이 수정 상태를 알고 있어야 한다.
 *
 * 저장하면 항목을 그 자리에 그대로 두고(saved에 새 등급 보관) 결과 띠로 알린다. 닫을 때 한 번 저장한 적이 있으면
 * 목록·개수를 서버에서 다시 받아 맞는 탭으로 옮긴다(낙관적 갱신 없이 서버 값을 다시 받는다).
 */
export function useDraftEditSession() {
  const edit = useEditOverviewDraft()
  const invalidate = useInvalidateOverviewQueries()
  const [session, setSession] = useState<DraftEditSession | null>(null)
  const [errorText, setErrorText] = useState<string | null>(null) // 저장 실패 문장(400 등). 수정 칸 아래에 빨간 글자로 보인다

  const isDirty = session !== null && session.value !== session.baseline

  // 수정 칸을 연다(이미 열린 칸이 있으면 부르는 쪽이 먼저 close로 닫고 부른다)
  function start(draft: OverviewDraft) {
    edit.reset()
    setErrorText(null)
    setSession({
      draftId: draft.draftId,
      baseline: draft.translated,
      value: draft.translated,
      saved: null,
    })
  }

  function setValue(next: string) {
    setSession((prev) => (prev ? { ...prev, value: next } : prev))
  }

  // 수정 칸을 닫는다. 한 번이라도 저장했으면 등급이 바뀌었을 수 있어 목록·개수를 새로 불러온다
  function close() {
    if (session?.saved) void invalidate()
    edit.reset()
    setErrorText(null)
    setSession(null)
  }

  // [저장하고 다시 검사]: 서버에 저장하고 새 검사 결과를 받는다
  function save() {
    // 1. 연타 방지: 저장 중이거나 열린 칸이 없으면 보내지 않는다
    if (edit.isPending || !session) return
    const { draftId, value } = session
    setErrorText(null)
    // 2. 저장 요청
    edit.mutate(
      { draftId, translatedText: value },
      {
        // 3. 성공: 새 등급의 초안을 보관한다. 저장하는 동안 더 입력한 글이 있으면 그대로 두고 기준만 바꾼다
        onSuccess: (draft) =>
          setSession((prev) =>
            prev && prev.draftId === draft.draftId
              ? {
                  ...prev,
                  baseline: draft.translated,
                  value: prev.value === value ? draft.translated : prev.value,
                  saved: draft,
                }
              : prev,
          ),
        // 4. 실패: 이미 지워진 초안(404)은 안내하고 칸을 닫는다. 그 밖(400 검증 등)은 칸 아래에 문장으로 보인다
        onError: (error) => {
          if (isApiError(error) && error.errorCode === 'LLM_DRAFT_NOT_FOUND') {
            toast.info('이미 지워진 초안입니다. 목록을 새로 불러왔습니다.')
            edit.reset()
            setSession(null)
            void invalidate()
            return
          }
          setErrorText(isApiError(error) ? error.userMessage : getErrorMessage(null))
        },
      },
    )
  }

  // 목록의 초안을 화면에 보일 모양으로 바꾼다: 방금 저장한 초안이면 서버가 돌려준 새 값(등급 포함)을 보인다
  function withSaved(draft: OverviewDraft): OverviewDraft {
    return session?.draftId === draft.draftId && session.saved ? session.saved : draft
  }

  return {
    session,
    isDirty,
    isSaving: edit.isPending,
    errorText,
    start,
    setValue,
    close,
    save,
    withSaved,
  }
}
