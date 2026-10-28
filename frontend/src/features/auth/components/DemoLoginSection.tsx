import { useEffect, useRef, useState, type MouseEvent } from 'react'
import { useForm } from 'react-hook-form'
import {
  ChevronDownIcon,
  EyeIcon,
  EyeOffIcon,
  InfoIcon,
  LoaderCircleIcon,
  TriangleAlertIcon,
} from 'lucide-react'
import { isApiError } from '@/api/http'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useDemoLogin } from '@/features/auth/hooks/useDemoLogin'
import { getErrorMessage } from '@/lib/errorMessages'
import { cn } from '@/lib/utils'

// 입력 칸·영역 id. 한 화면에 이 구역이 하나뿐이라 고정 id를 쓴다(aria-controls·aria-describedby가 가리킨다)
const PANEL_ID = 'demo-login-panel'
const LOGIN_ID_INPUT_ID = 'demo-login-id'
const PASSWORD_INPUT_ID = 'demo-login-password'
const LOGIN_ID_ERROR_ID = 'demo-login-id-error'
const PASSWORD_ERROR_ID = 'demo-login-password-error'
const LOCK_NOTE_ID = 'demo-login-lock-note'

// 요청 제한 응답에 Retry-After 헤더가 없을 때 기다리게 할 시간(초). 서버 규칙(같은 IP 15분 5회)의 창 길이와 같다
const FALLBACK_LOCK_SECONDS = 15 * 60

// 남은 시간 문구를 고쳐 쓰는 간격(1분). 분 단위로만 보이므로 더 자주 고칠 필요가 없다
const LOCK_TICK_MS = 60_000

// 필드 이름을 username·password로 둔 이유: React Hook Form은 입력 칸의 name 속성으로 필드를 찾는데,
// 비밀번호 관리자가 아이디 칸으로 알아보려면 name이 username이어야 한다. 요청 본문의 loginId로는 보낼 때 옮겨 담는다
interface DemoLoginFormValues {
  username: string // 아이디(제출 문서로 전달한 값). 요청 본문의 loginId
  password: string // 비밀번호. 화면 어디에도 미리 적어 두지 않는다
}

// 서버 오류를 화면에 어떻게 보일지. disabled는 폼 대신 "지금은 쓸 수 없음" 상자, message는 폼 아래 오류 문장이다
type ServerErrorView = { kind: 'disabled' } | { kind: 'message'; text: string } | null

/**
 * 요청 오류를 화면 표시 방식으로 바꾼다. 상태 코드별 문장은 UI 1차안 그대로다.
 * 429는 여기서 다루지 않는다: 남은 시간과 함께 따로 보인다(아래 lockedUntil).
 */
function toServerErrorView(error: unknown): ServerErrorView {
  if (!error) return null
  if (!isApiError(error)) {
    return {
      kind: 'message',
      text: '잠시 문제가 생겨 로그인하지 못했어요. 잠시 후 다시 시도해 주세요.',
    }
  }
  if (error.kind === 'canceled' || error.isRateLimited) return null
  // 1. 404: 서버에 데모 아이디·비밀번호가 없어 데모 로그인이 꺼져 있다(경로가 없는 것처럼 404, 계약)
  if (error.status === 404) return { kind: 'disabled' }
  // 2. 401: 아이디 또는 비밀번호가 틀림(어느 쪽인지는 알리지 않는다)
  if (error.status === 401) {
    return {
      kind: 'message',
      text: `${getErrorMessage('DEMO_LOGIN_FAILED')} 제출 문서의 값을 다시 확인해 주세요.`,
    }
  }
  // 3. 403: Origin이 프론트 주소와 달라 거절됨(로그인 CSRF 방지). 오래 열어 둔 탭 등에서 생길 수 있어 새로 고침을 권한다
  if (error.status === 403) {
    return {
      kind: 'message',
      text: '로그인 요청을 확인하지 못했어요. 페이지를 새로 고친 뒤 다시 시도해 주세요.',
    }
  }
  // 4. 500·네트워크 등 그 밖의 오류
  return {
    kind: 'message',
    text: '잠시 문제가 생겨 로그인하지 못했어요. 잠시 후 다시 시도해 주세요.',
  }
}

interface DemoLoginSectionProps {
  from: string // 로그인 뒤 돌아갈 내부 경로(getSafeRedirectPath로 검사한 값). 빈 문자열이면 홈
  isInitiallyOpen: boolean // 처음부터 펼쳐 둘지. 로그인 화면 주소가 /login?demo=1이면 참(제출 문서의 링크가 이 주소다)
}

