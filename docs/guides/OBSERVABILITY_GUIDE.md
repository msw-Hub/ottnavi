# 관측(모니터링) 가이드

> 사용자가 요청해 만든 설명 문서다. 근거 문서가 아니고 코드가 기준이다(결정은 `docs/HANDOFF.md`).
> 코드 흐름, 문법, Grafana·Loki를 쓰는 데 필요한 개념과 설계 결정만 둔다. 계정·화면 조작, 작업 순서, 개인 환경 문제는 쓰지 않는다.

## 1. 무엇을 보내고 어떻게 보내나

운영 서버(Cloudtype, 메모리 1GB)의 상태를 Grafana Cloud(무료)로 보내 그래프와 검색으로 본다(Task 096~099). 데이터는 세 종류다.

| 종류 | 서버에서의 예 | 특징 |
|---|---|---|
| 지표(metric) | 메모리 650MB, 요청 수 120건/분, 오류 3건 | 작고 가벼워 그래프로 오래 보관한다. "지금 이상하다"를 알려 준다 |
| 로그(log) | `알림 발송에 실패했다: outboxId=17, 근본원인=...` | 양이 많다. "왜 그런가"를 읽을 때 쓴다 |
| 추적(trace) | 요청 하나가 컨트롤러 → DB → Redis를 거친 시간 | 느린 곳을 찾을 때 쓴다(선택, 4단계) |

| 보내는 것 | 방법 | 도착지 | 단계 |
|---|---|---|---|
| 지표 | Micrometer OTLP(60초마다) | Grafana Mimir | 2단계 |
| 로그 | Loki4j 2.0.x(Logback의 출력 장소) | Grafana Loki | 3단계 |
| 추적 | OpenTelemetry | 미정 | 4단계(운영 메모리를 잰 뒤 결정) |

OTLP와 Loki4j는 고르는 관계가 아니라 **둘 다 쓰는 관계**다. OTLP는 데이터를 보내는 통신 규격(OpenTelemetry Protocol)이고 Loki4j는 로그를 Loki로 보내는 라이브러리다. OTLP로 로그도 보낼 수 있지만 로그 전송 설정과 마스킹을 Logback에서 다루기 쉽고 지표 전송과 서로 영향을 주지 않아서 로그에는 Loki4j를 쓴다. 모든 전송은 `observability` 프로필이 켜졌을 때만 동작하고, 프로필이 없으면 지금과 똑같다.

| 단계 | 내용 | 상태(2026-10-31) |
|---|---|---|
| 1 | 로그 정리 + 메모리 게이지 | 커밋 `7bd6b30` |
| 2 | OTLP 지표 전송(허용 목록 필터) | 커밋 `5406e22` |
| 3 | Loki 로그 전송 + 마스킹 + 방침 문구, GA 방문 통계 | 구현·검증 완료, 미커밋 |
| 4 | 추적(선택) | 시작 전 |
| 5 | 문서(TECH, `.env.example`) | 시작 전 |

## 2. 용어

| 용어 | 뜻 |
|---|---|
| Micrometer | 서버 안에서 지표를 모으는 라이브러리. `MeterRegistry`가 숫자를 담는 공용 저장소다 |
| 카운터 / 게이지 | 카운터는 횟수를 세며 계속 늘기만 한다(재시작하면 0). 게이지는 읽을 때마다 그 순간의 값을 돌려준다(예: 메모리) |
| 태그 / 시계열 | 태그는 지표에 붙이는 꼬리표(`bucket=public`). "지표 이름 + 태그 조합" 하나가 시계열 하나이고, 무료 한도가 시계열 개수 기준이라 태그 값이 많아지면 안 된다 |
| 프로필 | 서버를 켤 때 붙이는 이름표. 이름표에 해당하는 설정·코드만 적용된다. `prod`면 `observability`가 꺼진 것(지금과 같음), `prod,observability`면 켜진 것 |
| 어펜더(appender) | Logback에서 **로그를 내보내는 출구**. 한 줄의 로그가 연결된 출구마다 전달되고, 출구마다 필터와 글 모양(패턴)이 따로다 |
| `logback-spring.xml` | Java 코드가 아니라 **Logback의 설정 파일**. 어떤 출구로 무엇을 내보낼지를 정하므로 "무엇이 외부로 나가는가"를 정한다. 새 출구·로거별 수준 필터·글 모양 가공은 Logback의 XML 형식으로만 만들 수 있어 `application*.yml` 규칙의 예외로 둔다 |
| 허용 목록 | 보낼 지표 이름을 정해 두고 나머지는 보내지 않는 방식(Micrometer `MeterFilter`) |
| 마스킹 | 로그 문장 안의 이메일 모양 글자를 `***`로 바꾸는 처리 |

