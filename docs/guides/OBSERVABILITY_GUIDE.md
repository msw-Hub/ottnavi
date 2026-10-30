# 관측(모니터링) 이해 노트

> 사용자가 요청해 만든 설명 문서다. 근거 문서가 아니고 코드가 기준이다(규칙의 근거는 PRD·TECH, 결정은 `docs/HANDOFF.md`).
> 사용자가 모니터링을 직접 구현하는 것이 처음이라, **물어본 것 위주의 질문과 답**과 **기본 개념**을 섞어 둔다. 작업이 진행되면서 질문이 생기면 4절과 8절(갱신 이력)에 계속 추가한다.
> 문법은 사용자가 물어본 것만 6절에 둔다.

## 1. 관측이 무엇이고 왜 하나

운영 서버(Cloudtype, 메모리 1GB)가 지금 건강한지 볼 창구가 없다. 그래서 Grafana Cloud(무료)에 데이터를 보내 그래프와 검색으로 본다(Task 096~099).
병원 환자에 비유하면 데이터는 세 종류다.

| 종류 | 비유 | 서버에서의 예 | 특징 |
|---|---|---|---|
| 지표(metric) | 체온·혈압 같은 숫자 기록 | 메모리 650MB, 요청 수 120건/분, 오류 3건 | 작고 가벼워서 그래프로 오래 보관한다 |
| 로그(log) | 진료 기록(글) | `알림 발송에 실패했다: outboxId=17, 근본원인=...` | 원인을 읽을 때 쓰고 양이 많다 |
| 추적(trace) | 환자가 병원 안에서 거친 경로 | 요청 하나가 컨트롤러 → DB → Redis를 거친 시간 | 느린 곳을 찾을 때 쓴다(선택) |

숫자는 "지금 이상하다"를, 로그는 "왜 그런가"를 알려 주므로 둘 다 필요하다.

## 2. 전체 구조와 단계

| 보내는 것 | 방법 | 도착지 | 단계 |
|---|---|---|---|
| 지표 | Micrometer OTLP | Grafana Mimir(숫자 저장소) | 2단계 |
| 로그 | Loki4j 2.0.x(Logback에 끼우는 출력 장소) | Grafana Loki(로그 저장소) | 3단계 |
| 추적 | OpenTelemetry | 미정 | 4단계, 선택(운영 메모리를 잰 뒤 결정) |

**OTLP와 Loki4j는 고르는 관계가 아니라 둘 다 쓰는 관계다.** OTLP는 데이터를 보내는 통신 규격(OpenTelemetry Protocol)이고, Loki4j는 로그를 Loki로 보내는 라이브러리다. OTLP는 로그도 보낼 수 있는 규격이지만 이 프로젝트는 로그에 Loki4j를 쓴다(로그 전송 설정과 마스킹을 Logback 쪽에서 다루기 쉽고, 지표 전송과 서로 영향을 주지 않기 때문). 2단계와 3단계는 독립이라 한쪽이 막혀도 다른 쪽은 진행된다.

| 단계 | 내용 | 상태(2026-10-30) |
|---|---|---|
| 1 | 로그 정리 + 메모리 게이지 | 구현·커밋(`7bd6b30`) |
| 2 | OTLP 지표 전송(허용 목록 필터, 프로필 꺼지면 0건) | 구현·테스트 통과(필터 6건, 전송 4건), 미커밋 |
| 3 | Loki 로그 전송 + 개인정보 마스킹 + 개인정보처리방침 문구 | 시작 전 |
| 4 | 추적(선택) | 시작 전 |
| 5 | 문서(TECH, `.env.example`, 운영 기록) | 시작 전 |

브랜치는 `feature/q1-observability` 하나에 단계별 커밋으로 쌓고, 푸시·PR은 모든 단계가 끝난 뒤 사용자가 요청할 때 한다.

## 3. 기본 개념 풀이

