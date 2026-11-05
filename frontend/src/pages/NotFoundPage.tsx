import { Link } from 'react-router'
import { PageTitle } from '@/components/common/PageTitle'
import { Button } from '@/components/ui/button'

/**
 * 경로표에 없는 주소로 들어왔을 때 보여 주는 404 화면이다. 레이아웃(헤더·푸터) 안에 그려져 다른 화면으로 바로 이동할 수 있다.
 * 다음 행동은 "홈으로 이동" 주 버튼 하나다(새로고침해도 결과가 같아 새로고침 버튼은 두지 않는다).
 * 오류 경계(RouteErrorBoundary)와 달리 라우터가 멀쩡한 상태라 페이지를 새로 불러오지 않는 Link로 이동한다.
 */
export function NotFoundPage() {
  return (
    <section className="flex flex-col gap-3">
      <PageTitle name="페이지를 찾을 수 없음" />
      <h1 className="text-2xl font-semibold">페이지를 찾을 수 없음</h1>
      <p className="text-muted-foreground">주소가 바뀌었거나 없는 페이지입니다.</p>
      {/* 이전: 글자 링크(<Link to="/" className="underline underline-offset-4">홈으로 이동</Link>).
          다음 행동이 눈에 덜 띄어 높이 44px의 주 버튼으로 바꿨다. w-fit: 세로 flex 안에서 버튼이 가로로 늘어나지 않게 한다 */}
      <Button asChild className="h-11 w-fit">
        <Link to="/">홈으로 이동</Link>
      </Button>
    </section>
  )
}