어펜더 두 개의 차이:

| 출구 | 내보내는 곳 | 글 모양 | 필터 |
|---|---|---|---|
| `CONSOLE` | Cloudtype 로그 창 | Boot 기본 형식, 이메일과 스택 전체 | 없음 |
| `LOKI` | Grafana Loki | 이메일 `***`, 스택 없음, 근본원인 클래스 이름만 | `LokiLogFilter` |

## 3. Grafana·Loki를 쓰는 데 필요한 개념

### 3-1. 연결 값(환경 변수 5개)

| 환경 변수 | 내용 |
|---|---|
| `GRAFANA_OTLP_ENDPOINT` | 지표 전용 전체 주소 `https://otlp-gateway-<리전 코드>.grafana.net/otlp/v1/metrics`. 공식 문서에서 신호별 주소는 경로(`/v1/metrics`)를 직접 붙이게 되어 있다. 이 스택의 리전은 일본(`prod-ap-northeast-0`) |
| `GRAFANA_OTLP_AUTH` | `Basic ` + Base64(`<OpenTelemetry Instance ID>:<토큰>`). 끝에 줄바꿈이 붙으면 인증이 실패한다(공식 가이드) |
| `GRAFANA_LOKI_URL` | Loki 호스트 + `/loki/api/v1/push` |
| `GRAFANA_LOKI_USER` | Loki 인스턴스 번호(숫자) |
| `GRAFANA_LOKI_TOKEN` | 쓰기 권한 토큰 |

지표의 Instance ID와 Loki 사용자 번호는 **서로 다른 숫자**다. Grafana 사용량 조회 결과의 라벨에서 `id`가 Loki 번호이고 `stack_id`가 OpenTelemetry Instance ID와 같다. 값은 코드·설정·문서에 쓰지 않고 환경 변수로만 받는다.

### 3-2. 토큰과 권한

- **접근 정책(access policy)**은 권한(scope)과 영역(realm)으로 이루어지고, 토큰은 정책에 속해 그 권한을 갖는다. 토큰은 서비스(지표·로그)에 묶이지 않고 **권한에 묶인다**.
- 쓰기 권한 이름은 `metrics:write`, `logs:write`, `traces:write`(공식 문서)다.
- **결정**: 정책 하나(이 스택만, `metrics:write`와 `logs:write`만)와 토큰 하나를 지표와 로그 양쪽에 쓴다. 쓰기 전용이라 최소 권한이고 비밀 값이 하나다.
- **함정**: 각 카드의 "Generate now"는 쓰지 않는다. Loki 카드의 생성 창은 **읽기 전용 권한**(`logs:read` 등)을 주고, OpenTelemetry 카드의 창은 권한을 고를 수 없이 쓰기 권한을 한꺼번에(`alerts:write`, `rules:write` 포함) 준다.
- 토큰에는 만료일(2027-10-15)이 있다. 만료되면 지표와 로그 전송이 조용히 멈춘다.

### 3-3. Loki의 라벨과 스트림

Loki는 **라벨 조합 하나가 스트림 하나**다. 라벨 값이 다양해지면(사용자 번호, IP, 이메일) 스트림이 폭증해 한도와 성능이 무너지고 개인정보도 샌다. 그래서 라벨은 `app`, `env`, `level` 세 개만 쓰고, `structuredMetadata`를 꺼서 스레드·로거·MDC 같은 부가 정보를 보내지 않는다(Loki4j 2.0의 기본값은 MDC까지 보낸다). 활성 스트림 수는 `grafanacloud-usage` 데이터 소스의 `grafanacloud_logs_instance_active_streams`로 본다.

### 3-4. 무료 한도와 설계

