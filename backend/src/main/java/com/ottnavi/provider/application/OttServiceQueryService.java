package com.ottnavi.provider.application;

import com.ottnavi.provider.api.dto.response.OttServiceResponse;
import com.ottnavi.provider.domain.OttServiceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** OTT 서비스 조회 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OttServiceQueryService {

	private final OttServiceRepository ottServiceRepository;

	/** 서비스 목록을 화면 정렬 순서대로 조회한다 */
	public List<OttServiceResponse> listOttServices() {
		return ottServiceRepository.findAllByOrderByDisplayOrderAsc().stream()
				.map(OttServiceResponse::from)
				.toList();
	}
}
