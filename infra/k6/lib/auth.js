// 측정용 Access Token(JWT, HS256)을 k6 안에서 직접 서명한다(Task 100).
// 서버(JwtTokenProvider)는 app.jwt.secret을 Base64 디코딩한 바이트를 HMAC 키로 쓰고, iss·exp·서명만 검사한다.
// 키 값은 컴포즈 k6 서비스의 JWT_SECRET(로컬 개발 전용 키)에서 읽고, 화면·파일에 출력하지 않는다.
import crypto from 'k6/crypto';
import encoding from 'k6/encoding';

const ISSUER = 'ottnavi';            // app.jwt.issuer 기본값
const TOKEN_TTL_SECONDS = 3 * 3600;  // 서버는 exp만 보므로 측정 시간보다 길게 서명한다

/** 환경 변수의 Base64 비밀 값을 HMAC 키 바이트(ArrayBuffer)로 바꾼다 */
function signingKey() {
  const secret = __ENV.JWT_SECRET;
  if (!secret) {
    throw new Error('JWT_SECRET 환경 변수가 없다(컴포즈 k6 서비스로 실행했는지 확인)');
  }
  // 서버의 Decoders.BASE64.decode와 같은 표준 Base64(패딩 포함)로 해석한다. format을 비우면 ArrayBuffer가 온다
  return encoding.b64decode(secret.trim(), 'std');
}

/** 사용자 id(sub)와 권한으로 서명한 JWT 문자열을 만든다 */
export function signAccessToken(userId, role = 'USER') {
  // 1. 헤더와 클레임을 base64url(패딩 없음)로 인코딩한다. 시각은 초 단위다
  const now = Math.floor(Date.now() / 1000);
  const header = encoding.b64encode(JSON.stringify({ alg: 'HS256', typ: 'JWT' }), 'rawurl');
  const payload = encoding.b64encode(JSON.stringify({
    iss: ISSUER,
    sub: String(userId),
    role: role,
    iat: now,
    exp: now + TOKEN_TTL_SECONDS,
  }), 'rawurl');
  // 2. "헤더.클레임"을 HMAC-SHA256으로 서명하고 base64url(패딩 없음)로 붙인다
  const signingInput = `${header}.${payload}`;
  const signature = crypto.hmac('sha256', signingKey(), signingInput, 'base64rawurl');
  return `${signingInput}.${signature}`;
}

/** Authorization 헤더를 포함한 요청 파라미터를 만든다 */
export function authParams(token, tags = {}) {
  return {
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    tags: tags,
  };
}
