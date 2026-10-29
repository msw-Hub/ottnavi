import { BellIcon, ChevronRightIcon, XIcon } from 'lucide-react'
import { Link } from 'react-router'

// 이전: 띠형과 카드형 두 모양이 있었고 layout prop으로 골랐다(사용자가 띠형을 골라 카드형은 지웠다)

interface NotificationNudgeProps {
  // "닫기"를 눌렀을 때. 넘기지 않으면 닫기 버튼을 그리지 않는다.
  // 화면 상태(useState)로만 숨기고 서버·저장소에는 남기지 않는다. 이 안내는 저장 직후에만 보이므로 다음 저장 때 다시 보여도 된다
  onDismiss?: () => void
}

// 알림 설정 화면 경로. 머리글·내 설정의 "알림 설정" 링크와 같은 곳이다
const NOTIFICATION_SETTINGS_PATH = '/settings/notifications'

/**
 * 플랜을 저장한 직후 "알림이 꺼져 있으면 켜 보라"고 권하는 띠형 안내다(알림 기본 꺼짐 + 안내 방침).
 *
 * - 화면 전용 컴포넌트라 데이터를 읽지 않는다. 언제 보일지(저장 직후 && 알림 꺼짐 && 데모 아님)는 부르는 쪽(PlanScreen)이
 *   useNotificationNudge 훅으로 정한다.
 * - 모양: 넓은 화면(640px 이상)은 [아이콘 문장 ··· 알림 켜기 X] 한 줄, 좁은 화면은 문장이 폭을 다 쓰고 링크·닫기가 아랫줄 오른쪽으로 내려간다.
 *   (처음엔 375px에서도 한 줄에 넣었더니 문장이 좁은 칸에 갇혀 4줄로 꺾였다 → 문장에 최소 폭을 줘 줄을 바꾸게 했다)
 * - 색: 경고가 아니라 참고 안내라 info 토큰(파랑 계열)을 쓴다. 다른 안내 띠(재계산·"저장하면 바뀌어요")와 같은 색이라
 *   "새로 생긴 경고"로 오해하지 않는다. 색만으로 뜻을 전하지 않게 벨 아이콘 + 문장을 함께 둔다.
 * - 낭독: role="status"·aria-live를 주지 않는다. 같은 순간에 "플랜을 저장했어요" 토스트와 저장 완료 안내(role="status")가
 *   이미 읽히므로, 여기까지 살아 있는 영역으로 만들면 세 문장이 겹쳐 읽힌다. 평범한 글로 두면 낭독기가 화면을 읽어 내려갈 때 순서대로 읽힌다.
 *   영역 이름(aria-label)만 붙여 "알림 켜기 안내" 묶음이라는 것은 알린다.
 * - 터치 대상: "알림 켜기" 링크와 닫기 버튼 모두 44px(min-h-11 / size-11) 이상.
 * - 끄는 방법 한 줄: 켜기를 망설이는 이유(메일이 쌓일까 봐)를 덜어 주려고 "언제든 끌 수 있다"를 작게 덧붙인다.
 */
export function NotificationNudge({ onDismiss }: NotificationNudgeProps) {
  return (
    <div
      aria-label="알림 켜기 안내"
      role="group"
      className="flex flex-wrap items-start gap-x-3 rounded-lg border border-dashed border-info bg-info-bg py-1 pr-1 pl-3 text-sm text-info sm:items-center"
    >
      {/* 좁은 화면은 위쪽 정렬이라 문장 첫 줄 가운데에 맞춰 내린다(p의 위 여백 8px + 줄 높이 보정 2px) */}
      <BellIcon aria-hidden="true" className="mt-2.5 size-4 shrink-0 sm:mt-0" />
      <p className="min-w-[calc(100%-2.5rem)] flex-1 py-2 break-keep sm:min-w-0">
        <span className="font-semibold">알림을 켜면 매월 구독 안내 메일을 받아요.</span>{' '}
        <span className="text-info/80">알림 설정에서 언제든 끌 수 있어요.</span>
      </p>
      <div className="-mt-1 ml-auto flex items-center sm:mt-0">
        <NudgeLink />
        {onDismiss && <DismissButton onClick={onDismiss} />}
      </div>
    </div>
  )
}

// 띠의 "알림 켜기" 글자 링크. 다른 화면의 "알림 설정" 링크와 같은 글자 모양(굵게, 밑줄은 올렸을 때)에 화살표를 붙여 이동임을 알린다
function NudgeLink() {
  return (
    <Link
      to={NOTIFICATION_SETTINGS_PATH}
      className="inline-flex min-h-11 items-center gap-0.5 rounded-md px-2 font-semibold whitespace-nowrap underline underline-offset-3 hover:decoration-2 focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
    >
      알림 켜기
      <ChevronRightIcon aria-hidden="true" className="size-4" />
    </Link>
  )
}

interface DismissButtonProps {
  onClick: () => void // 눌렀을 때(안내 숨기기)
}

// 닫기(X) 버튼. 아이콘만 보이므로 낭독용 이름("알림 안내 닫기")을 붙인다. 누르는 칸은 44px
function DismissButton({ onClick }: DismissButtonProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label="알림 안내 닫기"
      className="inline-flex size-11 shrink-0 items-center justify-center rounded-md text-info/80 hover:bg-info/10 hover:text-info focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
    >
      <XIcon aria-hidden="true" className="size-4" />
    </button>
  )
}
