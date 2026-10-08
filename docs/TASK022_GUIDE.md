# Task 022 작업 안내서 (Cloudtype SMTP 외부 발신 시험)

> 2026-10-08 작성. 기준 문서는 `docs/ROADMAP.md` Task 022, 10.1 R11-11, TECH 5절(메일 타임아웃)이다.
> 근거 수준: Spring Boot 4.1.0 공식 문서(context7 `/spring-projects/spring-boot/v4.1.0`, 이하 `c7`), 이 저장소의 실측 기록(Task 003·016·020·021). `c7`로 확인하지 못한 것은 "확인 필요"로 표시했다.
> 이 Task는 사용자가 직접 진행한다(백엔드는 사용자가 작성). Claude는 검수와 기록 정리를 돕는다.

## 1. 한눈에 보기

| 항목 | 내용 |
|---|---|
| 목적 | Cloudtype에서 밖으로 SMTP(587) 메일을 보낼 수 있는지 **실제로** 확인해 R11-11(메일 발송 방식)을 결정한다 |
| 기한 | **2026-10-14** (10.1 일찍 해야 하는 결정) |
| 막히는 Task | 072(알림 메일 발송 구현). 결과에 따라 SMTP 구현체를 쓸지 HTTPS 메일 API 구현체를 쓸지 정해진다 |
| 브랜치 | `develop`에서 만든 `feature/b1-smtp-trial` — **병합하지 않고 시험 후 삭제** |
| 배포 대상 | 운영 서비스(`ottnavi`)와 분리된 **일회성** Cloudtype 서비스(예: `ottnavi-smtp-trial`), 무료 리소스 |
| 결과물 | ROADMAP Task 022 `기록:`(성공·실패, 오류 메시지, 시험일, 서비스·브랜치 삭제), R11-11 결정 |

| 완료 기준 (ROADMAP) | 확인 방법 |
|---|---|
| V-H: 성공·실패, 오류 메시지, 시험일, 일회성 서비스 삭제를 기록했다 | Cloudtype 배포 로그, 수신함, 콘솔의 서비스 목록 |
| 사용자가 R11-11을 결정했다 | 10.1·10.2 표 갱신, PRD 11절 11번 갱신 |

## 2. 왜 하는가

- **클라우드 플랫폼은 SMTP 포트(25·465·587)의 외부 발신을 막는 경우가 많다.** 스팸 발송 악용을 막기 위해서다. Cloudtype이 막는지는 문서로 확인하지 못했으므로(Task 003 조사 범위 밖) 직접 보내 봐야 안다.
- 2단계 알림 메일(FR-15·16, Task 071~074)의 구현 방식이 이 결과에 달려 있다. TECH 5절은 이미 "발송 포트 하나 + SMTP·HTTPS 메일 API 구현체 둘, `app.mail.provider`로 선택"으로 설계했고, **어느 구현체를 운영에서 쓸지**만 이 시험으로 정한다.
- MVP 배포(Task 092~095) 전에 알아야 HTTPS 메일 API 계정·키 발급, 발신 도메인 인증 같은 준비를 미리 할 수 있다. 늦게 알면 2단계 일정이 밀린다.
- 메일 서버가 응답하지 않을 때 타임아웃을 안 걸면 스레드가 **무기한 대기**한다(`c7`: Spring Boot 문서가 타임아웃 설정을 권장). 시험 코드에서 타임아웃까지 같이 확인해 두면 Task 072에서 그대로 쓴다.

## 3. 시험 방식 결정 (먼저 읽기)

### 3.1 무엇으로 발송을 일으킬지 — `ApplicationRunner` 추천

| 방식 | 장점 | 단점 |
|---|---|---|
| **A. 기동 시 1회 발송하는 `ApplicationRunner`** (추천) | HTTP 엔드포인트가 없어 공개 주소로 남이 메일을 보낼 수 없다. 재시험은 재배포(또는 재시작)로 한다 | 다시 보내려면 재시작이 필요하다 |
| B. 시험용 HTTP 엔드포인트 | 여러 번 쉽게 보낼 수 있다 | 공개 주소에 메일 발송 엔드포인트가 열린다. 오리진 비밀 헤더로 막히긴 하지만 굳이 위험을 만들 이유가 없다 |

