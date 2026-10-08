import { isRouteErrorResponse, Link, useRouteError } from 'react-router'

/**
 * 라우트에서 난 오류를 받아 안내 화면을 그리는 전역 오류 경계다.
 *
 * React Router의 데이터 라우터는 화면을 그리다 던진 오류, lazy로 페이지 코드를 받아오다 실패한 오류
 * (예: 새 배포 뒤 예전 파일 이름의 조각 파일이 없어진 경우)를 가장 가까운 ErrorBoundary 라우트로 보낸다.
 * 여기서 받지 않으면 React Router 기본 오류 화면(영어, 개발자용)이 사용자에게 그대로 보인다.
 *
 * API 오류(ProblemDetail) 안내는 각 화면의 오류 상태와 공통 인터셉터(Task 025)가 맡는다. 이 경계는 그 밖의 예기치 못한 오류용이다.
 */
export function RouteErrorBoundary() {
  // useRouteError: 이 경계까지 올라온 오류 값을 돌려준다. 타입이 unknown이라 아래에서 종류를 좁혀 쓴다.
  const error = useRouteError()

  // 개발 중 원인을 바로 보도록 콘솔에 남긴다. 운영 로그 수집은 아직 없다(관측은 3단계).
  console.error('라우트 오류:', error)

  // 라우터가 만든 응답 오류(예: 일치하는 경로 없음 404)는 상태 코드로 구분한다.
  // 지금 라우터에는 '*' 경로가 있어 없는 주소는 NotFoundPage가 받지만, 경로 설정이 바뀌어도 안내가 비지 않도록 남긴다.
  const isNotFound = isRouteErrorResponse(error) && error.status === 404

  return (
    <section role="alert" className="mx-auto flex max-w-xl flex-col gap-4 px-4 py-16">
      <h1 className="text-2xl font-semibold">
        {isNotFound ? '페이지를 찾을 수 없음' : '화면을 표시하지 못함'}
      </h1>
      <p className="text-muted-foreground">
        {isNotFound
          ? '주소가 바뀌었거나 없는 페이지입니다.'
          : '일시적인 문제일 수 있습니다. 새로고침하거나 잠시 후 다시 시도해 주세요.'}
      </p>
      {/* 오류 경계 안에서는 레이아웃이 깨졌을 수 있어, 항상 동작하는 홈 링크를 둔다 */}
      <Link to="/" className="underline underline-offset-4">
        홈으로 이동
      </Link>
    </section>
  )
}
