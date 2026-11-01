/*
 * 방문 통계(Google Analytics 4, gtag.js) 연동이다. 사용자 결정(2026-10-30): 방문자 수를 보는 목적으로만 쓰고,
 * 로그인 정보·이메일·내부 사용자 번호와 연결하지 않으며 광고 목적으로 쓰지 않는다.
 *
 * 켜지는 조건: 환경 변수 VITE_GA_MEASUREMENT_ID(측정 ID, 예 G-XXXXXXXXXX)가 있고 목업 모드가 아닐 때만이다.
 * 값이 비어 있으면 스크립트를 불러오지도, 어떤 함수를 호출해도 전송하지도 않는다(개발·테스트·목업·로컬은 외부 요청 0건).
 * 측정 ID는 화면 소스에 그대로 보이는 공개 값이라 VITE_ 변수에 둬도 된다(비밀 값 아님, frontend.md).
 *
 * 공식 문서(Google Analytics 개발자 가이드, 2026-10-30 context7로 확인) 기준:
 * - SPA는 주소가 바뀌어도 브라우저가 새로 불러오지 않으므로 자동 page_view에 기대지 않고
 *   config에 send_page_view: false를 주고, 화면 이동마다 gtag('event', 'page_view', ...)를 직접 보낸다.
 * - allow_google_signals: false는 제3자 광고 식별자 기반 인구통계·관심분야 보고를 끈다.
 *   allow_ad_personalization_signals: false는 광고 개인화 기능을 끈다.
 *
 * 쿼리·제외 경로 보호(방침 약속: "주소 뒤에 붙는 값은 보내지 않는다", "관리자·로그인 확인 화면은 집계에서 뺀다"):
 * 우리가 보내는 page_view만 정리하면 부족하다. GA가 스스로 보내는 이벤트(user_engagement, session_start,
 * first_visit 등)는 page_location 기본값으로 document.location 전체(?q=검색어 포함)를 쓰기 때문이다. 그래서
 * - config와 화면 이동마다 gtag('set', ...)로 page_location·page_path·page_title 기본값을 정리한 값으로 바꾸고,
 * - 제외 경로에서는 GA 공식 차단 표식 window['ga-disable-<측정 ID>'] = true를 세워 gtag.js가 아무것도 보내지 못하게 한다.
 *   공개 경로로 돌아오면 false로 되돌린다. 이 표식은 gtag.js 로드 전에 먼저 있어야 하므로 초기화보다 앞서 세팅한다.
 *
 * 동의 관문(사용자 결정 2026-10-31): 방문자가 방문 통계에 동의하기 전에는 gtag.js를 불러오지 않고, dataLayer·gtag를 만들지 않으며,
 * 쿠키(_ga, _ga_*)도 만들지 않는다. 동의 상태는 setAnalyticsConsent로 주입받는다(저장·판정은 features/consent가 맡는다.
 * lib가 feature를 import하지 않게 하려고, 이쪽은 "허용됨/아님" 한 비트만 받는다). 기본값은 허용 안 됨이다.
 */

// 방문 통계에서 뺄 경로. 운영자·평가용 데모 접속이 통계를 왜곡하지 않게 하고, 로그인 콜백은 인증 값이 오가는 곳이라 보내지 않는다.
// 정확히 이 경로이거나 그 하위 경로('/admin/collect' 등)일 때 제외한다('/administrator'처럼 이름만 비슷한 경로는 제외하지 않는다).
const EXCLUDED_PATH_PREFIXES = ['/admin', '/auth/callback', '/__dev']

// gtag 스크립트 태그에 붙이는 id. 같은 스크립트를 두 번 넣지 않았는지 확인하는 표식이다
const GA_SCRIPT_ELEMENT_ID = 'ga4-gtag-script'

// 방문 통계 동의 여부. 기본은 false(동의 전). 새로고침하면 다시 false로 시작하고, 저장된 동의를 읽은 뒤 setAnalyticsConsent로 켠다
let consentGranted = false

// gtag 호출 한 번의 인자 묶음(command, 대상/이벤트 이름, 옵션 객체)
type GtagArguments = unknown[]

declare global {
  interface Window {
    dataLayer?: unknown[]
    gtag?: (...args: GtagArguments) => void
  }
}

/**
 * GA 공식 차단 표식('ga-disable-<측정 ID>')을 켜거나 끈다.
 * 이름이 측정 ID마다 달라 Window 전역 타입에 고정 필드로 선언할 수 없다. 인덱스 시그니처로 Window 전체를 넓히면
 * 다른 전역 접근의 타입 검사가 약해지므로, 좁은 캐스팅을 이 함수 한 곳에만 둔다.
 */
