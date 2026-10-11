package com.ottnavi.infra.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** append_to_response=credits로 붙는 출연진. 드라마는 "가장 최근 시즌" 출연진이다(4절 6번) */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbCredits(List<TmdbCastMember> cast) {
}
