package com.ottnavi.catalog.domain.repository;

import com.ottnavi.catalog.domain.entity.TitleGenre;
import com.ottnavi.catalog.domain.entity.TitleGenreId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TitleGenreRepository extends JpaRepository<TitleGenre, TitleGenreId> {
    /** 작품에 연결된 장르 연결 행 전부를 조회한다 */
    List<TitleGenre> findByTitleId(Long titleId);
}
