package com.ottnavi.catalog.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** title_genre의 복합 기본키. equals/hashCode가 필수이고, 값은 TitleGenre의 @MapsId가 연관 객체와 맞춘다 */
@Getter
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TitleGenreId implements Serializable {

    @Column(name = "title_id")
    private Long titleId;

    @Column(name = "genre_id")
    private Integer genreId;
}
