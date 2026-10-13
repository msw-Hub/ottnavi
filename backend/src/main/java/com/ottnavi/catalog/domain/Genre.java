package com.ottnavi.catalog.domain;

import com.ottnavi.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 장르(ERD genre). PK는 TMDB 장르 ID를 그대로 쓴다(애니메이션은 16) */
@Getter
@Entity
@Table(name = "genre")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Genre extends BaseTimeEntity {

    @Id
    private Integer id; // TMDB 장르 ID. 자동 생성하지 않는다

    @Column(nullable = false, length = 50)
    private String name;

    /** TMDB 장르로 장르를 만든다 */
    public static Genre create(Integer id, String name) {
        Genre genre = new Genre();
        genre.id = id;
        genre.name = name;
        return genre;
    }
}
