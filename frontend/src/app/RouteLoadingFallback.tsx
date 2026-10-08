/**
 * 앱을 처음 열 때 첫 화면 코드(lazy로 나눈 페이지 파일)를 받아오는 동안 보여 주는 자리표시다.
 *
 * 왜 필요한가: 데이터 라우터는 첫 화면에 필요한 lazy 코드를 다 받기 전까지 화면을 그리지 않는다.
 * 그동안 그릴 것(HydrateFallback)을 최상위 라우트에 주지 않으면 빈 화면이 보이고 개발 콘솔에 경고가 남는다.
 * 첫 로드 이후의 화면 이동에서는 이전 화면이 유지된 채로 다음 화면을 받아오므로 이 자리표시가 다시 보이지 않는다.
 */
export function RouteLoadingFallback() {
  return (
    <p role="status" className="px-4 py-16 text-center text-muted-foreground">
      불러오는 중
    </p>
  )
}
