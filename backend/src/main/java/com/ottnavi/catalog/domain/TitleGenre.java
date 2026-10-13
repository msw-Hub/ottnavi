package com.ottnavi.catalog.domain;

import com.ottnavi.global.common.BaseTimeEntity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 작품 × 장르 연결(ERD title_genre). 복합 기본키 (title_id, genre_id) */
@Getter
@Entity
@Table(name = "title_genre")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TitleGenre extends BaseTimeEntity {

    @EmbeddedId
    private TitleGenreId id;

    @MapsId("titleId") // 기본키의 titleId를 이 연관에서 가져온다
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "title_id")
    private Title title;

    @MapsId("genreId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "genre_id")
    private Genre genre;

    /** 작품과 장르를 연결한다. 두 객체는 이미 저장돼 id가 있어야 한다 */
    public static TitleGenre create(Title title, Genre genre) {
        TitleGenre titleGenre = new TitleGenre();
        titleGenre.id = new TitleGenreId(title.getId(), genre.getId());
        titleGenre.title = title;
        titleGenre.genre = genre;
        return titleGenre;
    }
}