/**
 * 로그인 화면(SCR-06)의 "데모 계정으로 로그인" 구역이다(Task 109).
 *
 * 데모 계정은 Google 계정 없이 서비스를 둘러보도록 여러 방문자가 함께 쓰는 공용 계정이다. 아이디·비밀번호는 제출 문서로만 전달하고
 * 화면에는 적지 않는다. 일반 방문자가 회원가입 폼으로 오해하지 않도록 평소에는 접어 두고, /login?demo=1일 때만 펼쳐서 보인다(사용자 결정 Q1).
 *
 * 상태별 표시(UI 1차안):
 * - 빈 칸: 요청을 보내지 않고 칸 아래에 안내, 첫 빈 칸으로 포커스(React Hook Form의 검증·포커스 기능을 쓴다)
 * - 요청 중: 버튼에 회전 아이콘과 "로그인하는 중…", 중복 제출 막기
 * - 401: 오류 문장 + 비밀번호 칸으로 포커스·전체 선택(다시 입력하기 쉽게)
 * - 429: "약 N분 뒤에" 안내, 대기 중에는 제출을 막고 남은 분을 1분마다 고쳐 보인다(전역 429 토스트는 띄우지 않는다, useDemoLogin)
 * - 404: 폼 대신 "지금은 사용할 수 없어요" 상자(펼침 버튼은 남긴다)
 * - 403·500·네트워크: 상황별 오류 문장
 * 성공하면 useDemoLogin이 토큰을 메모리에 넣고 원래 화면으로 이동한다.
 */
