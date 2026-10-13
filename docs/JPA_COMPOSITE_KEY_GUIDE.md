# JPA 복합 기본키 쉬운 설명 (우리 프로젝트 코드로 배우기)

> 2026-10-13 작성. Task 035를 구현하며 오간 설명을 정리한 학습 문서다. 규칙의 근거 문서가 아니다.
> 코드 예시는 `backend/src/main/java/com/ottnavi/catalog/domain/`의 `TitleGenre`, `TitleGenreId`에서 가져왔다. `ProductComponent`도 같은 구조다.

## 1. 복합키가 뭐고 왜 쓰나

보통 표는 줄마다 번호 `id`를 하나 붙여 구분한다. 그런데 **두 대상을 잇기만 하는 표**는 줄의 정체가 "두 값의 짝"이다.

```
title_genre (작품 ↔ 장르 연결 표)
 title_id  genre_id
 100       18        ← 작품 100은 장르 18이다
 100       35        ← 작품 100은 장르 35이기도 하다
 200       18
```

같은 짝이 두 줄 들어가면 안 되니, 두 컬럼을 묶어 기본키로 쓰면 번호를 따로 붙이지 않아도 중복이 막힌다. 이것이 **복합 기본키**다. 새 id를 만드는 것이 아니라 **이미 있는 두 컬럼을 짝으로 묶는 것**이다.

| | 복합키 | 번호(대리키) 방식 |
|---|---|---|
| 표의 컬럼 | `title_id, genre_id` | `id, title_id, genre_id` |
| 줄을 구분하는 것 | 두 값의 짝 | 새로 붙인 번호 |
| 중복 방지 | 기본키가 해 줌 | `UNIQUE(title_id, genre_id)`를 따로 걺 |

우리 프로젝트에서 복합키를 쓴 표는 `title_genre`(작품, 장르)와 `product_component`(상품, 서비스) 둘뿐이다. `user_subscription`처럼 **자기 정보**(상태, 결제일 등)가 있는 표는 번호 + `UNIQUE`를 썼다(ERD의 PK 예외 목록).

## 2. 이 구조는 다대다 관계를 푼 것이다

작품 하나에 장르가 여러 개이고, 장르 하나에 작품이 여러 개다(**다대다**). 표 하나로는 표현할 수 없어서 가운데에 연결 표를 둔다.

```
Title ←1──N→ TitleGenre ←N──1→ Genre
```

`TitleGenre`는 두 엔티티를 합친 것이 아니라 "어느 작품이 어느 장르인지"라는 **관계 한 줄**을 저장하는 엔티티다. `Title`과 `Genre`는 각자 따로 있다. 같은 구조가 `SubscriptionProduct ← ProductComponent → OttService`(번들에 포함된 서비스)에도 있다.

## 3. 자바에서는 키 클래스가 필요하다

JPA는 엔티티 하나를 `findById(키 하나)`로 찾는다. 기본키가 두 컬럼이면 두 값을 담을 **키 클래스**가 있어야 한다. 엔티티 필드 두 개에 `@Id`를 붙이는 방법은 JPA 표준에 없다.

```java
@Embeddable                                  // 엔티티 안에 들어가는 값 묶음
@EqualsAndHashCode                           // 같은 짝이면 같은 키 (필수)
public class TitleGenreId implements Serializable {   // JPA 규칙: Serializable (표시만 붙이면 됨)
    @Column(name = "title_id")  private Long titleId;
    @Column(name = "genre_id")  private Integer genreId;
}
```

키 클래스 요건(JPA 명세): `public`, 인자 없는 생성자, `Serializable`, `equals()`·`hashCode()`. `Serializable`은 메서드가 없는 표시용 인터페이스이고, `equals`/`hashCode`가 없으면 같은 짝을 다른 키로 취급한다.

## 4. 키 클래스를 쓰는 방법 두 가지

| | `@EmbeddedId` (우리) | `@IdClass` |
|---|---|---|
| 키 클래스 | `@Embeddable` | 별도 클래스 |
| 엔티티 안 | `@EmbeddedId` 필드 **하나** | `@Id` 필드 **두 개** + 엔티티 위에 `@IdClass(...)` |
| 연관 객체와 같이 쓸 때 | `@MapsId`로 한 번에 연결, 편함 | 같은 컬럼을 두 곳에서 따로 관리해 복잡 |

우리는 `Title`/`Genre` 객체를 `@ManyToOne`으로 가리키는 방식(프로젝트 규칙)이라 `@EmbeddedId` + `@MapsId`가 자연스럽다.

```java
public class TitleGenre extends BaseTimeEntity {
    @EmbeddedId
    private TitleGenreId id;                  // 기본키 = 키 클래스

    @MapsId("titleId")                        // 기본키의 titleId는 이 연관(title)에서 가져온다
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "title_id")
    private Title title;

    @MapsId("genreId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "genre_id")
    private Genre genre;

    public static TitleGenre create(Title title, Genre genre) {   // 키는 두 객체의 id로 만든다
        ...
    }
}
```

사용:
```java
titleGenreRepository.save(TitleGenre.create(title, genre));                 // 저장
titleGenreRepository.findById(new TitleGenreId(title.getId(), 16));         // 조회: 키 객체로
```

## 5. 복합키와 조인은 다르다

| | 복합키 | 조인(JOIN) |
|---|---|---|
| 하는 일 | **표 하나** 안에서 줄을 구분 | **표 여러 개**를 합쳐 조회 |
| 시점 | 표를 만들 때 | 조회할 때마다 |

연결 표를 "조인 표"라고도 불러서 헷갈리지만, 복합키가 조인을 뜻하는 것은 아니다. 번호만 들어 있는 `title_genre`에서 "기생충은 드라마"를 읽으려면 `title`, `genre`와 **조인**한다. JPA에서는 `titleGenre.getTitle().getTitle()`처럼 객체를 따라가면 필요할 때 조인 SQL을 만들어 준다.

## 6. 주의할 점

- **키 클래스에는 리포지토리를 만들지 않는다.** 표가 없는 부품이다. `JpaRepository<TitleGenreId, ...>`를 만들면 `The given domain class does not contain an id attribute`로 앱 기동이 실패한다(이번에 겪음).
- 리포지토리의 기본키 타입 칸에는 키 클래스를 쓴다: `JpaRepository<TitleGenre, TitleGenreId>`.
- `create`에 넘기는 작품·장르는 **먼저 저장돼 있어야** 한다. 저장 전이면 `id`가 비어 키를 만들 수 없다.
- `Long`, `Integer` 같은 단일 키는 이미 `Serializable`이라 따로 쓸 일이 없다.