| 항목 | 내용 | 확인 수준 |
|---|---|---|
| 보관 기간 | 지표·로그·추적 14일 | 공식 무료 요금 카드 |
| 스택 | 무료 계정 1개, 리전은 만든 뒤 변경 불가 | 공식 문서 |
| 가입 체험 | 14일 체험 후 사용 한도가 있는 무료 플랜 | 공식 가입 문서 |
| 활성 시계열 10,000개, 로그 월 50GB | 무료 한도 | 검색 요약(공식 페이지에서는 숫자 미확인) |
| 한도 초과 | 초과분은 버려지고 과금 없음 | 검색 요약 |

**설계 방침: 무료 한도 안에서 동작한다.** 지표는 허용 목록으로 약 250~450개 시계열(추정)만 보내고, 로그는 `com.ottnavi` INFO 이상과 라이브러리 WARN 이상만 보내며, Grafana가 받지 못해도 앱은 영향받지 않는다(3-5). 사용량은 공식 사용량 문서의 `grafanacloud_instance_active_series`(활성 시계열), `grafanacloud_instance_active_otlp_series`(OTLP로 들어온 시계열), `grafanacloud_instance_samples_per_second`(60을 곱하면 분당 샘플 수)로 본다. 데이터를 보내기 전에는 이 지표가 목록에 보이지 않을 수 있다(원인은 데이터가 없어서일 것으로 추정, 문서에 조건은 적혀 있지 않다).

### 3-5. 전송이 실패하면

| 경로 | 동작 |
|---|---|
| 지표(OTLP) | 별도 스레드(`otlp-metrics-publisher-N`)가 전송한다. 실패하면 그 스레드가 `WARN Failed to publish metrics to OTLP receiver`만 남기고 서버 동작과 지표 기록은 계속된다(연결 불가 주소로 시험). 타임아웃 기본값은 연결 1초, 읽기 10초(Boot 소스). 누적(CUMULATIVE) 방식이라 한 번 실패해도 다음 전송에 이어질 것으로 보나 실측은 못 했다 |
| 로그(Loki4j) | 전송 큐(4MB)가 가득 차면 **새 로그를 버리고 호출 스레드는 막히지 않는다**(소스와 시험으로 확인). 연결 실패·429·503은 최대 2회 재시도, 500은 재시도 없이 버린다 |
| 기동 시(로컬 전체 기동으로 확인) | 프로필을 켠 채 **환경 변수가 비면 앱이 기동에 실패한다**(Loki 어펜더가 빈 주소로 `IllegalArgumentException`, 종료 코드 1). 값은 있는데 연결이 안 되면(닫힌 포트) 정상 기동하고(10.8초, `/actuator/health` 200) 지표는 1분마다 WARN, 로그는 배치마다 재시도 2회 뒤 버린다. 값이 빠진 설정을 조용히 넘기지 않고 멈추는 쪽으로 정했다 |

## 4. 개인정보 설계

**원칙**: 이메일·이름·IP·토큰은 로그에 담지 않는다. 마스킹은 실수로 섞인 것을 거르는 안전망일 뿐 면허가 아니다. 내부 숫자 `userId`는 로그에 허용한다(방침에 명시).

### 4-1. 지표

지표는 태그 값을 코드가 정한 고정 값으로만 써서 마스킹이 필요 없다(`bucket`은 public·single_fetch·demo_login, `result`는 success·failure 등). 방어는 세 겹이다. 허용 목록으로 보낼 지표 이름을 제한하고, 지금 코드의 태그가 전부 닫힌 값이며, 새 지표는 리뷰에서 거른다. `OtlpMetricsAllowFilter`가 `userId`, `user_id`, `user.id`, `ip`, `client_ip`, `email`, `query` 태그 키가 붙은 지표를 이름과 상관없이 거부하지만, 이것은 **이름 목록 방식이라 `phone`처럼 목록에 없는 키나 허용된 키의 값에 넣은 개인정보는 못 막는다.**

### 4-2. 로그

