import { ArrowLeftIcon, InfoIcon } from 'lucide-react'
import { Link } from 'react-router'
import { NotificationHistory } from '@/features/notification/components/NotificationHistory'
import { NotificationSettingCard } from '@/features/notification/components/NotificationSettingCard'

/**
 * SCR-12 알림 설정·발송 이력 화면의 본문이다. pages/NotificationSettingsPage는 이 컴포넌트를 연결만 한다(pages는 얇게, frontend.md).
 *
 * 위에서 아래 순서와 이유:
 * 1. 제목·한 줄 설명: 이 화면에서 무엇을 정하는지 먼저 알린다.
 * 2. 설정 카드: 켜기/끄기와 받는 이메일. 가장 자주 하는 일이라 맨 위에 둔다.
 * 3. 어떤 메일이 가는지 안내: 동의(켜기) 없이는 보내지 않는다는 점과 "확인된 범위" 표현을 알려 켜기 전에 안심하게 한다.
 * 4. 발송 이력: 설정과 별개로 지금까지의 기록을 보여 "메일이 왜 안 왔지?"를 화면에서 확인하게 한다.
 *
 * 제목은 데이터가 오기 전에도 보인다(상태 화면은 각 영역 안에서만 바뀐다). 로딩·오류·빈 상태는 설정 카드와 이력이 각자 맡는다.
 */
export function NotificationScreen() {
  return (
    <section className="mx-auto flex w-full max-w-2xl flex-col gap-5">
      <div className="grid gap-2">
        {/* 설정 화면(SCR-07)에서 들어오는 하위 화면이라 되돌아가는 길을 제목 위에 둔다 */}
        <Link
          to="/settings"
          className="inline-flex min-h-11 w-fit items-center gap-1.5 rounded-md text-sm font-semibold text-primary focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
        >
          <ArrowLeftIcon aria-hidden="true" className="size-4" />내 설정
        </Link>
        <h1 className="text-2xl font-semibold">알림 설정</h1>
        <p className="break-keep text-muted-foreground">
          알림을 켜면 새 데이터로 다시 계산한 결과를 이메일로 알려 드려요.
        </p>
      </div>

      <NotificationSettingCard />

      <aside
        aria-label="알림 안내"
        className="grid gap-2 rounded-xl border border-info bg-info-bg px-4 py-3 text-info"
      >
        <p className="flex items-start gap-2 font-semibold break-keep">
          <InfoIcon aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
          알림을 켜기 전에는 어떤 메일도 보내지 않아요
        </p>
        <ul className="grid list-disc gap-1 pl-9 text-sm break-keep">
          {/* 이전: "가입 안내: 플랜을 처음 저장한 뒤 구독 일정을 정리해 알려 드려요." (회원가입으로 오해돼 이름을 바꿨다, Task 109) */}
          <li>
            구독 안내: 플랜을 처음 저장한 뒤 언제 어떤 서비스를 구독하면 되는지 정리해 알려 드려요.
          </li>
          <li>월초 안내: 매월 새 데이터로 다시 계산한 뒤 이번 달 구독 계획을 알려 드려요.</li>
          <li>
            플랜 변경 알림: 이번 달 플랜에 있던 작품이 빠지는 등 바뀐 점이 있을 때 알려 드려요.
          </li>
        </ul>
        <p className="text-sm break-keep">
          메일의 제공 여부와 요금은 확인된 범위이며, 실제와 다를 수 있어요. 언제든 여기서 끌 수
          있어요.
        </p>
      </aside>

      <section aria-labelledby="notification-history-title" className="grid gap-3">
        <h2 id="notification-history-title" className="text-xl font-semibold">
          발송 이력
        </h2>
        <NotificationHistory />
      </section>
    </section>
  )
}
