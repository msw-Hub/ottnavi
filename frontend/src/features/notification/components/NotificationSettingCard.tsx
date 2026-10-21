import { LoaderCircleIcon, MailIcon, TriangleAlertIcon } from 'lucide-react'
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
            {update.isPending ? '저장하는 중' : isOn ? '지금 켜져 있어요' : '지금 꺼져 있어요'}
          </p>
        </div>
        <Switch
          id="notification-switch"
          checked={isOn}
          disabled={update.isPending}
          onCheckedChange={(next) => update.mutate(next)}
          className="mt-1 scale-150 origin-right"
        />
      </div>

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
        <p>{message}</p>
        {isOnboardingRequired && (
          <Link
            to="/settings"
            className="mt-1 inline-flex min-h-11 items-center rounded-md font-semibold underline underline-offset-3 focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
          >
            내 설정 입력하러 가기
          </Link>
        )}
      </AlertDescription>
    </Alert>
  )
}
