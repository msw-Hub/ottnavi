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
 * 낭독기: 창이 열리면 AlertDialog의 이름(제목)·설명이 함께 읽힌다. 제목은 role="status"(aria-live=polite) 영역에도 둬서
 * 창을 잘 읽지 않는 낭독기에서도 진행 중임을 알린다.
 *
 * 움직임: 막대 안의 띠가 계속 지나가는 끝없는 진행 막대(animate-indeterminate, styles/tokens.css).
 * 동작 줄이기 설정이면 motion-safe로 걸러 움직이지 않는 띠(가운데에 멈춘 짧은 막대)로 보인다.
 * 여러 화면에서 쓸 수 있게 components/common에 둔다(기능 폴더끼리 import 금지 규칙, frontend.md).
 */
export function BlockingProgressDialog({ open, title, description }: BlockingProgressDialogProps) {
  return (
    <AlertDialog open={open}>
      <AlertDialogContent
        // Esc로 닫히지 않게 막는다. 작업은 취소되지 않는데 창만 사라지면 화면을 다시 만질 수 있게 돼 혼란스럽다
        onEscapeKeyDown={(event) => event.preventDefault()}
        // 375px 화면에서 좌우 여백 16px씩이 남도록 폭을 화면보다 2rem 좁게 하고, 넓은 화면은 24rem(max-w-sm)까지만 넓힌다.
        // 원본이 data-[size=default]:max-w-xs로 폭을 정해 같은 변형(data-[size=default]:)으로 덮어써야 이긴다
        className="w-[calc(100%-2rem)] gap-4 p-6 text-center data-[size=default]:max-w-sm data-[size=default]:sm:max-w-sm"
      >
        <div role="status" aria-live="polite" className="grid gap-2">
          <AlertDialogTitle className="text-lg font-semibold break-keep">{title}</AlertDialogTitle>
          <AlertDialogDescription className="text-sm break-keep text-muted-foreground">
            {description}
          </AlertDialogDescription>
        </div>
        {/* 끝없는 진행 막대. 장식이라 낭독기에서는 숨긴다(진행 중이라는 사실은 위 글자가 전한다).
            움직일 때: 왼쪽 바깥에서 출발(-translate-x-full)해 오른쪽으로 지나간다. 동작 줄이기: translate-x-full로 가운데쯤 멈춰 있다 */}
        <div aria-hidden="true" className="h-1.5 overflow-hidden rounded-full bg-muted">
          <div className="h-full w-1/3 translate-x-full rounded-full bg-primary motion-safe:animate-indeterminate" />
        </div>
      </AlertDialogContent>
    </AlertDialog>
  )
}