| 용어 | 뜻 |
|---|---|
| Micrometer | 서버 안에서 숫자(지표)를 모으는 라이브러리. `MeterRegistry`가 숫자를 담는 공용 저장소다 |
| 카운터(Counter) | 횟수를 세는 지표. 서버가 켜진 뒤 계속 늘기만 하고 재시작하면 0부터 다시 시작한다 |
| 게이지(Gauge) | 지금 값을 읽는 지표(예: 메모리). 읽을 때마다 그 순간의 값을 돌려준다 |
| 태그 | 지표에 붙이는 꼬리표(예: `bucket=public`). 태그 조합마다 시계열이 하나씩 생긴다 |
| 시계열 | "지표 이름 + 태그 조합" 하나가 시간에 따라 변하는 값의 줄. 무료 한도가 시계열 개수 기준이라 아껴 써야 한다 |
| 프로필 | 서버를 켤 때 붙이는 이름표 스위치. 이름표에 해당하는 설정·코드만 적용된다(5절) |
| 허용 목록 | 보낼 지표 이름을 미리 정해 두고, 나머지는 보내지 않는 방식 |
| 마스킹 | 로그 문장 안의 이메일 같은 개인정보 모양을 가려서 내보내는 처리 |

## 4. 질문과 답

### 4-1. 선택과 역할

1. **OTLP와 Loki4j 중 뭘 고르나?** 고르지 않는다. 지표는 OTLP, 로그는 Loki4j로 각각 맡는다(2절).
2. **2단계(OTLP 지표 전송)의 역할은?** 서버 안에 이미 모이는 숫자(1단계의 메모리 게이지, 오류 카운터, JVM 메모리 등)를 60초마다 Grafana로 보내는 연결이다. 보내도 되는 것만 골라서 보낸다.
3. **OTLP 전송이 실패하면 값을 버리는지를 왜 알아야 하나?** 이 문제는 지표 전송에만 해당한다. Loki4j는 별개 경로다. 지표 전송이 막혔을 때 서버 요청이 영향을 받는지, 그 사이 값이 사라지는지를 알아야 운영에서 Grafana 장애가 서비스 장애로 번지지 않는지 판단할 수 있다. 2단계 로컬 확인 결과는 5-3절.
4. **모니터링에 Spring Batch 이야기가 왜 나오나?** 수집은 Spring Batch Job으로 돌아가므로 "수집이 성공했나, 얼마나 걸렸나"를 대시보드에서 보고 싶다. 그런데 Batch 지표는 기본으로 나오지 않을 수 있어(관측 장치 `ObservationRegistry`가 연결돼야 한다) 자동 연결되는지 확인이 필요하다. 연결되면 `spring.batch.job`만 보내고, 아니면 로그 검색으로 본다. 실제로 나오는지는 첫 배포 뒤 Grafana에서 확인해야 한다.
5. **"두 로그 한 줄씩(시각과 로거 이름, 비밀 없음)"은 뭔가?** 운영 로그 중 스케줄러의 "알림 발송 주기를 마쳤다"와 `/api/auth/refresh` 401, 이 두 가지의 실제 한 줄씩을 보여 달라는 요청이다. 한 줄 앞부분의 시각과 로거 이름(어느 클래스가 찍었나)을 보면 이 로그를 지금 준 코드가 찍은 것인지(수준을 바꿀 수 있는지), 배포 서버(Cloudtype) 접속 기록인지 구분할 수 있다. "비밀 없음"은 토큰·이메일 등을 가린 채로 달라는 뜻이다. 받기 전에는 해당 로그를 바꾸지 않는다.

### 4-2. 개인정보

6. **개인정보 태그 막기는 따로 만들어야 하나?** 이미 있다. `OtlpMetricsAllowFilter`가 `userId`, `user_id`, `user.id`, `ip`, `client_ip`, `email`, `query` 중 하나가 태그 키에 있으면 이름이 허용이어도 거부한다(5-3절 2번의 표 1번). 다만 **이름 목록 방식**이라 `phone`처럼 목록에 없는 키, 또는 허용된 키의 값에 개인정보를 넣는 경우는 막지 못한다. 진짜 방어는 세 겹이다. 허용 목록으로 보낼 지표 이름을 제한한다, 지금 코드의 태그가 전부 닫힌 값이다(`bucket`은 public·single_fetch·demo_login, `result`는 success·failure 등), 새 지표를 만들 때 리뷰에서 거른다.
7. **마스킹을 하면 로그에 남겨도 되나?** 아니다. 원칙은 "개인정보는 로그에 담지 않는다"이고, 마스킹은 실수로 섞인 것을 한 번 더 걸러 주는 안전망이다. 이름처럼 모양이 일정하지 않은 것은 마스킹이 못 잡으므로 1순위는 계속 "안 담기"다. 3단계에서 Loki를 켜기 전에 기존 로그를 점검하고, 개인정보처리방침에 "로그를 외부(Grafana)에 보관하며 마스킹 처리한다"를 추가한다.
8. **지표에는 마스킹을 안 하나, 지금 방식이 표준인가?** 지표는 태그 값을 코드가 정한 고정 값으로만 쓰므로 마스킹이 필요 없고, 허용 목록은 Micrometer의 `MeterFilter` 표준 기능이다. 마스킹은 자유 문장인 로그에 적용한다(3단계).

