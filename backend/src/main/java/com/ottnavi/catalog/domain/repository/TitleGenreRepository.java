package com.ottnavi.catalog.domain.repository;

import com.ottnavi.catalog.domain.entity.TitleGenre;
import com.ottnavi.catalog.domain.entity.TitleGenreId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TitleGenreRepository extends JpaRepository<TitleGenre, TitleGenreId> {
    /** 작품의 장르 연결을 벌크로 지운다. 작품 삭제 정리가 쓴다 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from TitleGenre tg where tg.title.id = :titleId")
    int deleteByTitleId(@Param("titleId") Long titleId);

    /** 작품에 연결된 장르 연결 행 전부를 조회한다 */
    List<TitleGenre> findByTitleId(Long titleId);

    /** 조회 API용: 장르 이름을 함께 가져와 장르 ID 순으로 조회한다 */
    @Query("select tg from TitleGenre tg join fetch tg.genre g where tg.title.id = :titleId order by g.id asc")
    List<TitleGenre> findWithGenreByTitleId(@Param("titleId") Long titleId);
}
