import { useId, useState } from 'react'
import { ArrowLeftIcon, TriangleAlertIcon } from 'lucide-react'
import { Link } from 'react-router'
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'

/** 탈퇴하면 지워지는 데이터 목록(PRD FR-07·SCR-13). 대기 알림은 알림 기능이 공개 뒤라 넣지 않는다(사용자 확정) */
const DELETED_DATA = [
  '찜한 작품 목록',
  '서비스별 구독 상태 설정',
  '직접 고친 요금',
  '저장한 플랜(계산 결과·변경 내역 포함)',
] as const

interface WithdrawScreenProps {
  onWithdraw: () => void // 확인 대화상자에서 [탈퇴]를 확정했다. 실제 탈퇴 API·로그아웃·이동은 이 콜백을 넘기는 쪽이 맡는다
  isPending?: boolean // 탈퇴 요청이 진행 중인지. 참이면 버튼을 잠그고 대화상자를 닫지 못하게 한다
  errorMessage?: string // 탈퇴 요청이 실패했을 때 대화상자 안에 보일 안내. 없으면 보이지 않는다
}

/**
 * SCR-13 회원 탈퇴 화면의 본문이다(UI 1차안, Task 081). pages/WithdrawPage는 이 컴포넌트를 연결만 한다.
 *
 * 위에서 아래 순서와 이유(정보를 줄여 데스크톱 한 화면에 들어오게 했다, 사용자 요청):
 * 1. 내 설정으로 돌아가는 링크·제목: 설정 화면에서 들어오는 하위 화면이라 변경 내역 화면과 같은 모양으로 되돌아갈 길을 둔다.
 * 2. 핵심 경고 한 줄: "바로 삭제·되돌릴 수 없음"을 가장 먼저 읽게 한다. 빨간색만으로 위험을 알리지 않도록 경고 아이콘과 문장을 함께 둔다.
 * 3. 삭제되는 데이터 목록(4줄): 무엇이 사라지는지 짧게만 적는다.
 * 4. 동의 체크 → [탈퇴하기] → 확인 대화상자: 실수로 누르는 일을 두 번 막는다. 체크하기 전에는 버튼이 잠겨 있다.
 *
 * 버튼 톤: shadcn의 destructive 변형(옅은 빨강 바탕 + 빨강 글자)을 쓴다. 진한 빨강 면은 화면에서 가장 눈에 띄는 버튼이 되어
 * "누르라는 버튼"처럼 보이므로 피했다(과하지 않게, 사용자 요청). 대신 글자 "탈퇴하기"로 무엇을 하는지 분명히 한다.
 */
