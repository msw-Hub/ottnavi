package com.ottnavi.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * TMDB 제공처 목록(계약 AdminProviderList).
 *
 * @param providers 제공처 전체(미매핑 먼저, 그다음 제공처 번호 순)
 */
public record AdminProviderListResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<AdminProviderResponse> providers) {
}
