package com.ottnavi.provider.service;

import com.ottnavi.provider.domain.entity.OttService;
import com.ottnavi.provider.domain.repository.OttServiceRepository;
import java.util.List;
import java.util.Optional;
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
	public List<OttService> listOttServices() {
		return ottServiceRepository.findAllByOrderByDisplayOrderAsc();
	}

	/** 서비스 한 곳을 ID로 찾는다. 없으면 빈 값이다 */
	public Optional<OttService> findById(long ottServiceId) {
		return ottServiceRepository.findById(ottServiceId);
	}
}
