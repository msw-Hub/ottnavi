package com.ottnavi.catalog.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TitleRepository extends JpaRepository<Title, Long> {

    /** 종류와 TMDB ID로 작품을 찾는다. 수집이 이미 있는 작품인지 확인할 때 쓴다 */
    Optional<Title> findByMediaTypeAndTmdbId(MediaType mediaType, Integer tmdbId);
}