- **스택 트레이스는 Loki로 보내지 않는다.** 에러 로그의 스택 트레이스에는 라이브러리(메일, DB, JSON 처리기)가 만든 예외 메시지가 붙고 거기에 이메일이나 입력값이 섞일 수 있다. 예외 객체를 통째로 남기는 곳이 최소 10곳이다. Loki에는 우리가 쓴 메시지와 **근본원인 클래스 이름**만 가고, 전체 스택은 Cloudtype 콘솔에만 남는다. 대가로 Grafana에서는 에러가 난 코드 줄을 못 보고 정밀 디버깅은 콘솔에서 한다.
- **마스킹은 영문·숫자 이메일만** 잡는다. 한글 로컬 파트와 이름은 못 가린다.
- 기존 로그 점검 결과(`src/main`의 로그 문장 174개와 백엔드 전체 테스트 로그):

| 점검 | 결과 |
|---|---|
| 방문자 IP(`RequestIpLoggingFilter`) | DEBUG로만 남고 기본 수준(INFO)에서는 출력되지 않으며 Loki에도 가지 않는다 |
| 이메일·토큰을 직접 찍는 로그 | 키워드 검색으로 찾지 못함 |
| `e.getMessage()`를 남기는 곳 4곳 | 우리 코드나 TMDB가 만든 메시지로 보이나 내용은 일일이 확인하지 못함 |
| **Hibernate DB 오류 로그** | 중복 키 오류가 `Detail: Key (컬럼)=(값) already exists.` 줄을 값과 함께 남긴다. `users` 테이블의 이메일(`uk_users_email`)과 구글 계정 고유 번호(`uk_users_oauth`의 `oauth_subject`)가 해당하고, 고유 번호는 마스킹이 못 가린다. **`org.hibernate.orm.jdbc.error` 로거를 Loki 전송에서 제외**했다(콘솔에는 그대로 남음) |

한계: 이 점검은 테스트가 만든 로그 범위다. 운영에서만 나는 라이브러리 로그는 보지 못했다.

## 5. 코드 흐름

### 5-1. Redis 장애 때 로그 줄이기 (1단계)

`PublicRateLimiter.check`(단건·데모 제한기도 같은 모양)가 Redis에서 버킷을 꺼내다 실패하면, 같은 경고가 쌓이지 않게 **장애당 처음 1건만 상세, 이후 1분마다 건수 요약**만 남긴다.

| 순서 | 위치 | 하는 일 |
|---|---|---|
| 1 | `check` 맨 위 | 최근 5초 안에 오류가 있었으면 Redis를 부르지 않고 통과(fail-open). `failureWindow`가 이 5초를 센다. 거부형(단건·데모)은 이때 429를 던진다 |
| 2 | `try`의 Redis 호출 | 성공하면 바로 아래 `storeErrorLog.markRecovered()`로 장애 상태를 끝낸다 |
| 3 | `catch` | `failureWindow.markFailed()`(5초 구간 시작) → 카운터 +1 → `storeErrorLog.recordError()` 호출 |
| 4 | `recordError` 결과 | `-1`(`FIRST_ERROR`)이면 상세 WARN, 양수 N이면 "최근 1분간 N건" WARN, `0`이면 로그 없음 |

장애 중에는 대략 5초마다 한 번씩만 `catch`에 들어온다(그 사이는 1번에서 Redis를 안 부르므로 오류도 안 난다). 복구되면 `markRecovered`가 초기화해서 다음 장애도 처음 1건부터 남긴다. 장애 횟수 자체는 로그가 아니라 `ratelimit.store_error{bucket=...}` 카운터가 매번 센다. 표준 필터(Logback `DuplicateMessageFilter`)는 장애가 끝난 뒤 다시 장애가 나도 안 찍힐 수 있고 요약이 없으며 앱 전체 로그에 적용되고 모든 로그 호출마다 실행되어 쓰지 않았다.

### 5-2. 메모리 게이지 (1단계)

서버가 켜질 때 `SystemMemoryMetrics.bindTo`가 게이지 `ottnavi.process.rss`, `ottnavi.container.memory.used`의 "읽는 방법"만 등록한다. 값은 누가 지표를 읽을 때(2단계 전송이 60초마다) `SystemMetricsService.readProcessRssBytes` 등이 `/proc`, cgroup 파일에서 읽어 돌려준다. 읽을 수 없는 환경(Windows·로컬)에서는 `NaN`이라 오류 없이 "값 없음"이다.

### 5-3. 지표 전송 (2단계)

