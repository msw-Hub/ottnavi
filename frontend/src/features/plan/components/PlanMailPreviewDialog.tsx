import type { ReactNode, RefObject } from 'react'
import { CalendarIcon, InfoIcon, SaveIcon, type LucideIcon } from 'lucide-react'
import { isApiError } from '@/api/http'
import type { PlanMailPreviewMail, PlanMailPreviewType } from '@/api/generated/model'
import { CloseLabeledDialogContent } from '@/components/common/CloseLabeledDialogContent'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { LoadingState } from '@/components/common/LoadingState'
import { Button } from '@/components/ui/button'
import { Dialog, DialogClose, DialogDescription, DialogTitle } from '@/components/ui/dialog'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { usePlanMailPreview } from '@/features/plan/hooks/usePlanMailPreview'
import { injectBaseTarget } from '@/features/plan/mailHtml'
import { MAIL_PREVIEW_TYPE_LABELS } from '@/features/plan/planDisplay'

// 탭 글자 옆 아이콘(글자가 함께 있어 아이콘만으로 뜻을 전하지 않는다)
const MAIL_TYPE_ICONS: Record<PlanMailPreviewType, LucideIcon> = {
  SUBSCRIBE_GUIDE: SaveIcon,
  MONTHLY: CalendarIcon,
}

// 메일 본문 칸(iframe)의 접근 이름. 낭독기가 "프레임"만 읽지 않고 어떤 메일인지 알려 준다
const MAIL_FRAME_TITLES: Record<PlanMailPreviewType, string> = {
  SUBSCRIBE_GUIDE: '구독 안내 메일 본문 예시',
  MONTHLY: '월초 안내 메일 본문 예시',
}

interface MailFrameProps {
  mail: PlanMailPreviewMail // 보여 줄 메일 한 통(제목과 완성 HTML)
}

/**
 * 메일 한 통: 제목 줄 + 본문 칸(iframe).
 *
 * 본문은 서버가 만든 이메일용 HTML이라 우리 화면의 글자·색 규칙이 닿지 않게 iframe 안에서만 그린다.
 * sandbox에는 allow-popups(+escape)만 준다. 스크립트(allow-scripts)와 같은 출처(allow-same-origin)를 주면
 * 메일 HTML이 이 앱의 토큰·쿠키를 건드릴 수 있어 절대 허용하지 않는다. 링크는 새 탭으로만 열린다(injectBaseTarget).
 */
function MailFrame({ mail }: MailFrameProps) {
  return (
    <>
      <p className="flex gap-2 text-sm">
        <span className="shrink-0 text-muted-foreground">제목</span>
        <span className="min-w-0 font-semibold break-words">{mail.subject}</span>
      </p>
      <iframe
        title={MAIL_FRAME_TITLES[mail.type]}
        srcDoc={injectBaseTarget(mail.html)}
        sandbox="allow-popups allow-popups-to-escape-sandbox"
        referrerPolicy="no-referrer"
        className="min-h-60 w-full flex-1 rounded-lg border border-border bg-mail-canvas"
      />
    </>
  )
}

// 본문 칸 자리를 채우는 상태 화면(로딩·빈·오류)이 팝업 높이를 흔들지 않게 같은 칸에 담는다
function StateBox({ children }: { children: ReactNode }) {
  return <div className="min-h-0 flex-1 overflow-y-auto">{children}</div>
}

/**
 * 팝업 본문: 조회 상태(로딩 → 오류 → 빈 → 메일)를 가르고 메일은 1통이면 그대로, 2통이면 탭으로 보인다.
 * 팝업이 열릴 때만 마운트되므로 이 안의 조회 훅도 열릴 때만 요청한다.
 */
