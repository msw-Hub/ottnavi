import { useState, type FormEvent } from 'react'
import { ChevronDownIcon, ChevronRightIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { LlmWriteButton } from '@/features/admin/components/overview/LlmWriteButton'

interface RevertByTitleIdFormProps {
  onRequestRevert: (titleId: number) => void // 입력한 작품 번호로 되돌리기 확인 대화상자를 연다
}

/**
 * "작품 번호로 영어 줄거리 되돌리기" 접힌 상자다. 반영 결과 화면은 새로고침하면 사라지므로,
 * 나중에 잘못된 번역을 발견했을 때 작품 번호(titleId)만 알면 되돌릴 수 있게 한다(계약 변경 없이 같은 API를 쓴다).
 * 이 상자는 번호만 확인하고, 실제 되돌리기는 같은 확인 대화상자(RevertOverviewDialog)에서 한다.
 */
export function RevertByTitleIdForm({ onRequestRevert }: RevertByTitleIdFormProps) {
  const [isOpen, setIsOpen] = useState(false)
  const [titleIdText, setTitleIdText] = useState('')
  const [errorText, setErrorText] = useState<string | null>(null)

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const titleId = Number(titleIdText)
    if (titleIdText.trim() === '' || !Number.isInteger(titleId) || titleId < 1) {
      setErrorText('작품 번호를 숫자로 입력해 주세요.')
      return
    }
    setErrorText(null)
    onRequestRevert(titleId)
  }

  return (
    <section
      aria-labelledby="llm-revert-heading"
      className="rounded-xl border border-border bg-card p-4"
    >
      <h2 id="llm-revert-heading" className="text-lg font-semibold">
        <Button
          type="button"
          variant="ghost"
          aria-expanded={isOpen}
          aria-controls="llm-revert-panel"
          onClick={() => setIsOpen((prev) => !prev)}
          className="-ml-2 h-auto px-2 py-1 text-lg font-semibold"
        >
          {isOpen ? (
            <ChevronDownIcon aria-hidden="true" />
          ) : (
            <ChevronRightIcon aria-hidden="true" />
          )}
          작품 번호로 영어 줄거리 되돌리기
        </Button>
      </h2>
      {isOpen && (
        <form id="llm-revert-panel" onSubmit={handleSubmit} noValidate className="mt-3 grid gap-3">
          <div className="grid gap-1.5 sm:max-w-xs">
            <Label htmlFor="llm-revert-title-id">작품 번호(titleId)</Label>
            <Input
              id="llm-revert-title-id"
              type="number"
              inputMode="numeric"
              min={1}
              value={titleIdText}
              onChange={(event) => setTitleIdText(event.target.value)}
              aria-invalid={errorText ? true : undefined}
              aria-describedby={
                errorText
                  ? 'llm-revert-title-id-help llm-revert-title-id-error'
                  : 'llm-revert-title-id-help'
              }
            />
            <p id="llm-revert-title-id-help" className="text-xs text-muted-foreground">
              반영 결과에 보이는 # 뒤의 번호입니다. 이 화면에서 반영한 번역만 되돌릴 수 있습니다.
              TMDB가 준 한국어 줄거리는 되돌릴 수 없습니다.
            </p>
            {errorText && (
              <p id="llm-revert-title-id-error" className="text-sm text-destructive">
                {errorText}
              </p>
            )}
          </div>
          <div>
            <LlmWriteButton type="submit" variant="outline">
              영어로 되돌리기
            </LlmWriteButton>
          </div>
        </form>
      )}
    </section>
  )
}
