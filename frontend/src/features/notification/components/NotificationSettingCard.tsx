import { LoaderCircleIcon, LockIcon, MailIcon, TriangleAlertIcon } from 'lucide-react'
import { Link } from 'react-router'
import { isApiError } from '@/api/http'
import { ErrorState } from '@/components/common/ErrorState'
import { LoadingState } from '@/components/common/LoadingState'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import {
  useNotificationSetting,
  useUpdateNotificationSetting,
} from '@/features/notification/hooks/useNotificationSetting'
import { useIsDemo } from '@/hooks/useIsDemo'

// 데모 계정에게 알림을 꺼 둔 이유 문장의 id(스위치가 aria-describedby로 가리킨다)
const DEMO_NOTE_ID = 'notification-demo-note'

/**
 * 알림 켜기/끄기 스위치와 수신 이메일을 보여 주는 카드다(SCR-12, FR-16).
 *
 * 스위치 값(isOn)을 따로 state로 두지 않고 계산하는 이유(frontend.md "파생 가능한 값은 계산"):
 * - 저장 중이면 사용자가 누른 값(update.variables)을 먼저 보여 반응이 즉시 느껴지게 하고,
 * - 그렇지 않으면 서버에 저장된 값(setting.data)을 따른다.
 * 저장이 실패하면 pending이 끝나 서버 값으로 자동으로 되돌아가므로 "실패 시 되돌리기"를 따로 코딩하지 않아도 된다.
 *
 * 저장 중에는 스위치를 잠가(disabled) 연타로 요청이 겹치지 않게 한다. 오류는 alert 영역에 글자로 알리고(409는 설정 화면 링크 포함),
 * 알림이 켜졌는지는 스위치 색이 아니라 "켜짐/꺼짐" 글자로도 보인다.
 */
export function NotificationSettingCard() {
  const setting = useNotificationSetting()
  const update = useUpdateNotificationSetting()
  const isDemo = useIsDemo() // 데모 계정이면 스위치를 잠그고 이유를 안내한다(Task 109)

  // 1. 불러오는 중 / 실패는 카드 자리에서 상태 화면으로 보인다
  if (setting.isPending) return <LoadingState message="알림 설정을 불러오고 있어요" />
  if (setting.isError) {
    return (
      <ErrorState
        error={setting.error}
        title="알림 설정을 불러오지 못했어요"
        onRetry={() => void setting.refetch()}
      />
    )
  }

  // 2. 화면에 보일 스위치 값: 저장 중이면 누른 값, 아니면 서버 값
  const isOn = update.isPending ? update.variables : setting.data.isNotificationEnabled

  return (
    <div className="grid gap-4 rounded-xl border border-border bg-card p-4">
      <div className="flex items-start justify-between gap-4">
        <div className="grid gap-1">
          <Label htmlFor="notification-switch" className="text-base font-semibold">
            이메일 알림 받기
          </Label>
          {/* 상태를 색이 아니라 글자로도 알린다. 저장 중에는 진행 중임을 글자와 회전 아이콘으로 알린다 */}
          <p className="flex items-center gap-1.5 text-sm text-muted-foreground" aria-live="polite">
            {update.isPending && (
              <LoaderCircleIcon aria-hidden="true" className="size-4 motion-safe:animate-spin" />
            )}
            {isDemo
              ? '지금 꺼져 있어요 · 데모 계정은 바꿀 수 없어요'
              : update.isPending
                ? '저장하는 중'
                : isOn
                  ? '지금 켜져 있어요'
                  : '지금 꺼져 있어요'}
          </p>
        </div>
        {/* 데모 계정이면 스위치를 꺼 둔다(서버 값도 늘 꺼짐, 변경은 서버가 403). 이유는 아래 안내 문장이 설명한다(aria-describedby) */}
        <Switch
          id="notification-switch"
          checked={isOn}
          disabled={update.isPending || isDemo}
          aria-describedby={isDemo ? DEMO_NOTE_ID : undefined}
          onCheckedChange={(next) => update.mutate(next)}
          // ring-offset: 켜짐 상태의 스위치 바탕(--primary)과 포커스 테두리(--ring)가 같은 하늘색이라 붙어 있으면 테두리가 안 보인다.
          // 카드 바탕색으로 2px 틈을 두어 켜짐·꺼짐 모두에서 키보드 포커스가 보이게 한다(WCAG 2.4.7)
          className="mt-1 origin-right scale-150 focus-visible:ring-offset-2 focus-visible:ring-offset-card"
        />
      </div>

      {/* 데모 계정 안내(Task 109). 공용 계정이라 알림을 꺼 둔 이유를 스위치 바로 아래(경계선 위)에 둔다 */}
      {isDemo && (
        <p
          id={DEMO_NOTE_ID}
          className="flex items-start gap-1.5 text-sm break-keep text-muted-foreground"
        >
          <LockIcon aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
          데모 계정은 알림 메일을 받지 않아요. 공용 계정이라 메일이 엉뚱한 사람에게 가거나 쌓이지
          않도록 알림을 꺼 두었어요.
        </p>
      )}

      <div className="grid gap-1 border-t border-border pt-3">
        <p className="flex items-center gap-1.5 text-sm text-muted-foreground">
          <MailIcon aria-hidden="true" className="size-4" />
          받는 이메일
        </p>
        <p className="font-semibold break-all">{setting.data.email}</p>
        <p className="text-sm break-keep text-muted-foreground">
          로그인한 계정의 이메일이에요. 여기서 따로 바꿀 수 없어요.
        </p>
      </div>

      {update.isError && <UpdateErrorAlert error={update.error} />}
    </div>
  )
}

/** 저장 실패 안내다. 내 설정을 아직 저장하지 않은 계정(409 ONBOARDING_REQUIRED)에는 설정 화면으로 가는 링크를 함께 둔다. */
function UpdateErrorAlert({ error }: { error: unknown }) {
  const isOnboardingRequired = isApiError(error) && error.errorCode === 'ONBOARDING_REQUIRED'
  const message = isApiError(error) ? error.userMessage : '잠시 후 다시 시도해 주세요.'

  return (
    <Alert variant="destructive">
      <TriangleAlertIcon aria-hidden="true" />
      <AlertTitle>알림 설정을 바꾸지 못했어요</AlertTitle>
      <AlertDescription className="text-destructive/90">
        {/* mb-0!: shadcn AlertDescription이 마지막이 아닌 문단에 붙이는 아래 여백(16px)을 없애 링크가 안내 문장 바로 밑에 붙게 한다.
            원본(components/ui/alert.tsx)은 고치지 않고 이 자리에서만 덮어쓴다. 링크가 없으면 이 문단이 마지막이라 원래도 여백이 없다 */}
        <p className="mb-0!">{message}</p>
        {isOnboardingRequired && (
          <Link
            to="/settings"
            // -mt-2: 누르는 높이 44px(min-h-11)는 지키면서 글자 위 빈 칸 때문에 링크가 안내 문장과 떨어져 보이지 않게 당긴다. 이전: mt-1
            className="-mt-2 inline-flex min-h-11 items-center rounded-md font-semibold underline underline-offset-3 focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
          >
            내 설정 입력하러 가기
          </Link>
        )}
      </AlertDescription>
    </Alert>
  )
}
