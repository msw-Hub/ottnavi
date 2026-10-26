package com.ottnavi.infra.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 장르 (상세 응답의 genres[]) */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbGenre(int id, String name) {
}
