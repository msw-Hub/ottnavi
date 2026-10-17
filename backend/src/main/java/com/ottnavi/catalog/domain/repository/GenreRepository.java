package com.ottnavi.catalog.domain.repository;

import com.ottnavi.catalog.domain.entity.Genre;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GenreRepository extends JpaRepository<Genre, Integer>  {

    /** 작품이 하나 이상 연결된 장르만 이름 가나다순(같으면 ID 순)으로 조회한다. 탐색 화면의 장르 선택용 */
    @Query("select g from Genre g where exists (select 1 from TitleGenre tg where tg.genre.id = g.id) "
            + "order by g.name asc, g.id asc")
    List<Genre> findAllWithTitles();
}