| | 지표 | 로그 |
|---|---|---|
| 개인정보가 들어오는 경로 | 태그 값 | 메시지 문장 어디든 |
| 표준 방어 | 태그에 개인정보를 쓰지 않는다 + 허용 목록 | 로그에 안 담기가 1순위, 마스킹은 안전망 |

### 4-3. 로그 줄이기 코드(1단계)

9. **이 로직은 라이브러리인가, 직접 만든 건가?** 직접 만든 우리 프로젝트 코드다(설계는 메인이 정리하고 `backend-dev`가 구현). 버킷 계산은 Bucket4j, 카운터는 Micrometer 라이브러리를 쓴다. `failureWindow`는 원래 있던 코드다.
10. **표준 도구는 없나?** Logback에 `DuplicateMessageFilter`가 있다(공식 문서 확인). 같은 메시지(`{}` 치환 전 원문 기준)가 허용 횟수(기본 5)를 넘게 반복되면 버린다. 설정 한 줄로 쓸 수 있지만 차이가 있다.

    | | 직접 만든 `StoreErrorLog` | `DuplicateMessageFilter` |
    |---|---|---|
    | 장애가 끝난 뒤 다시 장애 | 처음 1건을 다시 상세하게 남김 | 이미 버리는 상태라 안 찍힐 수 있음(문서에 되살리는 규칙 없음) |
    | 장애가 이어지는 동안 | 1분마다 "N건 발생" 요약 | 허용 횟수 이후 침묵 |
    | 적용 범위 | 저장소 오류 경고 3종 | 앱 전체 로그 |
    | 실행 시점 | 오류가 날 때만 | 모든 로그 호출마다(DEBUG도 먼저 거침) |

    **결정(2026-10-30): 직접 만든 코드 유지(A).** 운영 안전성 면에서 표준 필터가 더 낫다고 할 근거가 없었다. 이 구성(Boot 4.1 + Logback 1.5.38)에서 표준 필터를 돌려 보지는 않았다.
11. **저장소 오류를 몇 번 겪었는지는 어디서 보나?** 로그를 줄여도 `ratelimit.store_error{bucket=...}` 카운터가 매번 센다. 장애 전체의 정확한 횟수는 이 카운터다.

## 5. 코드 흐름 요약

### 5-1. Redis 장애 때 로그 줄이기 (1단계)

`PublicRateLimiter.check`(단건·데모 제한기도 같은 모양)가 Redis에서 버킷을 꺼내다 실패하면, 같은 경고가 쌓이지 않게 **장애당 처음 1건만 상세, 이후 1분마다 건수 요약**만 남긴다.

| 순서 | 위치 | 하는 일 |
|---|---|---|
| 1 | `check` 맨 위 | 최근 5초 안에 오류가 있었으면 Redis를 부르지 않고 통과(fail-open). `failureWindow`가 이 5초를 센다. 거부형(단건·데모)은 이때 429를 던진다 |
| 2 | `try`의 Redis 호출 | 성공하면 바로 아래 `storeErrorLog.markRecovered()`로 장애 상태를 끝낸다 |
| 3 | `catch` | `failureWindow.markFailed()`(5초 구간 시작) → 카운터 +1 → `storeErrorLog.recordError()` 호출 |
| 4 | `recordError` 결과 | `-1`(`FIRST_ERROR`)이면 상세 WARN, 양수 N이면 "최근 1분간 N건" WARN, `0`이면 로그 없음 |

장애 중에는 대략 5초마다 한 번씩만 `catch`에 들어온다(그 사이는 1번에서 Redis를 안 부르므로 오류도 안 난다). 복구되면 `markRecovered`가 초기화해서 다음 장애도 처음 1건부터 남긴다.

