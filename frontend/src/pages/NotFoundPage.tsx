import { Link } from 'react-router'

/** 경로표에 없는 주소로 들어왔을 때 보여 주는 404 화면이다. 레이아웃(헤더·푸터) 안에 그려져 다른 화면으로 바로 이동할 수 있다. */
export function NotFoundPage() {
  return (
    <section className="flex flex-col gap-3">
      <h1 className="text-2xl font-semibold">페이지를 찾을 수 없음</h1>
      <p className="text-muted-foreground">주소가 바뀌었거나 없는 페이지입니다.</p>
      <Link to="/" className="underline underline-offset-4">
        홈으로 이동
      </Link>
    </section>
  )
}