function PlanMailPreviewBody() {
  const preview = usePlanMailPreview()

  if (preview.isPending) {
    return (
      <StateBox>
        <LoadingState message="메일 예시를 만들고 있어요" />
      </StateBox>
    )
  }

  if (preview.isError) {
    // 403은 데모 계정이 아니라는 뜻이라 다시 시도해도 같다 → 다시 시도 버튼 없이 안내만
    if (isApiError(preview.error) && preview.error.status === 403) {
      return (
        <StateBox>
          <ErrorState title="메일 예시를 볼 수 없어요" error={preview.error} />
        </StateBox>
      )
    }
    return (
      <StateBox>
        <ErrorState
          title="메일 예시를 불러오지 못했어요"
          error={preview.error}
          onRetry={() => void preview.refetch()}
        />
      </StateBox>
    )
  }

  const { mails, noMailReasons } = preview.data

  // 저장한 플랜이 없어 만들 메일이 하나도 없다(NO_CURRENT_PLAN)
  if (mails.length === 0) {
    return (
      <StateBox>
        <EmptyState
          title="저장한 플랜이 없어 메일 예시를 만들 수 없어요"
          description="플랜을 저장하면 다시 볼 수 있어요."
        />
      </StateBox>
    )
  }

  // 메일이 1통이면 탭이 의미 없어 탭 없이 보인다. 구독 안내만 없는 경우(이번 달 새로 구독할 서비스 없음)에는 이유를 먼저 알린다
  if (mails.length === 1) {
    const [mail] = mails
    return (
      <div className="flex min-h-0 flex-1 flex-col gap-2">
        {noMailReasons.includes('NO_SUBSCRIBE_GUIDE') && (
          <p className="flex items-start gap-2 rounded-lg border border-info bg-info-bg px-3 py-2 text-sm text-info">
            <InfoIcon aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
            <span className="break-keep">
              이번 달에 새로 구독할 서비스가 없어서 &apos;저장하면 오는 메일&apos;은 만들어지지
              않았어요. 매월 1일에 오는 메일만 보여 드려요.
            </span>
          </p>
        )}
        <p className="text-xs font-semibold text-muted-foreground">
          {MAIL_PREVIEW_TYPE_LABELS[mail.type]}
        </p>
        <MailFrame mail={mail} />
      </div>
    )
  }

  // 2통: 탭. 방향키 이동·선택은 Radix Tabs 기본 동작이다
  return (
    <Tabs defaultValue={mails[0].type} className="min-h-0 flex-1 gap-3">
      {/* 이전 기본 크기(h-8)를 키워 터치하기 쉽게 한다(최소 44px). 긴 글자는 단어 단위로 줄바꿈한다 */}
      <TabsList className="group-data-horizontal/tabs:h-auto w-full">
        {mails.map((mail) => {
          const Icon = MAIL_TYPE_ICONS[mail.type]
          return (
            <TabsTrigger
              key={mail.type}
              value={mail.type}
              className="h-auto min-h-11 text-sm break-keep whitespace-normal"
            >
              <Icon aria-hidden="true" />
              {MAIL_PREVIEW_TYPE_LABELS[mail.type]}
            </TabsTrigger>
          )
        })}
      </TabsList>
      {mails.map((mail) => (
        <TabsContent
          key={mail.type}
          value={mail.type}
          className="flex min-h-0 flex-1 flex-col gap-2"
        >
          <MailFrame mail={mail} />
        </TabsContent>
      ))}
    </Tabs>
  )
}

interface PlanMailPreviewDialogProps {
  isOpen: boolean // 팝업이 열려 있는지
  onOpenChange: (isOpen: boolean) => void // 닫기(X·Esc·바깥 클릭·닫기 버튼) 요청이 올 때
  // 닫힌 뒤 포커스를 돌려받을 "알림 메일 예시 보기" 버튼.
  // 자동으로 열린 경우 저장 버튼이 이미 사라져 Radix 기본 복귀 대상이 없어서, 이 버튼으로 직접 옮긴다
  focusReturnRef: RefObject<HTMLButtonElement | null>
}

/**
 * 데모 계정이 플랜을 저장했을 때 보이는 "알림 메일 예시" 팝업이다(Task 110).
 * 실제 회원이 받는 구독 안내·월초 안내 메일을 서버가 만든 그대로 보여 준다. 데모 계정에는 실제로 메일을 보내지 않는다.
 *
 * - 크기: 모바일(sm 미만)은 찜 추가 팝업처럼 아래에서 올라오는 시트(높이 92dvh, 위쪽만 둥글게, 아래 안전 영역 여백),
 *   데스크톱은 가운데 카드. 팝업 자체는 스크롤하지 않고 메일 본문 칸(iframe) 안에서만 스크롤한다(아주 낮은 화면의 예비 스크롤만 둠).
 * - 제목 오른쪽 여백(pr-10)은 오른쪽 위 닫기 버튼과 글자가 겹치지 않게 한다.
 */
export function PlanMailPreviewDialog({
  isOpen,
  onOpenChange,
  focusReturnRef,
}: PlanMailPreviewDialogProps) {
  return (
    <Dialog open={isOpen} onOpenChange={onOpenChange}>
      <CloseLabeledDialogContent
        // 닫힌 뒤 "알림 메일 예시 보기" 버튼으로 포커스를 옮긴다. 버튼으로 연 경우에도 같은 버튼이라 결과가 같다.
        // 버튼이 없으면(화면이 바뀌어 사라짐) Radix 기본 동작에 맡긴다
        onCloseAutoFocus={(event) => {
          const target = focusReturnRef.current
          if (!target) return
          event.preventDefault()
          target.focus()
        }}
        className="top-auto bottom-0 left-0 flex h-[92dvh] max-w-none translate-x-0 translate-y-0 flex-col gap-3 overflow-y-auto rounded-b-none p-4 pb-[max(1rem,env(safe-area-inset-bottom))] data-open:slide-in-from-bottom-8 sm:top-1/2 sm:bottom-auto sm:left-1/2 sm:h-[min(85dvh,52rem)] sm:max-w-2xl sm:-translate-x-1/2 sm:-translate-y-1/2 sm:rounded-xl sm:p-5 sm:data-open:slide-in-from-bottom-0"
      >
        <div className="grid gap-1.5">
          <DialogTitle className="pr-10 text-lg leading-snug font-bold">알림 메일 예시</DialogTitle>
          <DialogDescription className="break-keep">
            알림을 켠 회원은 이 플랜을 저장하면 아래 메일을 받아요. 데모 계정에는 메일을 보내지 않고
            이 화면으로만 보여 드려요.
          </DialogDescription>
        </div>

        <PlanMailPreviewBody />

        <DialogClose asChild>
          <Button type="button" variant="outline" className="h-11 w-full sm:w-auto sm:self-end">
            닫기
          </Button>
        </DialogClose>
      </CloseLabeledDialogContent>
    </Dialog>
  )
}