### 3.2 시험 서비스가 어떤 설정으로 뜰지 — prod 프로필 + 시험 속성 추천

현재 앱은 기동할 때 DB(JPA·Flyway)·Redis·`ORIGIN_SECRET`이 모두 필요하다. 시험 서비스만 따로 띄우려면 둘 중 하나다.

| 방식 | 내용 | 판단 |
|---|---|---|
| **A. prod 프로필 그대로 + 메일 변수 추가** (추천) | 운영 서비스와 같은 런타임 환경 변수 7개(`DB_*`, `REDIS_*`, `ORIGIN_SECRET`)에 메일 변수를 더한다 | 코드 변경이 가장 적다. Flyway는 이미 V1이라 `validate`만 하고 아무것도 쓰지 않는다. 비밀 값이 서비스 하나에 더 들어가므로 **시험이 끝나면 서비스를 바로 삭제**한다 |
| B. 시험 프로필에서 DB·Redis 자동 구성 제외 | `spring.autoconfigure.exclude`로 DataSource·JPA·Flyway·Redis를 끈다 | 리포지토리를 쓰는 빈(`OttServiceQueryService`)이 생성되지 않아 기동이 실패한다. 피하려면 코드를 더 고쳐야 해서 시험 목적에 비해 과하다 |

- A의 확인 필요 사항: Supabase Session Pooler 연결이 시험 서비스만큼 더 쓰인다(Hikari 최대 5). 시험은 짧으므로 문제 없다고 보지만, 연결 오류가 나면 운영 서비스를 잠시 중지하고 시험한다.
- **Cloudtype 프리티어 리소스 확인 필요**: 프리티어 동시실행은 4개(Task 003)지만, 무료 메모리 1GB를 서비스끼리 나눠 쓰는지는 기록이 없다. 시험 서비스 생성 화면에서 메모리를 고를 때 부족하다고 나오면 **운영 서비스를 잠시 중지**하고 시험한다(운영 서비스는 지금 서비스 목록 API뿐이라 영향이 작다).

## 4. 사전 준비 (체크리스트)

- [ ] Gmail 앱 비밀번호가 비밀번호 관리자에 있다(Task 002). `MAIL_HOST=smtp.gmail.com`, `MAIL_PORT=587`
- [ ] 받는 주소(본인 메일)를 정한다. 다른 사람 주소로 보내지 않는다
- [ ] Docker Desktop이 켜져 있다(로컬 사전 시험·빌드 테스트용)
- [ ] `develop`이 최신이다: `git switch develop; git pull`

## 5. 단계 (단계마다 멈춰서 확인하고 넘어간다)

### ① 시험 브랜치 만들기

```powershell
git switch develop; git pull
git switch -c feature/b1-smtp-trial
```

- 이 브랜치는 **PR을 만들지 않는다**. Cloudtype이 원격 브랜치를 빌드하므로 푸시는 필요하다(⑤). 푸시하면 CI(`backend-ci`)가 돌 수 있지만 PR이 없으므로 병합될 일은 없다.

### ② 메일 의존성 추가

`backend/build.gradle`의 dependencies에 한 줄 추가한다.

```groovy
	implementation 'org.springframework.boot:spring-boot-starter-mail'   // Task 022 시험용(병합하지 않는 브랜치)
```

- 확인 필요: Boot 4.1.1에서 스타터 이름이 `spring-boot-starter-mail`인지. `c7`에서 메일 자동 구성 모듈(`spring-boot-mail`, `MailSenderPropertiesConfiguration`)은 확인했지만 스타터 아티팩트 이름 자체는 확인하지 못했다. `.\gradlew.bat compileJava`가 통과하면 맞는 것이다.
- 확인 명령: `cd backend; .\gradlew.bat dependencyInsight --dependency jakarta.mail --configuration runtimeClasspath`