1. **켜질 때**: `application.yml`에 `enabled: false`가 공통으로 있다. `observability` 프로필이 켜지면 `application-observability.yml`이 `enabled: true`, `step: 60s`, `url`, `Authorization`(환경 변수)로 덮어쓴다. 그러면 Boot가 `OtlpMeterRegistry`를 만들고 `@Profile("observability")`인 `ObservabilityConfig`가 거기에 `OtlpMetricsAllowFilter`를 붙인다. 필터는 OTLP 저장소에만 붙으므로 `/actuator` 지표는 그대로다. 의존성 `spring-boot-opentelemetry`가 없으면 Boot의 OTLP 자동 구성이 에러 없이 빠져 전송이 안 된다(로컬 시험으로 확인).
2. **지표 하나가 등록될 때**: `OtlpMetricsAllowFilter.accept`가 이 순서로 판단한다.

    | 순서 | 판단 | 결과 |
    |---|---|---|
    | 1 | 태그 키에 개인정보 성격의 이름이 있으면 | 거부 |
    | 2 | 이름이 `ALLOWED_NAMES`와 정확히 같으면 | 통과 |
    | 3 | 이름이 `ALLOWED_PREFIXES`로 시작하면 | 통과 |
    | 4 | 나머지 | 거부 |

    허용 이름: `ottnavi.process.rss`, `ottnavi.container.memory.used`, `ratelimit.rejected`, `ratelimit.store_error`, `process.cpu.usage`, `system.cpu.usage`, `http.server.requests`, `tmdb.call`, `plan.calculate`, `notification.send`, `llm.call`, `llm.call.duration`. 허용 접두사: `jvm.memory.`, `jvm.gc.`, `jvm.threads.`, `hikaricp.connections`, `spring.batch.job`. 보낼 지표를 늘리려면 `ALLOWED_NAMES`에 한 줄 추가한다.
3. **60초마다**: 별도 스레드가 필터를 통과한 지표의 현재 값을 모아 `url`로 POST한다(`Authorization` 헤더 포함). 요청 처리 스레드와 분리돼 있어 Grafana가 느려도 사용자 요청은 기다리지 않는 구조다.

### 5-4. Loki 로그 전송 (3단계)

| 순서 | 위치 | 하는 일 |
|---|---|---|
| 1 | `logback-spring.xml` | 서버가 켜질 때 읽는다. 프로필이 꺼져 있으면 루트 INFO + 콘솔만(`!observability`), 켜져 있으면 콘솔 + `LOKI` 어펜더를 만든다. `base.xml`은 파일 어펜더까지 켜서 쓰지 않고 `defaults.xml`·`console-appender.xml`만 가져와 프로필을 끄면 콘솔 형식이 Boot 기본과 같다 |
| 2 | 로그 한 줄 발생 | 콘솔 어펜더는 이메일·스택까지 전체를 그대로 남기고, `LOKI` 어펜더는 아래 3~6을 거친다 |
| 3 | `LokiLogFilter.decide` | `com.github.loki4j`·`io.micrometer.registry.otlp`·`org.hibernate.orm.jdbc.error` 로그는 수준과 상관없이 거부(앞 둘은 전송 실패 로그가 다시 전송되는 순환 방지), `com.ottnavi`는 INFO 이상, 그 밖은 WARN 이상만 통과 |
| 4 | 메시지 패턴 | 이메일 모양 글자를 `***`로 바꾸고, 예외가 있으면 ` rootCause=클래스이름`을 붙이고, 스택 자동 추가는 막는다(`%nopex`) |
| 5 | `LokiRootCauseConverter.convert` | 예외 `cause`를 가장 안쪽까지(최대 10단계) 따라가 **클래스 이름만** 돌려준다. 예외 메시지는 쓰지 않는다 |
| 6 | `Loki4jAppender` | 라벨 `app`, `env`, `level`만 붙여 내부 큐에 넣고 바로 돌아간다. 별도 스레드가 64KB 또는 5초마다 `/loki/api/v1/push`로 POST한다 |

로그 한 줄의 예(가상): `log.error("알림 처리 중 예외가 났다: outboxId=17, 수신자=hong@example.com", e)`에서 e의 가장 안쪽 원인이 `SocketTimeoutException`이면, 콘솔에는 메시지 전체와 스택이 남고 Loki에는 `... 수신자=*** rootCause=java.net.SocketTimeoutException`만 간다. `drainOnStop=true`라 종료할 때 큐에 남은 로그를 마저 보낸다(Loki 장애 중 종료가 길어질 수 있다는 추정은 운영 배포 때 종료 시간으로 확인한다).

