package com.ottnavi.catalog.domain.repository;

import com.ottnavi.catalog.domain.entity.TitleGenre;
import com.ottnavi.catalog.domain.entity.TitleGenreId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TitleGenreRepository extends JpaRepository<TitleGenre, TitleGenreId> {
}
