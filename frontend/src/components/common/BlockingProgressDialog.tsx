import { useEffect, useState } from 'react'
import {
  AlertDialog,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'

interface BlockingProgressDialogProps {
  open: boolean // 작업이 진행 중인지. true인 동안만 창이 떠 있고, false가 되면 저절로 사라진다
  title: string // 창 가운데 큰 글자(예: "플랜을 계산하고 있어요")
  description: string // 제목 아래 보충 설명(무엇을 하는지, 얼마나 걸리는지)
  activityLabel?: string // 경과 시간 뒤에 붙는 말(예: "계산하는 중이에요" → "7초째 계산하는 중이에요"). 기본 "진행하는 중이에요"
}

/**
 * 오래 걸리는 작업이 끝날 때까지 화면 가운데에 띄우는 "닫을 수 없는" 진행 창이다(예: 플랜 계산).
 *
 * 왜 이 모양인가:
 * - 계산 중에 요금을 고치거나 다른 버튼을 누르면 결과가 어긋날 수 있어, 뒤 화면을 흐리게(overlay backdrop-blur) 덮고 조작을 막는다.
 * - shadcn AlertDialog(Radix)를 쓴다. AlertDialog는 바깥을 눌러도 닫히지 않고, 열린 동안 포커스가 창 안에만 머문다(포커스 가둠).
 *   Esc는 Radix가 닫으려 하지만 onEscapeKeyDown에서 막는다. 또 open을 부모가 쥐고 onOpenChange를 받지 않아 스스로 닫히지 않는다.
 * - 창 안에 누를 것이 없어 포커스는 창 본문(Radix가 tabIndex=-1을 준다)에 머문다.
 *
 * 낭독기: 창이 열리면 포커스가 창으로 들어가며 창의 이름(제목)·설명이 한 번 읽힌다(role="alertdialog", aria-labelledby·aria-describedby).
 * 이전: 제목·설명을 role="status"(aria-live=polite) 영역으로도 감싸, 창 이름으로 한 번 + 실시간 알림으로 한 번 같은 글이 두 번 읽힐 수 있었다(검수 지적).
 * 경과 시간 글자는 1초마다 바뀌므로 aria-hidden으로 숨긴다(매초 읽으면 다른 낭독을 방해한다).
 *
 * 움직임(styles/tokens.css):
 * - 평소: 막대 안의 짧은 띠가 왼쪽 바깥에서 오른쪽 바깥으로 계속 지나간다(animate-indeterminate).
 * - 동작 줄이기: 띠가 막대 전체 폭으로 제자리에 있고 밝기만 천천히 오르내린다(animate-breathe). 위치가 움직이지 않아 어지럼이 없고, 멈춘 것처럼 보이지도 않는다.
 * - 경과 시간 글자: "7초째 계산하는 중이에요"처럼 숫자만 1초마다 바뀐다. 움직임과 상관없이 늘 보여, 동작 줄이기에서도 진행 중임을 숫자로 알 수 있다.
 *   단계나 퍼센트는 서버가 알려 주지 않아 보이지 않는다(지어내면 "확정"처럼 보인다).
 * 여러 화면에서 쓸 수 있게 components/common에 둔다(기능 폴더끼리 import 금지 규칙, frontend.md).
 */
export function BlockingProgressDialog({
  open,
  title,
  description,
  activityLabel = '진행하는 중이에요',
}: BlockingProgressDialogProps) {
  return (
    <AlertDialog open={open}>
      <AlertDialogContent
        // Esc로 닫히지 않게 막는다. 작업은 취소되지 않는데 창만 사라지면 화면을 다시 만질 수 있게 돼 혼란스럽다
        onEscapeKeyDown={(event) => event.preventDefault()}
        // 375px 화면에서 좌우 여백 16px씩이 남도록 폭을 화면보다 2rem 좁게 하고, 넓은 화면은 24rem(max-w-sm)까지만 넓힌다.
        // 원본이 data-[size=default]:max-w-xs로 폭을 정해 같은 변형(data-[size=default]:)으로 덮어써야 이긴다
        className="w-[calc(100%-2rem)] gap-4 p-6 text-center data-[size=default]:max-w-sm data-[size=default]:sm:max-w-sm"
      >
        {/* 이전: <div role="status" aria-live="polite" className="grid gap-2"> — 창 이름·설명과 겹쳐 두 번 읽힐 수 있어 일반 div로 바꿨다 */}
        <div className="grid gap-2">
          <AlertDialogTitle className="text-lg font-semibold break-keep">{title}</AlertDialogTitle>
          <AlertDialogDescription className="text-sm break-keep text-muted-foreground">
            {description}
          </AlertDialogDescription>
        </div>
        {/* 끝없는 진행 막대. 장식이라 낭독기에서는 숨긴다(진행 중이라는 사실은 위 글자가 전한다).
            평소: 띠(폭 1/3)가 왼쪽 바깥(-100%)에서 출발해 오른쪽 바깥으로 지나간다.
            동작 줄이기: 띠를 막대 전체 폭(w-full)으로 펴고 밝기만 오르내린다.
            이전: className="h-full w-1/3 translate-x-full ... motion-safe:animate-indeterminate"
              translate-x-full은 Tailwind 4에서 CSS translate 속성이라 keyframes의 transform과 따로 더해졌다.
              그래서 움직일 때도 출발점이 -100% + 100% = 0(막대 왼쪽 끝 안쪽)이 되어 띠가 갑자기 나타났다(검수 지적). 지금은 translate를 쓰지 않는다 */}
        <div aria-hidden="true" className="h-1.5 overflow-hidden rounded-full bg-muted">
          <div className="h-full w-1/3 rounded-full bg-primary motion-safe:animate-indeterminate motion-reduce:w-full motion-reduce:animate-breathe" />
        </div>
        {/* 창이 닫히면 AlertDialogContent가 화면에서 빠지며 이 글자도 함께 사라진다. 다시 열면 새로 만들어져 0초부터 센다 */}
        <ElapsedSeconds activityLabel={activityLabel} />
      </AlertDialogContent>
    </AlertDialog>
  )
}

/**
 * 창이 열린 뒤 몇 초째인지 보여 주는 작은 글자다(예: "7초째 계산하는 중이에요").
 * 숫자 폭이 바뀌어 글자가 좌우로 흔들리지 않게 tabular-nums(고정폭 숫자)를 쓴다.
 * 첫 1초 동안은 "0초째"가 어색해 "잠시만 기다려 주세요"를 보인다.
 */
function ElapsedSeconds({ activityLabel }: { activityLabel: string }) {
  const [seconds, setSeconds] = useState(0) // 화면 표시 전용 상태(창이 열린 뒤 지난 초)

  // 1초마다 숫자만 1 올린다. 창이 닫혀 이 컴포넌트가 사라지면 타이머도 정리된다
  useEffect(() => {
    const timer = window.setInterval(() => setSeconds((prev) => prev + 1), 1000)
    return () => window.clearInterval(timer)
  }, [])

  return (
    <p aria-hidden="true" className="text-sm text-muted-foreground tabular-nums">
      {seconds < 1 ? '잠시만 기다려 주세요' : `${seconds}초째 ${activityLabel}`}
    </p>
  )
}