export function WithdrawScreen({
  onWithdraw,
  isPending = false,
  errorMessage,
}: WithdrawScreenProps) {
  const consentId = useId() // 동의 체크박스와 label을 잇는 id
  const [isAgreed, setIsAgreed] = useState(false) // 동의 체크 여부. 체크해야 [탈퇴하기]가 열린다
  const [isDialogOpen, setIsDialogOpen] = useState(false) // 확인 대화상자 열림 여부

  return (
    <section className="mx-auto flex w-full max-w-xl flex-col gap-5">
      <div className="grid gap-2">
        {/* 변경 내역 화면의 되돌아가기 링크와 같은 모양. 높이 44px(min-h-11)로 터치 크기를 맞췄다 */}
        <Link
          to="/settings"
          className="inline-flex min-h-11 w-fit items-center gap-1.5 rounded-md text-sm font-semibold text-primary focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
        >
          <ArrowLeftIcon aria-hidden="true" className="size-4" />내 설정
        </Link>
        <h1 className="text-2xl font-semibold">회원 탈퇴</h1>
      </div>

      <div className="grid gap-4 rounded-xl border border-border bg-card p-5">
        {/* 핵심 경고. 아이콘(삼각 경고)과 굵은 문장으로 위험을 알리고 빨강은 아이콘에만 써서 색 없이도 읽힌다 */}
        <p className="flex items-start gap-2.5 text-base font-semibold break-keep">
          <TriangleAlertIcon
            aria-hidden="true"
            className="mt-0.5 size-5 shrink-0 text-destructive"
          />
          탈퇴하면 아래 데이터가 바로 삭제되고, 되돌릴 수 없어요.
        </p>

        <ul
          aria-label="삭제되는 데이터"
          className="grid gap-1.5 pl-7.5 text-sm text-muted-foreground"
        >
          {DELETED_DATA.map((item) => (
            <li key={item} className="list-disc break-keep marker:text-muted-foreground">
              {item}
            </li>
          ))}
        </ul>

        <div className="grid gap-4 border-t border-border pt-4">
          {/* 동의 체크. 내 설정 화면의 "가장 저렴한 요금제" 체크와 같은 방식(브라우저 기본 체크박스, shadcn checkbox 미설치).
              줄 전체를 <label>로 감싸 글자를 눌러도 체크되고, 높이 44px로 터치 크기를 맞췄다 */}
          <label
            htmlFor={consentId}
            className="flex min-h-11 cursor-pointer items-center gap-3 rounded-lg border border-border px-3 py-2.5 has-[:checked]:border-destructive has-[:disabled]:cursor-not-allowed has-[:focus-visible]:ring-3 has-[:focus-visible]:ring-ring/50"
          >
            <input
              id={consentId}
              type="checkbox"
              checked={isAgreed}
              disabled={isPending}
              onChange={(event) => setIsAgreed(event.target.checked)}
              className="size-4.5 shrink-0 accent-destructive focus-visible:outline-none"
            />
            <span className="text-sm font-semibold break-keep">
              위 내용을 확인했고 탈퇴에 동의해요
            </span>
          </label>

          <div className="flex flex-col-reverse gap-2 sm:flex-row sm:items-center sm:justify-between">
            {/* 취소는 보조 링크. 탈퇴 버튼보다 눈에 덜 띄지만 높이 44px로 누르기 쉽게 했다 */}
            <Link
              to="/settings"
              className="inline-flex min-h-11 items-center justify-center rounded-md px-2 text-sm font-semibold text-muted-foreground underline-offset-4 hover:text-foreground hover:underline focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
            >
              취소하고 내 설정으로
            </Link>

            {/* 확인 대화상자. Radix AlertDialog는 열릴 때 포커스를 [취소](안전한 쪽)에 두고, Esc로 닫히며,
                닫히면 포커스를 [탈퇴하기](Trigger)로 돌려준다. 바깥을 눌러서는 닫히지 않는다.
                탈퇴 요청이 진행 중이면 닫지 못하게 막는다(요청 도중 화면만 닫혀 결과를 못 보는 일을 막는다) */}
            <AlertDialog
              open={isDialogOpen}
              onOpenChange={(isOpen) => {
                if (!isPending) setIsDialogOpen(isOpen)
              }}
            >
              <AlertDialogTrigger asChild>
                <Button
                  variant="destructive"
                  disabled={!isAgreed || isPending}
                  className="h-11 px-5 text-base font-semibold"
                >
                  탈퇴하기
                </Button>
              </AlertDialogTrigger>
              <AlertDialogContent>
                <AlertDialogHeader>
                  <AlertDialogTitle className="text-lg font-semibold">
                    정말 탈퇴할까요?
                  </AlertDialogTitle>
                  <AlertDialogDescription className="break-keep">
                    찜·설정·플랜이 모두 바로 삭제되고 복구할 수 없어요. 탈퇴하면 로그아웃돼요.
                  </AlertDialogDescription>
                </AlertDialogHeader>
                {errorMessage ? (
                  <p role="alert" className="text-sm break-keep text-destructive">
                    {errorMessage}
                  </p>
                ) : null}
                <AlertDialogFooter>
                  <AlertDialogCancel disabled={isPending} className="h-11">
                    취소
                  </AlertDialogCancel>
                  {/* AlertDialogAction 대신 일반 버튼을 쓴 이유: Action은 누르는 즉시 대화상자를 닫아 버려
                      요청 진행 중·실패 안내를 보여 줄 수 없다. 성공하면 넘기는 쪽이 로그아웃·이동하므로 화면째 사라진다 */}
                  <Button
                    variant="destructive"
                    disabled={isPending}
                    aria-busy={isPending}
                    onClick={onWithdraw}
                    className="h-11 font-semibold"
                  >
                    {isPending ? '탈퇴하는 중…' : '탈퇴'}
                  </Button>
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
          </div>
        </div>
      </div>
    </section>
  )
}
