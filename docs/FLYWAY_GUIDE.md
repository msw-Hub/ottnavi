# Flyway 쉬운 설명 (개념과 우리 프로젝트 사용법)

> 2026-10-13 작성. 학습용 문서이고 규칙의 근거 문서가 아니다(근거는 `docs/ERD.md`, `docs/TECH.md`, `CLAUDE.md`의 Flyway 규칙).
> 공식 문서(Flyway, context7로 확인)와 이 저장소 설정을 근거로 썼다. 우리 설정은 `backend/build.gradle`, `application.yml`, `db/migration/`.

## 1. Flyway가 뭐고 왜 쓰나

**DB 구조(테이블·컬럼·제약)의 변경 이력을 SQL 파일로 쌓아 두고, 앱이 켜질 때 아직 안 한 것만 순서대로 실행해 주는 도구**다. 코드를 git으로 버전 관리하듯 DB 구조도 파일로 버전 관리한다고 보면 된다.

없으면 "내 컴퓨터 DB에는 컬럼이 있는데 운영 DB에는 없다"가 생긴다. Flyway가 있으면 내 컴퓨터, 테스트, 운영 DB가 모두 같은 파일을 같은 순서로 실행하므로 구조가 같아진다.

## 2. 핵심 개념 4가지

| 개념 | 설명 |
|---|---|
| **마이그레이션 파일** | `V5__create_user.sql`처럼 `V{번호}__{설명}.sql`. 번호가 실행 순서이고 **밑줄은 두 개**다. 한 번 실행되면 끝이다 |
| **이력 표 `flyway_schema_history`** | Flyway가 DB에 만드는 표. 어떤 번호를 언제 실행했는지와 파일의 **체크섬**(내용 지문, SQL은 CRC32)을 적는다 |
| **실행 규칙** | 앱이 켜지면 이력 표를 보고 **아직 안 한 번호만** 번호 순으로 실행한다. 실패하면 앱이 켜지지 않는다 |
| **검증(validate)** | 실행 전에 "이미 한 파일의 내용이 지금 파일과 같은가"를 체크섬으로 비교한다. 다르면 오류로 멈춘다 |

그래서 **이미 적용된 파일은 고치면 안 된다**(체크섬이 달라져 멈춘다). 구조를 바꾸고 싶으면 새 번호 파일을 추가한다. 공식 문서도 이름·종류·체크섬이 다르거나, 적용했는데 파일이 없거나, 파일은 있는데 아직 안 한 번호가 낮은 번호로 끼어든 경우를 검증 실패로 본다.

## 3. 우리 프로젝트에서의 쓰임

| 항목 | 내용 |
|---|---|
| 위치 | `backend/src/main/resources/db/migration/` (Spring Boot 기본 위치라 설정이 없다) |
| 의존성 | `spring-boot-starter-flyway` (Boot 4에서는 이게 없으면 Flyway가 돌지 않는다), PostgreSQL용 `flyway-database-postgresql` |
| 실행 시점 | 앱 시작 시 자동. JPA보다 먼저 돌아서 테이블이 만들어진 뒤에 엔티티를 검사한다 |
| 역할 분담 | `ddl-auto: validate`는 **엔티티와 DB 구조가 같은지 검사만** 한다(테이블을 만들지 않는다). 구조를 만드는 건 Flyway뿐이다 |
| Spring Batch | `initialize-schema: never`라 라이브러리가 표를 안 만든다. `BATCH_*`는 Flyway V4가 만든다(Task 033에서 추가 완료) |
| 번호 배정 | **현재 있는 파일은 V1~V4다**(V1 `ott_service`, V2~V4 작품·상품·배치는 Task 033에서 완료). V5~V7 사용자·플랜·알림은 Task 034에서 추가할 예정이다. 번호는 Task에 미리 배정돼 있다 |
| 테스트 | Testcontainers가 빈 PostgreSQL 17.6을 띄우고 그 시점에 있는 파일(V1부터)을 전부 적용한 뒤 검사한다. 파일에 오타가 있으면 테스트가 바로 실패한다 |

## 4. 엔티티로 테이블을 만들던 방식과 뭐가 다른가