### ③ 메일 설정(yml) — 시험 브랜치의 `application.yml`에 추가

```yaml
spring:
  mail:
    host: ${MAIL_HOST:smtp.gmail.com}
    port: ${MAIL_PORT:587}
    username: ${MAIL_USERNAME:}
    password: ${MAIL_PASSWORD:}
    properties:
      "[mail.smtp.auth]": true
      "[mail.smtp.starttls.enable]": true        # 587은 평문으로 연결한 뒤 STARTTLS로 암호화한다
      "[mail.smtp.starttls.required]": true      # STARTTLS를 못 하면 평문으로 보내지 않고 실패시킨다
      "[mail.smtp.connectiontimeout]": 5000      # 연결 타임아웃(ms). 포트가 막혀 있으면 여기서 실패한다
      "[mail.smtp.timeout]": 5000                # 읽기 타임아웃(ms)
      "[mail.smtp.writetimeout]": 5000           # 쓰기 타임아웃(ms)
app:
  smtp-trial:
    enabled: ${SMTP_TRIAL_ENABLED:false}         # true일 때만 기동 시 1회 발송
    to: ${SMTP_TRIAL_TO:}                        # 받는 주소(본인 메일)
```

- 근거: 타임아웃 3종과 대괄호 키 표기는 `c7`(Spring Boot 4.1.0 "Sending Email") 예시와 같다. 값(5초)은 시험용 제안값이다.
- **포트가 막혀 있으면** 보통 `connectiontimeout`에서 실패한다(`MailConnectException`, 원인 `SocketTimeoutException: connect timed out`). 타임아웃을 안 걸면 몇 분씩 멈춰서 원인 구분이 어렵다.

### ④ 시험 코드 — `ApplicationRunner` 하나

위치 제안: `backend/src/main/java/com/ottnavi/infra/mail/SmtpTrialRunner.java`(시험 브랜치에만 존재).

```java
package com.ottnavi.infra.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

/** Task 022: 기동 시 SMTP 연결 확인과 시험 메일 1건 발송 결과를 로그로 남긴다(병합하지 않는 시험 코드). */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBooleanProperty("app.smtp-trial.enabled")
public class SmtpTrialRunner implements ApplicationRunner {

    private final JavaMailSenderImpl mailSender;

    @Value("${app.smtp-trial.to}")
    private String to; // 받는 주소

    @Override
    public void run(ApplicationArguments args) {
        // 1단계: 연결·인증만 확인한다. 실패 단계를 나눠야 "포트 차단"과 "인증 실패"를 구분할 수 있다
        long start = System.currentTimeMillis();
        try {
            mailSender.testConnection();
            log.info("SMTP 연결·인증 성공: host={}, port={}, 소요={}ms",
                    mailSender.getHost(), mailSender.getPort(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("SMTP 연결·인증 실패: host={}, port={}, 소요={}ms",
                    mailSender.getHost(), mailSender.getPort(), System.currentTimeMillis() - start, e);
            return;
        }

        // 2단계: 실제 발송
        start = System.currentTimeMillis();
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailSender.getUsername());
            message.setTo(to);
            message.setSubject("[ottnavi] Cloudtype SMTP 발신 시험");
            message.setText("Task 022 시험 메일입니다. 이 메일이 보이면 Cloudtype에서 SMTP 587 발신이 됩니다.");
            mailSender.send(message);
            log.info("시험 메일 발송 성공: 소요={}ms", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("시험 메일 발송 실패: 소요={}ms", System.currentTimeMillis() - start, e);
        }
    }
}
```