## 6. 문법: 물어본 것만

### 6-1. `boolean` 필드의 기본값과 `try`/`catch`로 상태를 바꾸는 방식

Java의 `boolean` 필드는 값을 안 주면 자동으로 `false`다. `StoreErrorLog.inFailure`는 처음에 "장애 아님"이다. 값을 바꾸는 `if` 조건이 따로 없고, **어디서 호출되느냐가 판단 기준**이다.

```java
try {
    ... bucket.tryConsumeAndReturnRemaining(1);   // Redis 호출
    storeErrorLog.markRecovered();                // 위가 성공했을 때만 여기에 도달 → inFailure = false
} catch (RuntimeException e) {                    // 위가 실패했을 때만 여기에 도달
    long result = storeErrorLog.recordError();    // 첫 오류면 inFailure = true
}
```

`try` 안에서 예외가 나면 그 줄 아래를 실행하지 않고 `catch`로 간다. `markRecovered`를 `try` **맨 위**에 두면 안 되는 이유는 그 시점에는 성공 여부를 모르기 때문이다. 장애 중에 요청마다 맨 위에서 초기화되면 오류마다 "첫 오류"로 판단돼 상세 로그가 매번 찍힌다.

### 6-2. `volatile`

여러 스레드가 같은 변수를 읽고 쓸 때, 한 스레드가 바꾼 값을 다른 스레드가 못 볼 수 있다(스레드가 값을 자기 캐시에 베껴 두기 때문). `volatile`을 붙이면 항상 메인 메모리에서 직접 읽고 써서 바뀐 값을 바로 본다. 처음에는 "여러 요청이 동시에 건드리는 값이라는 표시"로 읽으면 된다.

| 필드 | volatile | 이유 |
|---|---|---|
| `inFailure` | 있음 | `markRecovered`가 락 없이 읽기 때문 |
| `lastLoggedAtNanos`, `pendingCount` | 없음 | 항상 `synchronized` 안에서만 읽고 쓰므로 |

### 6-3. `synchronized`

`volatile`은 최신 값을 보게 할 뿐이고, `pendingCount++`처럼 "읽기 → 더하기 → 쓰기"는 동시에 하면 하나가 사라진다. `synchronized`는 구역에 한 번에 한 스레드만 들어가게 하는 문이다. `synchronized long recordError()`는 메서드 전체, `synchronized (this) { ... }`는 괄호 안만 잠근다. `markRecovered`는 먼저 락 없이 `if (!inFailure) return;`으로 거르고(평소엔 여기서 끝나 빠르다), 장애에서 막 회복한 드문 순간에만 락을 잡는다.

### 6-4. `meterRegistry.counter(...).increment()`

```java
meterRegistry.counter("ratelimit.store_error", "bucket", "public").increment();
```

이름과 태그 조합에 해당하는 카운터를 찾고 없으면 만들어 1 올린다. `meterRegistry`는 스프링이 하나만 만드는 공용 저장소라 세 제한기가 같은 것을 쓴다. 값은 서버가 켜진 뒤 누적이고, "분당 몇 건"은 Grafana가 누적값의 차이로 계산한다.

### 6-5. `recordError`, `pendingCount`, `FIRST_ERROR`

- `recordError`는 오류를 세는 것이 아니라 **"이번 오류를 로그에 남길까"를 판단**해 값을 돌려준다. `pendingCount`는 "요약 로그에 아직 안 쓴 건수"라서 요약을 쓰면 0으로 돌아간다(첫 오류는 포함하지 않는다). 실제 총 횟수는 6-4의 카운터다.
- `FIRST_ERROR`(-1)는 "이 장애의 첫 오류이니 상세 로그를 남겨라"라는 뜻이지 "검사를 건너뛰라"가 아니다. 검사를 건너뛰는 것은 `failureWindow`와 `catch` 뒤 `return`이다. `0`(로그 없음)과 양수(요약 건수)가 이미 다른 뜻이라 `-1`을 따로 쓰고, 숫자 대신 이름 `FIRST_ERROR`를 붙였다.

