# 도메인·HTTPS 구조: 이전 경험(EC2 직접 운영)과 ottnavi(Vercel + Cloudtype)의 차이

> 사용자가 요청해 만든 설명 문서다(2026-10-21). 근거 문서가 아니고 규칙의 근거는 PRD·TECH다. 이전 경험은 사용자의 velog 글 2편(Route53 없이 HTTPS 적용하는 서브도메인 구성, HttpOnly 쿠키 JWT의 프론트·백 분리 배포 문제)이다.
> 쓰임: Task 005(도메인 구매)·092(도메인 연결)를 할 때 "그때는 이렇게 했는데 이번엔 왜 안 하나"를 확인한다.

## 1. 한 줄 결론

이전에는 **서버를 직접 운영**해서 HTTPS와 도메인 분리를 직접 처리했다. 이번에는 **관리형 서비스 두 곳(Vercel, Cloudtype)이 HTTPS를 자동으로 맡고**, 방문자가 보는 주소가 하나라서 도메인 분리 문제도 없다. 겹치는 것은 DNS 입력과 기다리는 시간뿐이다.

## 2. 배경: 용어

| 용어 | 뜻 |
|---|---|
| DNS | 도메인 이름을 IP 주소로 바꿔 주는 전화번호부. 카페24가 `ottnavi.shop`의 전화번호부를 들고 있다 |
| A 레코드 | "이 도메인은 이 IP로 가라"는 DNS 항목 |
| CNAME | "이 도메인은 저 도메인과 같다"는 DNS 항목(서브도메인용) |
| 네임서버 | 그 도메인의 전화번호부를 관리하는 서버. 구매처(카페24) 것을 쓰거나 Vercel 것으로 옮길 수 있다 |
| 인증서(SSL/TLS) | HTTPS 접속 때 서버가 "나는 이 도메인의 진짜 주인"이라고 브라우저에 내미는 증명서. 인증기관(Let's Encrypt 등)이 발급하고 유효기간이 짧아 갱신이 필요하다 |
| TLS 종료 | HTTPS 연결을 맨 앞에서 받아 풀어 주는 곳. 여기에 인증서가 있어야 한다 |

## 3. 두 구조 비교

| 항목 | 이전 경험 | ottnavi |
|---|---|---|
| 서버 | EC2 한 대에 nginx를 직접 설치 | Cloudtype(관리형) |
| 인증서 | certbot으로 발급, cron으로 갱신, 보안그룹 443 열기 | Vercel과 Cloudtype이 각자 자동 발급·갱신 |
| 방문자가 보는 주소 | 두 개: 프론트 `www.`, 백엔드 `api.` | 한 개: `ottnavi.shop` |
| 서버 IP | 탄력적 IP를 할당·연결(연결 안 하면 과금) | 우리가 관리하는 IP가 없다 |
| DNS | 카페24 A 레코드(프론트 IP, 서버 IP) | 카페24에서 `ottnavi.shop`을 Vercel로 향하게 하는 레코드뿐 |
| 쿠키 | 도메인이 달라 쿠키 속성과 CORS를 맞춰야 했다 | 같은 주소라 해당 문제가 없다 |
| 기다림 | 약 12시간(카페24 DNS 적용) | 같은 카페24라 비슷하다고 본다 |

## 4. 왜 다른가

### 4.1 HTTPS를 직접 하지 않는 이유

- 이전: 서버가 인증서를 직접 들고 있어야 했다. EC2는 빈 컴퓨터라서 certbot 설치, 인증서 발급, 90일 안팎 주기의 갱신 cron, nginx 설정, 보안그룹 443 개방을 모두 내가 했다.
- 지금: HTTPS를 받는 곳이 두 군데이고 둘 다 서비스가 대신 한다.
  1. 방문자 → `ottnavi.shop`: **Vercel**이 TLS를 종료한다. 도메인을 추가하면 인증서를 자동으로 발급한다.
  2. Vercel → 서버: **Cloudtype 기본 주소**의 인증서를 Cloudtype이 자동 발급한다(공식 도메인 연결 문서: 프리뷰 URL과 연결한 커스텀 도메인 모두 자동 발급·적용, 수동 설정 불필요). GitHub Actions의 cron 호출도 같은 주소로 가고, 워크플로는 `https://`가 아니면 중단하게 돼 있다.
- 우리가 쓰는 도메인(`ottnavi.shop`)은 Vercel에만 연결하고, 서버 쪽에는 우리 도메인을 만들지 않는다. 그래서 certbot을 쓸 곳이 없다.

### 4.2 도메인이 하나인 이유(쿠키 문제가 없는 이유)

이전 구조는 `www.example.com`(프론트)이 `api.example.com`(백엔드)을 직접 호출했다. 브라우저에게 둘은 서로 다른 출처(origin)라서, 로그인 쿠키를 주고받으려면 쿠키 속성(`Domain`, `Secure` 등)과 CORS(`allowCredentials`, 정확한 `allowedOrigins`), 프론트의 `credentials: 'include'`를 모두 맞춰야 했다.

지금은 프론트가 서버를 직접 부르지 않는다. Vercel이 `/api/...` 요청을 대신 서버로 전달한다(rewrite).

```
브라우저  → GET https://ottnavi.shop/api/public/ott-services
Vercel    → (이 요청을 그대로 전달) GET https://{Cloudtype 기본 주소}/api/public/ott-services
              + 헤더 x-origin-secret (Vercel이 붙이는 비밀 헤더)
Cloudtype → 응답
Vercel    → 브라우저에 응답을 돌려 준다
```

브라우저가 아는 주소는 `ottnavi.shop` 하나다. Cloudtype 주소는 브라우저에 노출되지 않는다. 그래서:

- 로그인 쿠키(Refresh Token, HttpOnly, `Path=/api/auth`)는 `ottnavi.shop`의 쿠키로 저장되고 같은 주소로만 오간다.
- Google 로그인의 돌아오는 주소(`https://ottnavi.shop/api/login/oauth2/code/google`)도 같은 주소다.
- 이 구조 때문에 CORS 설정으로 다른 출처를 열어 줄 필요가 없다.

### 4.3 서버가 공개 주소를 가진 것의 보완

Cloudtype 기본 주소는 인터넷에서 직접 열 수 있다. 그래서 서버는 Vercel이 붙인 `x-origin-secret` 헤더가 없는 요청을 막는다(오리진 비밀 헤더 필터, local만 꺼짐). 이전 구조에서는 `api.` 주소가 처음부터 공개였다는 점이 다르다.

### 4.4 같은 것: DNS와 기다림

- 카페24 DNS 관리에서 레코드를 입력하는 방식은 이전과 같다(설정 → DNS 관리 → 도메인 선택 → DNS 관리 → 호스트 IP(A레코드) 관리).
- 바뀐 것은 가리키는 대상이다. 이전에는 우리 IP(프론트 IP, 탄력적 IP)였고, 이번에는 Vercel이 화면에 보여 주는 값이다. **값은 Vercel 대시보드 도메인 화면에 표시되는 것을 그대로 쓴다.**
- DNS 값이 전 세계에 퍼지는 시간이 필요하다. 이전에 약 12시간이 걸렸으므로 그 정도를 예상한다(Vercel·Cloudtype 공식 문서는 구체 시간을 적지 않았다).
- 네임서버를 Vercel로 옮기는 방법도 있지만, 그러면 이 도메인의 DNS 관리 전체가 Vercel로 간다. A 레코드만 추가하는 쪽을 추천한다.

## 5. 인증서를 직접 해야 하는 경우(참고)

| 상황 | 인증서 |
|---|---|
| Vercel에 도메인 연결 | 자동 |
| Cloudtype에 커스텀 도메인 연결 | 자동(공식 문서). 단 DNS 확인용 레코드(CNAME, 루트는 TXT도) 입력이 필요하고 Cloudflare를 쓰면 프록시를 꺼야 한다 |
| 직접 빌린 서버(EC2, VPS 등)에 nginx | 수동(certbot) |

## 6. 확인하지 못한 것

1. 카페24 DNS가 실제로 몇 시간 만에 적용되는지(이전 글의 12시간은 그때의 경험이다).
2. 카페24 DNS 화면에 `www`용 CNAME 항목이 있는지.
3. 운영에서 Vercel rewrite를 지나는 로그인(쿠키 저장, 재발급)이 실제로 되는지: Task 095 스모크 테스트에서 확인한다.
4. 도메인을 Vercel에 연결하기 전에 Google OAuth 운영 주소를 먼저 등록해도 되는지.
