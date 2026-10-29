import { InfoIcon } from 'lucide-react'
import { useIsDemo } from '@/hooks/useIsDemo'

/**
 * 데모 계정으로 로그인했을 때 머리글 바로 아래에 늘 보이는 안내 띠다(Task 109).
 *
 * 왜 늘 보이나(닫기 버튼 없음, 사용자 결정 Q2): 데모 계정은 여러 방문자가 함께 쓰는 공용 계정이라,
 * 지금 입력한 찜·설정이 다른 사람 때문에 바뀌거나 초기화될 수 있다. 어느 화면에서든 이 사실을 잊지 않게 한다.
 * 데모 로그인 성공 때 토스트를 띄우지 않는 것도 이 띠가 "로그인됐다"를 대신 알리기 때문이다.
 *
 * role="note": 화면 내용을 보충하는 안내라는 뜻이다. 상태가 바뀔 때 읽어 주는 알림(alert·status)이 아니라 화면을 옮길 때마다 다시 읽히지 않는다.
 * 색은 info 토큰만 써서 관리자 밝은 테마(styles/admin-theme.css)에서도 그 테마의 info 색으로 바뀐다.
 * 데모 계정이 아니면 아무것도 그리지 않는다.
 */
export function DemoBanner() {
  const isDemo = useIsDemo()
  if (!isDemo) return null

  return (
    <div
      role="note"
      aria-label="데모 계정 안내"
      className="border-t border-info/40 bg-info-bg text-info"
    >
      <div className="mx-auto flex max-w-5xl items-start gap-2 px-4 py-2 text-sm break-keep">
        <InfoIcon aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
        <p>
          <strong className="font-semibold">데모 계정으로 둘러보는 중이에요.</strong> 다른 방문자와
          함께 쓰는 계정이라 입력한 내용이 바뀌거나 초기화될 수 있어요.
        </p>
      </div>
    </div>
  )
}