| 돌려주는 값 | 의미 | 호출한 쪽의 동작 |
|---|---|---|
| `-1` (`FIRST_ERROR`) | 이 장애의 첫 오류 | 상세 WARN |
| `0` | 이어지는 오류, 마지막 로그 뒤 1분 안 지남 | 로그 없음 |
| 양수 N | 1분이 지났고 쌓인 N건 | 요약 WARN |

## 7. GA 방문 통계 (프론트 코드와 방침)

방문자 수를 보려고 구글 애널리틱스(GA4)를 쓴다. 코드는 `frontend/src/lib/analytics.ts`, `hooks/usePageViewTracking.ts`, `RootLayout.tsx`의 한 줄이다.

| 동작 | 내용 |
|---|---|
| 켜지는 조건 | 환경 변수 `VITE_GA_MEASUREMENT_ID`가 있고 목업 모드가 아닐 때만. 비면 스크립트도 전송도 없다 |
| 화면 이동 | SPA라 자동 집계에 기대지 않고 `send_page_view: false`로 막은 뒤 라우터의 경로가 바뀔 때마다 `page_view`를 직접 보낸다 |
| 보내는 값 | 경로만(쿼리·해시 제거). `user_id`는 설정하지 않는다 |
| 광고 관련 | `allow_google_signals: false`, `allow_ad_personalization_signals: false` |
| 제외 경로 | `/admin`, `/auth/callback`, `/__dev` |

GA 쪽 설정이 코드와 방침에 영향을 주는 점:

1. **GA가 보여 주는 설치 스니펫은 붙이지 않는다.** 붙이면 환경 변수와 상관없이 항상 로드되고, 첫 화면 조회가 쿼리 포함 주소로 자동 전송되며, 위의 광고 끄기와 제외 경로가 빠진다. 스트림 생성 직후 "수집 대기"로 보이는 것은 일반적이다.
2. **향상된 측정은 꺼야 한다.** 켜 두면 GA가 코드 없이 이벤트를 자동 수집한다: 페이지 조회, 스크롤, 이탈 링크 클릭, **사이트 검색**(주소의 `q`·`s`·`search`·`query`·`keyword` 값을 `search_term`으로 수집), 동영상, 파일 다운로드, 양식 상호작용. 자동 수집은 실제 주소를 읽으므로 우리 코드가 쿼리를 떼고 보내는 것과 별개로 검색어가 간다. 방침의 "검색어처럼 주소 뒤에 붙는 값은 보내지 않습니다"가 사실이려면 꺼져 있어야 한다.
3. **데이터 보관 기간은 사용자·이벤트 수준 데이터에만 적용된다**(쿠키·사용자 식별자·광고 식별자에 묶인 데이터, 탐색·깔때기 보고서에 영향). 표준 집계 보고서는 영향받지 않는다. 일반 속성에서 2개월 또는 14개월이고, "새 활동 시 재설정"을 켜면 활동하는 사용자는 만료되지 않고 끄면 기간이 끝나면 삭제된다. 방침 문구는 이 범위를 반영해야 한다. 우리 설정은 **이벤트 데이터 2개월, 사용자 데이터 14개월, "새 활동 시 재설정" 끔**이고 방침에 그대로 쓴다(집계 통계는 이 기간이 지나도 남을 수 있다고 함께 적는다).
4. **Google 신호와 광고 개인 최적화는 끈다.** Google 신호는 구글 계정에 로그인하고 광고 개인 최적화를 켠 사용자의 데이터를 GA 데이터와 연결해 인구통계·관심분야 보고서와 기기 간 보고서를 만드는 기능이다. 광고 개인 최적화 설정(지역별 스위치)은 끄면 그 지역 이벤트가 광고 개인 최적화에 쓰이지 않을 뿐 **수집이나 측정을 막지는 않는다.** 광고 목적으로 쓰지 않는다는 방침과 맞게 코드와 계정 양쪽에서 끈다.
5. **계정의 데이터 공유 설정(구글 제품 및 서비스, 모델링 기여 및 비즈니스 통계, 기술 지원, 비즈니스를 위한 추천)은 모두 동의하지 않는다.** 공식 문서에 모두 끄면 GA 데이터는 GA 서비스를 제공하고 유지하는 데에만 쓰인다고 되어 있어 방침(방문 통계 목적으로만 사용)과 일치한다. "구글 제품 및 서비스"를 켜면 Google이 데이터를 자체 제품 개선에 쓴다.
6. GA는 태그 매니저(GTM)를 쓰지 않는다. GTM을 쓰면 수집 내용을 코드가 아니라 GTM 화면에서 바꿀 수 있어 코드와 방침에서 벗어날 수 있다.
7. 광고·추적 차단 도구를 쓰는 방문자는 분석 도메인으로 가는 요청이 막혀 집계에서 빠지므로 GA의 방문자 수는 실제보다 적게 나올 수 있다. `http.server.requests`는 요청 횟수이지 사람 수가 아니므로 둘의 숫자는 원래 다르다.