이전 프로젝트처럼 `ddl-auto: update`(또는 `create`)를 쓰면 JPA가 **엔티티를 보고 테이블을 자동으로 만든다**. 이 프로젝트도 JPA(엔티티)를 쓰지만 테이블은 만들지 않는다. 테이블은 Flyway SQL이 만들고, 엔티티는 그 테이블을 읽고 쓰기만 한다.

| | 엔티티로 생성(이전) | Flyway SQL(지금) |
|---|---|---|
| 설정 | `ddl-auto: update` / `create` | `ddl-auto: validate` |
| 테이블을 만드는 것 | JPA(엔티티 클래스) | SQL 파일(`V번호__설명.sql`) |
| 엔티티의 역할 | 설계도 + 데이터 접근 | 데이터 접근만 |
| 변경 기록 | 없음 | `flyway_schema_history` 표와 git |
| 코드 리뷰 | 엔티티 코드만 본다 | SQL 자체를 PR에서 본다 |

**왜 바꿨나**

1. **JPA 자동 생성으로 만들 수 없는 제약이 필요하다.** 부분 유니크 인덱스(한 사용자의 ACTIVE 플랜은 1개), `NULLS NOT DISTINCT`(영화의 시즌 NULL 중복 방지), 열끼리의 CHECK, FK의 `ON DELETE CASCADE`/`SET NULL`(탈퇴 시 하위 데이터 삭제)은 SQL로 써야 한다.
2. **운영 DB를 안전하게 바꾸려면 변경 이력이 필요하다.** `update`는 컬럼 삭제·이름 변경을 못 하고, 무엇이 언제 적용됐는지 남지 않는다. Flyway는 내 PC, 테스트, 운영(Supabase)에 같은 파일을 같은 순서로 적용하고 기록한다.
3. **DB가 스스로 잘못된 데이터를 막게 한다.** 제약을 DB에 두고, 마이그레이션 테스트가 엔티티를 거치지 않고 SQL을 직접 넣어 확인한다. 엔티티를 거치면 앱 코드가 먼저 막아 DB 제약이 동작하는지 알 수 없다.

**작업 순서가 이전과 반대다.**

```
① SQL로 테이블을 만든다 (Task 033, 034)
② 엔티티를 SQL에 맞춰 작성한다 (Task 035, 062)
③ 앱이 켜질 때 validate가 "엔티티와 테이블이 같은가"를 검사한다 → 다르면 기동 실패
```

그래서 지금은 엔티티가 거의 없다(`OttService`만 V1을 검증한다). `validate`는 컬럼 이름과 타입만 검사하고 **CHECK와 FK의 ON DELETE는 검사하지 않는다.** 그래서 삭제 동작은 Testcontainers 테스트가 직접 확인한다(Task 034).

## 5. 작업할 때 지킬 것

1. **병합된 파일은 고치지 않는다.** 새 번호를 추가한다.
2. **아직 병합 전이고 내 컴퓨터 DB에만 적용했다면** 고쳐도 되지만 로컬 DB를 새로 만들어야 한다: `docker compose -f infra/docker-compose.yml down -v` 후 `up -d` (로컬 데이터가 사라진다).
3. **파일 이름의 밑줄은 두 개**다(`V5_create...`는 인식되지 않는다).
4. **번호를 겹치지 않게 쓴다.** 두 브랜치가 같은 번호를 만들면 병합 때 충돌한다.
5. **FK의 ON DELETE 동작은 `validate`가 검사하지 않는다.** 그래서 삭제 동작은 Testcontainers 테스트로 고정한다(Task 034).
6. 기동 로그에 `Successfully applied N migration`이 보이면 적용된 것이다. `Migration checksum mismatch`는 적용된 파일을 고쳤다는 뜻이다.

## 6. 공식 문서 출처

- Flyway 문서 Validate 명령: 체크섬(SQL은 CRC32) 비교와 검증 실패 조건 — https://documentation.red-gate.com/display/FD/Flyway+schema+history+table
- Flyway 저장소 문서 `Reference/Commands/Validate.md`, `Tutorial - Baseline Migrations.md`(버전 파일 이름 예 `V1__create_two_tables.sql`)
- Boot 4의 `spring-boot-starter-flyway`와 PostgreSQL 모듈 필요: `docs/TECH.md` 6절