function setGaDisabled(measurementId: string, isDisabled: boolean): void {
  ;(window as unknown as Record<string, unknown>)[`ga-disable-${measurementId}`] = isDisabled
}

/**
 * GA가 만드는 쿠키(_ga, _ga_<컨테이너>)를 지운다. 동의 철회 때만 부른다.
 *
 * 한계(코드로 확인할 수 없는 부분):
 * - 쿠키는 만들 때의 이름·도메인·경로가 같아야 지워진다. GA는 보통 접속 주소의 가장 위쪽 등록 가능 도메인(예: ottnavi.shop)에
 *   path=/로 만든다. 그래서 현재 호스트와 상위 도메인 후보를 모두 만료시키되, 브라우저가 거부하는 후보(공개 접미사 등)는 조용히 무시된다.
 * - 쓰기 결과를 되읽어 확인하지 않는다. 지워졌는지는 호출한 쪽이 알 수 없다(HttpOnly가 아닌 GA 쿠키라 보통 지워진다).
 * - 이미 불러온 gtag.js는 새로고침 전까지 메모리에 남아 있다. 그래서 쿠키를 지우는 것만으로는 부족하고, GA 공식 차단 표식
 *   ga-disable-<측정 ID>를 켜서 이후 전송과 쿠키 재생성을 막는다. 새로고침하면 동의가 없는 상태로 시작하므로 스크립트 자체가 로드되지 않는다.
 */