- 받는 주소·비밀번호는 로그에 남기지 않는다(호스트·포트·소요 시간만).
- `JavaMailSenderImpl`을 직접 주입하는 이유: `testConnection()`이 인터페이스(`JavaMailSender`)에는 없다. 자동 구성이 `JavaMailSenderImpl`을 만든다(`c7`: `MailSenderPropertiesConfiguration.applyProperties`).
- 예외는 잡아서 로그만 남긴다. 발송 실패로 앱 기동이 실패하면 Cloudtype이 재시작을 반복해 로그 보기가 어려워진다.
- `spring.mail.test-connection=true`(기동 시 연결 검증, `c7`)도 있지만 실패하면 **기동 자체가 실패**하므로 이 시험에는 쓰지 않는다.

### ⑤ 로컬에서 먼저 시험 (코드 문제와 네트워크 문제를 분리)

```powershell
docker compose -f infra/docker-compose.yml up -d
cd backend
$env:MAIL_USERNAME = '본인 Gmail 주소'
$env:MAIL_PASSWORD = '앱 비밀번호 16자리'      # 셸 기록에 남지 않게 끝나면 창을 닫는다
$env:SMTP_TRIAL_ENABLED = 'true'
$env:SMTP_TRIAL_TO = '받는 주소'
.\gradlew.bat bootRun
```

- 확인: 로그에 `SMTP 연결·인증 성공`, `시험 메일 발송 성공`이 나오고 수신함(스팸함 포함)에 메일이 온다.
- 로컬에서 실패하면 Cloudtype 시험은 의미가 없다. 앱 비밀번호·2단계 인증 설정부터 확인한다.
- 이어서 `.\gradlew.bat build`로 기존 테스트가 깨지지 않는지 본다(`app.smtp-trial.enabled` 기본 false라 테스트에서는 러너가 뜨지 않는다. 메일 자동 구성은 연결을 미리 열지 않으므로 테스트에 SMTP 서버가 없어도 된다 — 확인 필요, 실패하면 오류를 그대로 기록).

### ⑥ 커밋·푸시 (시험 브랜치)

- 커밋 예: `chore(backend): Cloudtype SMTP 발신 시험 코드 (Task 022, 병합 금지)`
- `git push -u origin feature/b1-smtp-trial`. **PR은 만들지 않는다.**

### ⑦ Cloudtype 일회성 서비스로 배포

- 같은 프로젝트(`backend`)에 새 서비스 `ottnavi-smtp-trial`을 만든다. 설정은 Task 020 `기록:`과 같다: 서브 디렉토리 `backend`, JDK 17, 포트 8080, Build `./gradlew bootJar`, Start `java -Dspring.profiles.active=prod -jar build/libs/backend-0.0.1-SNAPSHOT.jar`, **브랜치 `feature/b1-smtp-trial`**, 무료 리소스.
- 런타임 `Environment variables`(빌드 변수 아님, TECH 6절 함정): 운영과 같은 7개 + `SPRING_PROFILES_ACTIVE=prod` + `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `SMTP_TRIAL_ENABLED=true`, `SMTP_TRIAL_TO`.
- 기동에 약 3분 걸린다(Task 020 실측 201초). 로그에서 `Started OttnaviApplication` 이후 시험 로그를 본다.

### ⑧ 결과 판정

| 로그 | 의미 | 다음 행동 |
|---|---|---|
| 연결 성공 + 발송 성공 + 수신함 도착 | **SMTP 가능** | ⑨로 이동. R11-11 = SMTP(Gmail) 채택 가능 |
| 연결 실패, 원인 `connect timed out` | 587 외부 연결이 막힘(가능성 높음) | `MAIL_PORT=465` + 아래 SSL 설정으로 1회 더 시험 → 그래도 실패면 HTTPS 메일 API(⑩) |
| 연결 성공, 인증 실패(`AuthenticationFailedException`) | 네트워크는 열림, 계정 문제 | 로컬에서 같은 계정이 됐는지 확인. 값 오타(공백 포함 여부) 확인 |
| 발송 성공인데 메일이 안 옴 | Gmail이 받았지만 전달이 늦거나 스팸 처리 | 10분 기다린 뒤 스팸함·Gmail "보낸편지함" 확인 |

465(SSL) 재시험용 설정(yml의 `properties`를 바꾼다):

```yaml
      "[mail.smtp.ssl.enable]": true             # 465는 연결 즉시 TLS
      "[mail.smtp.starttls.enable]": false
      "[mail.smtp.starttls.required]": false