## 8. 결정과 미확인

### 8-1. 결정

- 지표는 Micrometer OTLP, 로그는 Loki4j **2.0.3**(Boot 4.1.1의 Logback 1.5.38과 맞는 계열, 2.1은 Logback 1.6 필요). 켜고 끄기는 `observability` 프로필, 프로필이 없으면 지금과 동일.
- 097·099용 새 API는 없다(기존 수집 실행·알림 요약·시스템 지표 API + Grafana 링크). DEMO 계정에도 Grafana 링크를 보인다.
- 의존성 `spring-boot-opentelemetry`는 유지한다(없으면 OTLP 자동 구성이 빠짐). `llm.call`, `llm.call.duration`은 허용 목록에 넣는다(태그 `operation`·`result`는 닫힌 값).
- Loki에는 `com.ottnavi` INFO 이상 + 라이브러리 WARN 이상만 보내고, 스택 트레이스는 보내지 않으며, 이메일 모양 글자는 가린다. `drainOnStop=true`.
- 로그 줄이기는 직접 만든 코드(`StoreErrorLog`)를 쓴다. 기동 직후 Redis 미리 연결은 이번에 하지 않는다.
- 개인정보처리방침에 로그의 외부 보관·마스킹, 회원 번호 기록, 방문 통계(Google Analytics), 처리 위탁(Grafana Labs, 서버 위치 일본)을 반영한다. 동의 안내 창은 만들지 않았고 한국 법령상 필요한지는 확인하지 못했다.

### 8-2. 메모리 영향 측정 계획

`spring-boot-opentelemetry`가 OpenTelemetry SDK jar를 들여오므로 1GB 서버의 영향을 잰다. 관측 코드를 프로필 끈 채로 배포해 RSS를 30분·24시간 뒤 재고, 프로필을 켜고 재배포해 같은 시점에 비교한다. 중단 기준은 RSS +100MB 또는 컨테이너 사용률 85%. 기준값은 새로 배포한 직후 약 496MiB, 2일 뒤 약 738MB다(`ps -o pid,rss,etime,args -C java`, `grep -E "VmRSS|VmHWM" /proc/<PID>/status`). 같은 경과 시간끼리 비교한다.

### 8-3. 아직 확인하지 못한 것

- 실서버 시계열 개수(추정 250~450개), `spring.batch.job` 지표가 나오는지, 의존성 추가의 실제 메모리·기동 시간 영향
- 실제 Grafana Cloud로의 전송(Cloudtype에서 나가는 통신, 응답이 없거나 4xx·5xx일 때)- 실제 Redis 장애에서 로그가 줄어드는지, 게이지가 실제 Linux 컨테이너에서 도는지
- 운영에서만 나는 라이브러리 로그에 개인정보가 섞이는지, `drainOnStop=true`일 때 Loki 장애 중 종료 시간
- GA4가 보내는 항목의 완전한 목록, 처리·보관 서버 위치, 한국 법령상 동의 절차

## 9. 갱신 이력

| 날짜 | 내용 |
|---|---|
| 2026-10-30 | 문서 신설(1·2단계) |
| 2026-10-31 | 질문 일지 형태를 기술 핵심 중심으로 재구성: 코드 흐름, 문법, Grafana·Loki 개념, 개인정보 설계, GA 방문 통계. 계정·화면 조작과 개인 환경 문제는 제외 |
| 2026-10-31 | 로컬 전체 기동 결과 반영(3-5절): 환경 변수가 비면 기동 실패, 연결 불가 값이면 정상 기동 |