### 5-2. 메모리 게이지 (1단계)

서버가 켜질 때 `SystemMemoryMetrics.bindTo`가 게이지 `ottnavi.process.rss`, `ottnavi.container.memory.used`의 "읽는 방법"만 등록한다. 값은 누가 지표를 읽을 때(2단계 전송이 60초마다) `SystemMetricsService.readProcessRssBytes` 등이 `/proc`, cgroup 파일에서 읽어 돌려준다. 읽을 수 없는 환경(Windows·로컬)에서는 `NaN`이라 오류 없이 "값 없음"이다.

### 5-3. 지표 전송 (2단계)

1. **켜질 때**: `application.yml`에 `enabled: false`가 공통으로 있다. `observability` 프로필이 켜지면 `application-observability.yml`이 `enabled: true`, `step: 60s`, `url`, `Authorization`(환경 변수)로 덮어쓴다. 그러면 Boot가 `OtlpMeterRegistry`를 만들고, `@Profile("observability")`인 `ObservabilityConfig`가 거기에 `OtlpMetricsAllowFilter`를 붙인다. 필터는 OTLP 저장소에만 붙으므로 `/actuator` 지표는 그대로다.
2. **지표 하나가 등록될 때**: `OtlpMetricsAllowFilter.accept`가 이 순서로 판단한다.

    | 순서 | 판단 | 결과 |
    |---|---|---|
    | 1 | 태그 키에 개인정보 성격의 이름이 있으면 | 거부 |
    | 2 | 이름이 `ALLOWED_NAMES`와 정확히 같으면 | 통과 |
    | 3 | 이름이 `ALLOWED_PREFIXES`로 시작하면 | 통과 |
    | 4 | 나머지 | 거부 |

    허용 이름: `ottnavi.process.rss`, `ottnavi.container.memory.used`, `ratelimit.rejected`, `ratelimit.store_error`, `process.cpu.usage`, `system.cpu.usage`, `http.server.requests`, `tmdb.call`, `plan.calculate`, `notification.send`, `llm.call`, `llm.call.duration`. 허용 접두사: `jvm.memory.`, `jvm.gc.`, `jvm.threads.`, `hikaricp.connections`, `spring.batch.job`. 보낼 지표를 늘리려면 `ALLOWED_NAMES`에 한 줄 추가한다.
3. **60초마다**: `otlp-metrics-publisher-N`이라는 별도 스레드가 필터를 통과한 지표의 현재 값을 모아 `url`로 POST한다(`Authorization` 헤더 포함). 요청 처리 스레드와 분리돼 있어 Grafana가 느려도 사용자 요청은 기다리지 않는 구조다(요청 처리 시간을 직접 재지는 않았고 코드 구조와 로그로 본 것). 실패하면 같은 스레드가 `WARN Failed to publish metrics to OTLP receiver`와 스택을 남기고, 서버 동작과 지표 기록은 계속된다(연결 불가 대상 `127.0.0.1:1`로 시험). 운영에서 Grafana가 막히면 60초마다 이 WARN이 쌓인다.
4. **확인한 설정값**: 타임아웃은 `management.otlp.metrics.export.connect-timeout` 기본 1초, `read-timeout` 기본 10초(Boot 소스 확인)이고 설정 파일에는 따로 쓰지 않았다. 누적(CUMULATIVE) 방식이라 한 번 실패해도 다음 전송에 이어질 것으로 보지만 실측은 못 했다.

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

## 7. 결정과 운영 계획

### 7-1. 확정된 결정

