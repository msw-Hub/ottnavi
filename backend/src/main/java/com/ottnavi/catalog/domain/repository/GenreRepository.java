package com.ottnavi.catalog.domain.repository;

import com.ottnavi.catalog.domain.entity.Genre;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GenreRepository extends JpaRepository<Genre, Integer>  {
}
