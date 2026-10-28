/*
 * 알림 메일 예시 본문(HTML) 가공 도우미다(Task 110).
 *
 * 서버가 주는 메일 HTML은 실제 메일 앱에서 쓰는 완성 문서라 링크(<a>)에 target이 없다(backend templates/mail 확인).
 * 팝업은 iframe sandbox 안에서 링크를 눌러도 팝업 자체가 이동하지 않고 새 탭에서 열려야 하므로,
 * <head>에 <base target="_blank">를 끼워 모든 링크의 기본 목적지를 새 탭으로 만든다.
 */

// <head ...> 여는 태그(대소문자 무관). 여기 바로 뒤에 <base>를 끼운다
const HEAD_OPEN_TAG = /<head(\s[^>]*)?>/i
// 이미 <base ... target=...>이 있는지(있으면 메일이 정한 값을 존중하고 건드리지 않는다)
const BASE_WITH_TARGET = /<base\b[^>]*\btarget\s*=/i
// <html ...> 여는 태그. <head>가 아예 없는 문서에서 <head>를 새로 만들 자리를 찾는다
const HTML_OPEN_TAG = /<html(\s[^>]*)?>/i

const BASE_TAG = '<base target="_blank">'

/**
 * 메일 HTML의 <head>에 `<base target="_blank">`가 없으면 끼워 넣어 돌려준다.
 * 1. 이미 target이 있는 <base>가 있으면 그대로 둔다.
 * 2. <head>가 있으면 그 바로 뒤에 넣는다.
 * 3. <head>는 없고 <html>만 있으면 <html> 뒤에 <head>째 만든다.
 * 4. 둘 다 없는 조각이면 맨 앞에 붙인다(브라우저가 알아서 head로 옮긴다).
 */
export function injectBaseTarget(html: string): string {
  // 1. 이미 있으면 손대지 않는다
  if (BASE_WITH_TARGET.test(html)) return html
  // 2. <head> 바로 뒤
  if (HEAD_OPEN_TAG.test(html)) return html.replace(HEAD_OPEN_TAG, (tag) => `${tag}${BASE_TAG}`)
  // 3. <html> 뒤에 <head> 새로 만들기
  if (HTML_OPEN_TAG.test(html)) {
    return html.replace(HTML_OPEN_TAG, (tag) => `${tag}<head>${BASE_TAG}</head>`)
  }
  // 4. 조각 문서
  return `${BASE_TAG}${html}`
}
