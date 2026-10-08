/**
 * SCR-06 로그인 콜백 화면(`/auth/callback`, 공개).
 *
 * 흐름(TECH 4절): Google 로그인이 끝나면 백엔드가 Refresh Token을 HttpOnly 쿠키(Path=/api/auth)로 심고
 * 브라우저를 이 주소로 302 이동시킨다. 이 화면은 POST /api/auth/refresh를 불러 Access Token을 받아
 * 메모리(authStore)에만 저장한 뒤 원래 화면으로 보낸다.
 *
 * 이 화면을 RequireAuth 밖(공개)에 두는 이유: 이 시점에는 Access Token이 아직 메모리에 없어서,
 * 가드 안에 두면 토큰을 받기도 전에 로그인 화면으로 되돌려 보내는 순환이 생긴다.
 *
 * 보안상 주의: 토큰을 주소(쿼리·해시)로 받지 않는다. 주소는 브라우저 기록·Referer·서버 로그에 남기 때문이다(frontend.md 인증 규칙).
 */
export function AuthCallbackPage() {
  // TODO(Task 057): /api/auth/refresh 호출, 성공 시 authStore 저장 후 이동, 실패 시 로그인 실패 안내(목업은 Task 027)
  return (
    <section className="flex flex-col gap-3">
      <h1 className="text-2xl font-semibold">로그인 처리 중</h1>
      <p className="text-muted-foreground">
        화면 골격입니다. 로그인 연동은 다음 작업에서 채웁니다.
      </p>
    </section>
  )
}