```

### ⑨ 정리 (성공·실패와 관계없이)

- [ ] Cloudtype에서 `ottnavi-smtp-trial` 서비스 **삭제**(비밀 값이 들어 있으므로 시험이 끝나면 바로)
- [ ] 운영 서비스를 중지했었다면 다시 시작하고 `/actuator/health` 200 확인
- [ ] 시험 브랜치 삭제: `git switch develop; git branch -D feature/b1-smtp-trial; git push origin --delete feature/b1-smtp-trial`
- [ ] 로그 전체를 저장소에 붙이지 않는다. 예외 클래스·메시지 한 줄과 소요 시간만 기록한다

### ⑩ SMTP가 막힌 경우에만: HTTPS 메일 API 시험

- 서비스 하나(예: Resend 또는 Brevo)를 골라 키를 발급한다(Task 002에서 옮겨 온 항목). **무료 한도·발신 주소(도메인) 인증 조건은 가입 전에 공식 문서로 확인한다**(이 안내서에서는 확인하지 않았다). 도메인 인증이 필요하면 Task 005(도메인 구매) 일정과 엮이므로 사용자 결정이 필요하다.
- 같은 시험 브랜치에서 `ApplicationRunner`를 HTTPS POST 1건으로 바꾼다(Spring `RestClient`, 연결·읽기 타임아웃 설정). 443 포트는 Vercel·TMDB 호출처럼 막혀 있지 않을 가능성이 높다.
- 결과 기록과 정리(⑨)는 같다. 키는 비밀번호 관리자에만 둔다.

## 6. 결과 기록 (ROADMAP Task 022 `기록:` 초안)

```
- 기록: 2026-10-__ 완료(사용자 수행).
  - 시험 환경: 일회성 서비스 `ottnavi-smtp-trial`(프리티어, prod 프로필), 브랜치 `feature/b1-smtp-trial`(병합 안 함), 로컬 사전 시험 성공/실패.
  - 587 STARTTLS: 연결 성공/실패(소요 __ms), 발송 성공/실패, 수신 확인. 오류: `예외 클래스: 메시지 한 줄`.
  - (시험한 경우) 465 SSL: ...
  - (시험한 경우) HTTPS 메일 API: 서비스명, 결과, 무료 한도·도메인 인증 조건(출처 URL·확인일).
  - 정리: 서비스 삭제일, 브랜치 삭제(로컬·원격).
  - R11-11 결정: SMTP(Gmail) / HTTPS 메일 API(서비스명). 운영 `app.mail.provider` 기본값 = __.
```

- 결정 후 함께 고칠 문서(`feature/docs-*` 브랜치·PR): ROADMAP 10.1·10.2에서 R11-11 행 정리, PRD 11절 11번 확정 표시, backend.md "메일" 줄의 "잠정" 문구, `docs/PHASE1_HUMAN_TASKS.md` 체크.

## 7. 확인하지 못한 것 (시험으로 확인)

- Cloudtype의 SMTP 포트 정책(문서 근거 없음, 이 시험의 목적).
- `spring-boot-starter-mail` 아티팩트 이름(②의 `compileJava`로 확인).
- 메일 자동 구성이 테스트 컨텍스트에서 SMTP 서버 없이 뜨는지(⑤의 `build`로 확인).
- 프리티어 무료 메모리를 두 서비스가 나눠 쓰는지(⑦의 서비스 생성 화면에서 확인).
- Gmail SMTP의 일일 발송 한도. 2단계 알림 발송량과 비교해야 하므로 Task 072 전에 Google 공식 도움말로 확인한다.