export function DemoLoginSection({ from, isInitiallyOpen }: DemoLoginSectionProps) {
  const [isOpen, setIsOpen] = useState(isInitiallyOpen)
  const [isPasswordVisible, setIsPasswordVisible] = useState(false)
  // 요청 제한(429)으로 막힌 동안의 끝 시각(밀리초). null이면 막히지 않았다
  const [lockedUntil, setLockedUntil] = useState<number | null>(null)
  // 남은 분을 계산할 "지금" 시각. 렌더링 중에 Date.now()를 부르지 않으려고(렌더링은 순수해야 한다) 막힐 때와 1분마다만 고친다
  const [lockNow, setLockNow] = useState(0)
  // 막힐 때 받은 대기 시간(분). 오류 문장의 "약 N분 뒤에"는 처음 값 그대로 두고, 버튼 아래 "약 N분 남았어요"만 줄어든다
  const [lockMinutesAtStart, setLockMinutesAtStart] = useState<number | null>(null)
  // 펼침 버튼으로 연 직후에만 아이디 칸으로 포커스를 옮기기 위한 표시. 화면을 처음 열 때(?demo=1)는 포커스를 빼앗지 않는다
  const shouldFocusOnOpen = useRef(false)

  const login = useDemoLogin(from)
  const {
    register,
    handleSubmit,
    setFocus,
    formState: { errors },
  } = useForm<DemoLoginFormValues>({ defaultValues: { username: '', password: '' } })

  const serverError = toServerErrorView(login.error)
  const isLocked = lockedUntil !== null
  const remainingMinutes =
    lockedUntil !== null ? Math.max(1, Math.ceil((lockedUntil - lockNow) / 60_000)) : null

  /*
   * 펼친 직후 아이디 칸에 포커스를 둔다.
   * effect에서 하는 이유: 펼침 버튼을 누르는 순간에는 입력 칸이 아직 화면에 없다(hidden). 다시 그린 뒤에야 포커스를 줄 수 있다.
   * 의존성 isOpen: 열림 상태가 바뀔 때만 확인하면 된다. setFocus는 React Hook Form이 주는 안정된 함수라 넣어도 다시 실행되지 않는다.
   */
  useEffect(() => {
    if (!isOpen || !shouldFocusOnOpen.current) return
    shouldFocusOnOpen.current = false
    setFocus('username')
  }, [isOpen, setFocus])

  /*
   * 요청 제한으로 막혀 있는 동안 1분마다 "지금" 시각을 고쳐 남은 분을 줄이고, 시간이 다 되면 막힘을 푼다.
   * 의존성 lockedUntil: 새로 막히면 타이머를 다시 만들고, 풀리면(null) 타이머를 지운다. 화면을 떠나면 정리 함수가 타이머를 지운다.
   */
  useEffect(() => {
    if (lockedUntil === null) return
    const timerId = window.setInterval(() => {
      const now = Date.now()
      setLockNow(now)
      if (now >= lockedUntil) {
        setLockedUntil(null)
        setLockMinutesAtStart(null)
      }
    }, LOCK_TICK_MS)
    return () => window.clearInterval(timerId)
  }, [lockedUntil])

  function handleToggle() {
    if (!isOpen) shouldFocusOnOpen.current = true
    setIsOpen((prev) => !prev)
  }

  // 검증을 통과한 값만 서버로 보낸다(빈 칸이면 handleSubmit이 이 함수를 부르지 않는다)
  const submit = handleSubmit((values) => {
    // 1. 요청 제한으로 막혀 있거나 요청 중이면 보내지 않는다(Enter 제출도 여기로 온다)
    if (isLocked || login.isPending) return
    // 2. 요청을 보내고, 실패하면 상태별로 포커스·막힘을 처리한다(문장은 login.error로 그린다)
    login.mutate(
      { data: { loginId: values.username, password: values.password } },
      {
        onError: (error) => {
          if (isApiError(error) && error.isRateLimited) {
            const seconds = error.retryAfterSeconds ?? FALLBACK_LOCK_SECONDS
            const now = Date.now()
            setLockNow(now)
            setLockedUntil(now + seconds * 1000)
            // 헤더가 없으면 null로 두어 "15분쯤 뒤에" 기본 문장을 쓴다
            setLockMinutesAtStart(
              error.retryAfterSeconds !== null ? Math.max(1, Math.ceil(seconds / 60)) : null,
            )
            return
          }
          // 401: 비밀번호를 다시 입력하기 쉽게 비밀번호 칸으로 옮기고 전체 선택한다
          if (isApiError(error) && error.status === 401) {
            setFocus('password', { shouldSelect: true })
          }
        },
      },
    )
  })

  // 막혀 있을 때 제출 버튼을 눌러도 아무것도 하지 않는다(aria-disabled라 포커스는 그대로 받는다)
  function handleLockedClick(event: MouseEvent<HTMLButtonElement>) {
    if (isLocked) event.preventDefault()
  }

  const rateLimitText = isLocked
    ? lockMinutesAtStart !== null
      ? `시도가 너무 많아요. 약 ${lockMinutesAtStart}분 뒤에 다시 시도해 주세요.`
      : '시도가 너무 많아요. 15분쯤 뒤에 다시 시도해 주세요.'
    : null
  const errorText = rateLimitText ?? (serverError?.kind === 'message' ? serverError.text : null)

  return (
    <div className="grid gap-3">
      {/* 구분선. 눈으로만 보이는 장식이라 낭독기에서 숨긴다(두 로그인 방법은 버튼 이름으로 구분된다) */}
      <div aria-hidden="true" className="flex items-center gap-3 text-xs text-muted-foreground">
        <span className="flex-1 border-t border-border" />
        또는
        <span className="flex-1 border-t border-border" />
      </div>

      <Button
        type="button"
        variant="ghost"
        aria-expanded={isOpen}
        aria-controls={PANEL_ID}
        onClick={handleToggle}
        className="min-h-11 w-full justify-between text-sm text-muted-foreground"
      >
        데모 계정으로 로그인
        <ChevronDownIcon
          aria-hidden="true"
          className={cn(
            'transition-transform motion-reduce:transition-none',
            isOpen && 'rotate-180',
          )}
        />
      </Button>

      {/* 접혀 있어도 영역 자체는 남겨 aria-controls가 가리킬 대상이 늘 있게 한다(hidden이라 화면·낭독기에서 빠진다) */}
      <div id={PANEL_ID} hidden={!isOpen}>
        {isOpen &&
          (serverError?.kind === 'disabled' ? (
            <p
              role="alert"
              className="rounded-xl border border-border bg-card p-4 text-sm break-keep text-muted-foreground"
            >
              지금은 데모 계정을 사용할 수 없어요. Google 계정으로 로그인해 주세요.
            </p>
          ) : (
            <form
              onSubmit={submit}
              noValidate
              aria-label="데모 계정 로그인"
              aria-busy={login.isPending}
              className="grid gap-4 rounded-xl border border-border bg-card p-4"
            >
              {/* 안내 상자. 공용 계정이라 생기는 일(공유·초기화·막힌 기능)을 먼저 알린다. 비밀번호 값은 적지 않는다 */}
              <div className="flex gap-2.5 rounded-lg border border-info bg-info-bg px-3 py-2.5 text-sm text-info">
                <InfoIcon aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
                <div className="grid gap-1.5">
                  <p className="font-semibold break-keep">
                    Google 계정 없이 둘러볼 수 있는 공용 계정이에요
                  </p>
                  <ul className="grid list-disc gap-1 pl-5 break-keep">
                    <li>아이디와 비밀번호는 제출 문서에 적혀 있어요.</li>
                    <li>
                      여러 방문자가 함께 쓰는 계정이라 찜·설정·플랜이 다른 사람과 공유되고, 언제든
                      초기화될 수 있어요. 개인 정보는 입력하지 마세요.
                    </li>
                    <li>
                      관리자 화면은 조회만 할 수 있고, 회원 탈퇴와 알림 설정 변경은 할 수 없어요.
                    </li>
                  </ul>
                </div>
              </div>

              <div className="grid gap-1.5">
                <Label htmlFor={LOGIN_ID_INPUT_ID}>아이디</Label>
                <Input
                  id={LOGIN_ID_INPUT_ID}
                  type="text"
                  autoComplete="username"
                  autoCapitalize="none"
                  autoCorrect="off"
                  spellCheck={false}
                  aria-invalid={errors.username ? true : undefined}
                  aria-describedby={errors.username ? LOGIN_ID_ERROR_ID : undefined}
                  className="h-11"
                  {...register('username', {
                    validate: (value) => value.trim() !== '' || '아이디를 입력해 주세요.',
                  })}
                />
                {errors.username && (
                  <p id={LOGIN_ID_ERROR_ID} className="text-sm text-destructive">
                    {errors.username.message}
                  </p>
                )}
              </div>

              <div className="grid gap-1.5">
                <Label htmlFor={PASSWORD_INPUT_ID}>비밀번호</Label>
                <div className="relative">
                  <Input
                    id={PASSWORD_INPUT_ID}
                    type={isPasswordVisible ? 'text' : 'password'}
                    autoComplete="current-password"
                    aria-invalid={errors.password ? true : undefined}
                    aria-describedby={errors.password ? PASSWORD_ERROR_ID : undefined}
                    className="h-11 pr-11"
                    {...register('password', {
                      // 비밀번호는 공백도 값일 수 있어 앞뒤를 자르지 않고 빈 문자열만 막는다
                      validate: (value) => value !== '' || '비밀번호를 입력해 주세요.',
                    })}
                  />
                  {/* 비밀번호 보기 전환. 눌림 상태(aria-pressed)와 이름이 함께 바뀌어 낭독기도 지금 상태를 안다 */}
                  <button
                    type="button"
                    aria-pressed={isPasswordVisible}
                    aria-label={isPasswordVisible ? '비밀번호 숨기기' : '비밀번호 보기'}
                    onClick={() => setIsPasswordVisible((prev) => !prev)}
                    className="absolute top-1/2 right-0.5 inline-flex size-10 -translate-y-1/2 items-center justify-center rounded-md text-muted-foreground hover:text-foreground focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
                  >
                    {isPasswordVisible ? (
                      <EyeOffIcon aria-hidden="true" className="size-4" />
                    ) : (
                      <EyeIcon aria-hidden="true" className="size-4" />
                    )}
                  </button>
                </div>
                {errors.password && (
                  <p id={PASSWORD_ERROR_ID} className="text-sm text-destructive">
                    {errors.password.message}
                  </p>
                )}
              </div>

              <div className="grid gap-1.5">
                <Button
                  type="submit"
                  variant="secondary"
                  size="lg"
                  disabled={login.isPending}
                  aria-disabled={isLocked ? true : undefined}
                  aria-describedby={isLocked ? LOCK_NOTE_ID : undefined}
                  onClick={handleLockedClick}
                  className="h-11 w-full aria-disabled:cursor-not-allowed aria-disabled:opacity-70"
                >
                  {/* loading-spin: 평소 회전, 동작 줄이기 설정이면 밝기만 오르내림(styles/tokens.css) */}
                  {login.isPending && (
                    <LoaderCircleIcon aria-hidden="true" className="loading-spin" />
                  )}
                  {login.isPending ? '로그인하는 중…' : '로그인'}
                </Button>
                {/* 남은 시간. 1분마다 바뀌지만 그때마다 읽어 주면 방해가 되어 aria-live를 두지 않는다(버튼의 설명으로만 연결) */}
                {isLocked && remainingMinutes !== null && (
                  <p id={LOCK_NOTE_ID} className="text-center text-sm text-muted-foreground">
                    약 {remainingMinutes}분 남았어요
                  </p>
                )}
              </div>

              {errorText && (
                <p
                  role="alert"
                  className="flex items-start gap-1.5 text-sm break-keep text-destructive"
                >
                  <TriangleAlertIcon aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
                  {errorText}
                </p>
              )}
            </form>
          ))}
      </div>
    </div>
  )
}
