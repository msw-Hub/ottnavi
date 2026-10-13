package com.ottnavi.catalog.domain;

import com.ottnavi.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 출연진(ERD title_cast). 상위 10명만 저장하고 인물 테이블은 두지 않는다 */
@Getter
@Entity
@Table(name = "title_cast")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TitleCast extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "title_id", nullable = false)
    private Title title;

    @Column(nullable = false)
    private int castOrder; // 출연 순서, 0부터

    @Column(nullable = false)
    private Integer personTmdbId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 300)
    private String characterName; // 배역, 비어 있을 수 있다

    @Column(length = 200)
    private String profilePath;

    /** 출연진 한 명을 만든다 */
    public static TitleCast create(Title title, int castOrder, Integer personTmdbId, String name,
                                   String characterName, String profilePath) {
        TitleCast cast = new TitleCast();
        cast.title = title;
        cast.castOrder = castOrder;
        cast.personTmdbId = personTmdbId;
        cast.name = name;
        cast.characterName = characterName;
        cast.profilePath = profilePath;
        return cast;
    }
}
