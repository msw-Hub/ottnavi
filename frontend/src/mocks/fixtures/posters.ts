/*
 * 목업 포스터 그림을 만드는 도우미다(Task 027 결정 ②).
 *
 * 왜 직접 그리나: 실제 TMDB 이미지 주소를 쓰면 목업 화면을 열 때마다 외부(image.tmdb.org)로 요청이 나가고,
 * 오프라인·테스트 환경에서는 실패해 콘솔 에러가 난다. 그래서 그림 자체를 주소에 담은 data URI(SVG)를 쓴다.
 * 요청이 따로 나가지 않으므로 항상 같은 그림이 뜬다. 포스터 없는 작품은 null로 두어 "포스터 없음" 대체 표시도 함께 확인한다.
 * SVG 안의 색 값은 포스터 "그림 내용"이지 화면 디자인 값이 아니라 디자인 토큰 규칙의 대상이 아니다(ComponentsPreviewPage와 같다).
 */

/**
 * 색상 각도(hue, 0~359)만 다른 간단한 세로 그라데이션 포스터를 data URI로 만든다.
 * 작품마다 색이 달라 목록에서 카드를 구분하기 쉽다. 글자는 넣지 않는다(제목은 카드가 따로 보여 준다).
 */
export function createMockPosterUrl(hue: number): string {
  const top = `hsl(${hue} 45% 14%)`
  const bottom = `hsl(${(hue + 40) % 360} 55% 34%)`
  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 300">` +
    `<defs><linearGradient id="g" x1="0" y1="0" x2="0" y2="1">` +
    `<stop offset="0" stop-color="${top}"/><stop offset="1" stop-color="${bottom}"/>` +
    `</linearGradient></defs>` +
    `<rect width="200" height="300" fill="url(#g)"/>` +
    `<circle cx="100" cy="120" r="42" fill="none" stroke="hsl(${hue} 80% 85%)" stroke-width="8"/>` +
    `<rect x="62" y="196" width="76" height="10" rx="5" fill="hsl(${hue} 80% 85%)"/>` +
    `</svg>`
  // # 같은 문자가 주소로 해석되지 않도록 encodeURIComponent로 안전하게 바꾼다
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`
}
