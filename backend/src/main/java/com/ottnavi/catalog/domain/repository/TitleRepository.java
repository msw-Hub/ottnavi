package com.ottnavi.catalog.domain.repository;

import com.ottnavi.catalog.domain.entity.Title;
import com.ottnavi.catalog.domain.enums.MediaType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TitleRepository extends JpaRepository<Title, Long> {

    /** 종류와 TMDB ID로 작품을 찾는다. 수집이 이미 있는 작품인지 확인할 때 쓴다 */
    Optional<Title> findByMediaTypeAndTmdbId(MediaType mediaType, Integer tmdbId);

    /**
     * 제목·원제에 패턴이 들어가는 작품을 인기도 내림차순(동률은 id 오름차순)으로 한 페이지 조회한다.
     * 패턴은 호출 쪽이 %·_·\를 이스케이프하고 앞뒤에 %를 붙여 넘긴다(이스케이프 문자는 escapeChar).
     * 인기도가 비어 있는 작품은 뒤로 보낸다(PostgreSQL은 DESC에서 NULL을 먼저 두기 때문).
     */
    @Query(value = "select t from Title t "
            + "where t.title ilike :pattern escape :escapeChar or t.originalTitle ilike :pattern escape :escapeChar "
            + "order by t.popularity desc nulls last, t.id asc",
            countQuery = "select count(t) from Title t "
                    + "where t.title ilike :pattern escape :escapeChar or t.originalTitle ilike :pattern escape :escapeChar")
    Page<Title> searchByTitle(@Param("pattern") String pattern, @Param("escapeChar") char escapeChar, Pageable pageable);
}