- 지표는 Micrometer OTLP, 로그는 Loki4j **2.0.x**(Boot 4.1.1의 Logback 1.5.38과 맞는 계열, 2.1은 Logback 1.6 필요). 켜고 끄기는 `observability` 프로필, 프로필이 없으면 지금과 동일.
- 097·099용 새 API는 없다(기존 수집 실행·알림 요약·시스템 지표 API + Grafana 링크). **DEMO 계정에도 Grafana 링크를 보인다**(평가원·일부 인원에게만 계정을 주므로 문제없고 "관측을 넣었다"는 의미가 강함).
- Loki에는 `com.ottnavi` INFO 이상 + 라이브러리 WARN 이상만 보낸다. 기동 직후 Redis 미리 연결은 이번에 하지 않는다(따로).
- **개인정보 원칙**: 이메일·이름·IP·토큰은 로그 금지. 마스킹은 안전망. 내부 숫자 `userId`를 로그에 허용할지는 확인 필요(허용으로 가정 중). 개인정보처리방침에 로그의 외부 보관과 마스킹을 추가한다(Loki를 켜기 전). 법적 요건은 확인하지 못했다.
- 로그 줄이기는 직접 만든 코드 유지(4-3절 10번).
- **2단계 결정(2026-10-30)**: 의존성 `spring-boot-opentelemetry`는 **유지**한다. 이 모듈이 없으면 Boot의 OTLP 자동 구성이 에러 없이 빠져 전송이 안 된다(로컬 테스트로 확인). 메모리 영향은 7-3절의 측정 계획으로 켜기 전에 잰다. `llm.call`, `llm.call.duration`은 허용 목록에 **넣는다**(태그 `operation`·`result`는 닫힌 값이고 작품 ID·줄거리 본문은 태그로 쓰지 않는다고 `GeminiClient`에 명시돼 있다. 시계열 약 5~10개 추가, 무료 한도 수치는 096에서 확인).

### 7-2. 환경 변수(이름만, 값은 저장소·대화에 두지 않는다)

`GRAFANA_OTLP_ENDPOINT`, `GRAFANA_OTLP_AUTH`(2단계), `GRAFANA_LOKI_URL`, `GRAFANA_LOKI_USER`, `GRAFANA_LOKI_TOKEN`(3단계). 값은 관측 코드를 배포하는 시점에 Cloudtype `Environment variables`에 등록한다(저장하면 재배포된다). `GRAFANA_OTLP_AUTH`는 `Basic ...` 전체 문자열이다.

### 7-3. 켜는 방법과 메모리 측정

운영 시작 명령의 프로필을 `prod`에서 `prod,observability`로 바꾸면 켜지고, `observability`를 빼면 꺼진다.
의존성 `spring-boot-opentelemetry`(전송 자동 구성에 필요)가 OpenTelemetry SDK jar를 같이 들여오므로 1GB 서버의 메모리 영향을 재야 한다.

1. 관측 코드를 **프로필 끈 채로** 배포 → RSS를 30분·24시간 뒤 측정.
2. 프로필을 켜고 재배포 → 같은 시점 측정해 비교.
3. 중단 기준: RSS +100MB 또는 컨테이너 사용률 85% 초과. `MALLOC_ARENA_MAX`는 필요할 때만.

기준값은 새로 배포한 직후 약 496MiB, 2일 뒤 약 738MB다(`ps -o pid,rss,etime,args -C java`, `grep -E "VmRSS|VmHWM" /proc/<PID>/status`). 2026-10-30에 사용자가 확인한 현재 사용량은 679.05MB / 1GB(Cloudtype 콘솔 값, 기동 후 경과 시간은 확인하지 못함)다. 측정은 모니터링 구현을 마친 뒤 사용자가 진행한다. 같은 경과 시간끼리 비교해야 하므로 배포 때마다 기동 시각과 측정 시각을 함께 적는다.

### 7-4. 아직 확인하지 못한 것

- 실서버 시계열 개수(추정 250~450개), `spring.batch.job` 지표가 나오는지
- 응답이 없는 서버와 4xx·5xx 응답일 때의 동작
- `prod,observability`를 함께 켠 풀 앱 기동(DB 필요, 미실행)
- Cloudtype에서 Grafana로 나가는 통신
- 실제 Redis 장애에서 로그가 줄어드는지, 게이지가 실제 Linux 컨테이너에서 도는지(운영 배포 뒤 확인)
- 의존성 추가의 실제 메모리·기동 시간 영향

## 8. 갱신 이력

| 날짜 | 내용 |
|---|---|
| 2026-10-30 | 문서 신설. 1단계(로그 줄이기·메모리 게이지) 구현·커밋, 2단계(OTLP 지표 전송) 구현·테스트 통과. 질문과 답 1~11, 문법 6-1~6-5 정리 |
| 2026-10-30 | 2단계 결정 반영: `spring-boot-opentelemetry` 유지, `llm.call`·`llm.call.duration` 허용 목록에 추가(필터 테스트 6건). 현재 메모리 679.05MB/1GB 기록 |
