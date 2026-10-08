interface ComingSoonProps {
  title: string // 화면 이름. 페이지 제목(h1)으로 그대로 쓴다
  plannedPhase: '2단계' | '3단계' // 이 화면이 제공될 개발 단계(PRD 6절 화면 표의 "단계" 열)
}

/**
 * MVP 범위 밖 화면(SCR-03·05·11·12·13·17·18)에 보여 주는 "준비 중" 자리표시다.
 *
 * 빈 화면이나 404 대신 이 자리표시를 두는 이유: 주소는 미리 확정해 두고(경로표, Task 018),
 * 사용자가 들어와도 "없는 페이지"가 아니라 "아직 제공 전"임을 알 수 있게 하기 위함이다.
 * 해당 단계에서 실제 화면을 만들면 페이지 파일이 이 컴포넌트 대신 기능 컴포넌트를 그리도록 바꾼다.
 */
export function ComingSoon({ title, plannedPhase }: ComingSoonProps) {
  return (
    <section className="flex flex-col gap-3">
      <h1 className="text-2xl font-semibold">{title}</h1>
      {/* 색이 아니라 "준비 중"이라는 글자로 상태를 알린다(색만으로 정보 전달 금지) */}
      <p className="text-muted-foreground">
        준비 중인 화면입니다. {plannedPhase}에서 제공할 예정입니다.
      </p>
    </section>
  )
}