function clearAnalyticsCookies(): void {
  // 1. 지울 쿠키 이름을 모은다(_ga, _ga_로 시작하는 것만. 이 서비스는 _gid 등 다른 GA 쿠키를 쓰지 않는다)
  const names = document.cookie
    .split(';')
    .map((part) => part.split('=')[0].trim())
    .filter((name) => name === '_ga' || name.startsWith('_ga_'))

  // 2. 도메인 후보를 만든다: 현재 호스트와 앞 구간을 하나씩 떼어 낸 상위 도메인(최상위 'shop' 같은 한 구간은 건너뛴다)
  const labels = window.location.hostname.split('.')
  const domains: (string | undefined)[] = [undefined] // undefined = domain 속성 없이(현재 호스트 전용 쿠키)
  for (let i = 0; i < labels.length - 1; i++) {
    const host = labels.slice(i).join('.')
    domains.push(host, `.${host}`)
  }

  // 3. 이름 × 도메인 조합마다 만료 시각을 과거로 써서 지운다
  for (const name of names) {
    for (const domain of domains) {
      document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/${domain ? `; domain=${domain}` : ''}`
    }
  }
}

/**
 * 방문 통계 동의 여부를 알려 준다. true → false로 바뀔 때(철회)만 전송 차단과 쿠키 삭제를 한다.
 * 같은 값을 다시 넣으면 아무 일도 하지 않는다(화면 이동마다 불려도 안전하다).
 * 켜는 쪽(false → true)은 여기서 스크립트를 불러오지 않는다: 현재 화면의 page_view를 보내는 trackPageView가 초기화까지 맡는다.
 */
export function setAnalyticsConsent(granted: boolean): void {
  // 1. 바뀌지 않았으면 끝
  if (granted === consentGranted) return
  consentGranted = granted

  // 2. 철회면 차단 표식을 켜고 쿠키를 지운다(측정 ID가 없으면 만들 것도 없다)
  if (!granted) {
    const measurementId = getMeasurementId()
    if (!measurementId) return
    setGaDisabled(measurementId, true)
    clearAnalyticsCookies()
  }
}

/** 설정된 측정 ID를 돌려준다. 비어 있거나 목업 모드면 null이다(= 방문 통계를 쓰지 않는다). */
export function getMeasurementId(): string | null {
  // 호출 시점에 읽는 이유: 테스트가 vi.stubEnv로 값을 바꿔 "비면 아무것도 안 한다"를 확인할 수 있다
  if (import.meta.env.VITE_USE_MOCK === 'true') return null
  const id = String(import.meta.env.VITE_GA_MEASUREMENT_ID ?? '').trim()
  return id === '' ? null : id
}

/**
 * 방문 통계로 보낼 경로를 정리한다. 제외 경로면 null이다.
 *
 * 쿼리 문자열(?q=검색어, OAuth 콜백 값 등)과 해시(#...)는 사용자가 입력한 값이 섞일 수 있어 아예 받지 않는다.
 * 그래서 location.search·hash를 쓰지 않고 pathname만 받는다. 혹시 문자열 전체가 들어와도 ?, # 뒤는 잘라 낸다.
 */
export function toTrackablePath(pathnameOrUrl: string): string | null {
  // 1. ?와 # 앞부분만 남긴다
  const path = pathnameOrUrl.split(/[?#]/)[0] || '/'

  // 2. 제외 경로면 보내지 않는다
  const isExcluded = EXCLUDED_PATH_PREFIXES.some(
    (prefix) => path === prefix || path.startsWith(`${prefix}/`),
  )
  return isExcluded ? null : path
}

/**
 * gtag 스크립트를 한 번만 넣고 초기 설정을 보낸다. 측정 ID가 없으면 아무것도 하지 않고 false를 돌려준다.
 *
 * 설정에 user_id·이메일 같은 사용자 식별값은 절대 넣지 않는다(방침 약속). GA가 쿠키(_ga)로 만드는 익명 ID만 쓴다.
 */
export function initAnalytics(): boolean {
  // 1. 측정 ID가 없거나(비어 있음·목업) 방문자가 동의하지 않았으면 스크립트도 전송도 없다
  const measurementId = getMeasurementId()
  if (!measurementId || !consentGranted) return false

  // 2. 이미 넣었으면 다시 넣지 않는다(화면을 옮길 때마다 불려도 스크립트와 config는 한 번이다)
  if (document.getElementById(GA_SCRIPT_ELEMENT_ID)) return true

  // 3. 공식 스니펫과 같은 모양: dataLayer와 gtag 함수를 먼저 만든다.
  //    gtag는 화살표 함수가 아니라 arguments 객체를 그대로 dataLayer에 넣어야 gtag.js가 알아본다
  window.dataLayer = window.dataLayer || []
  window.gtag = function gtag() {
    // eslint-disable-next-line prefer-rest-params
    window.dataLayer!.push(arguments)
  }
  window.gtag('js', new Date())
  // 최초 config 시점의 자동 이벤트에도 쿼리가 실리지 않게, 현재 경로를 정리해 기본 주소로 넣는다.
  // 제외 경로면 null이라 키를 생략한다(실제로는 trackPageView가 제외 경로에서 여기까지 오지 않는다)
  const cleanPath = toTrackablePath(window.location.pathname)
  window.gtag('config', measurementId, {
    ...(cleanPath !== null && {
      page_location: `${window.location.origin}${cleanPath}`,
      page_path: cleanPath,
      page_title: cleanPath,
    }),
    send_page_view: false, // SPA라 화면 이동마다 직접 보낸다(trackPageView)
    allow_google_signals: false, // 광고 식별자 기반 인구통계·관심분야 수집 끔
    allow_ad_personalization_signals: false, // 광고 개인화 끔
  })

  // 4. 스크립트를 비동기로 넣는다
  const script = document.createElement('script')
  script.id = GA_SCRIPT_ELEMENT_ID
  script.async = true
  script.src = `https://www.googletagmanager.com/gtag/js?id=${encodeURIComponent(measurementId)}`
  document.head.appendChild(script)
  return true
}

/**
 * 화면 이동 한 번을 page_view로 보낸다. 측정 ID가 없거나 제외 경로면 보내지 않는다.
 * page_location·page_path에는 쿼리·해시를 뺀 경로만 싣는다. page_title도 경로로 둔다:
 * 기본값인 document.title은 검색 화면에서 검색어를 담고, 화면이 lazy로 늦게 뜨면 이전 화면 제목일 수도 있기 때문이다.
 *
 * @param pathname React Router의 location.pathname(쿼리·해시 제외)
 */
export function trackPageView(pathname: string): void {
  // 1. 측정 ID가 없거나 동의 전이면 끝(스크립트도 표식도 만들지 않는다: 목업·로컬·동의 전은 전역 변경 0건.
  //    철회 뒤 표식이 false로 되돌아가지 않도록, 표식을 만지는 3번보다 앞에서 막는다)
  const measurementId = getMeasurementId()
  if (!measurementId || !consentGranted) return

  // 2. 보낼 경로를 정리한다(제외 경로면 null)
  const path = toTrackablePath(pathname)

  // 3. GA 차단 표식을 맞춘다. 제외 경로면 켜고 공개 경로로 돌아오면 끈다.
  //    gtag.js가 로드되기 전에도 먼저 있어야 하므로 초기화(5번)보다 앞에 둔다
  setGaDisabled(measurementId, path === null)

  // 4. 제외 경로에서는 GA를 초기화하지도 않는다
  if (path === null) return

  // 5. 아직 초기화 전이면 초기화한다(실패하면 끝)
  if (!window.gtag && !initAnalytics()) return

  // 6. 이후 모든 이벤트(GA 자동 이벤트 포함)의 기본 주소를 정리한 값으로 바꾼 뒤 page_view를 보낸다
  const pageParams = {
    page_location: `${window.location.origin}${path}`,
    page_path: path,
    page_title: path,
  }
  window.gtag!('set', pageParams)
  window.gtag!('event', 'page_view', pageParams)
}
