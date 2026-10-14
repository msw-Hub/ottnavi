package com.ottnavi.provider.service;

import com.ottnavi.global.error.FieldValidationException;
import com.ottnavi.global.error.ResourceNotFoundException;
import com.ottnavi.provider.domain.entity.OttService;
import com.ottnavi.provider.domain.entity.TmdbProvider;
import com.ottnavi.provider.domain.enums.MappingState;
import com.ottnavi.provider.domain.enums.ProviderTier;
import com.ottnavi.provider.domain.repository.OttServiceRepository;
import com.ottnavi.provider.domain.repository.TmdbProviderRepository;
import com.ottnavi.provider.service.model.AdminProviderView;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자의 TMDB 제공처 매핑 조회·변경(Task 046). 변경은 다음 수집부터 판정에 반영된다 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminProviderService {

	private final TmdbProviderRepository tmdbProviderRepository;
	private final OttServiceRepository ottServiceRepository;

	/** 제공처 전체를 미매핑 먼저, 그다음 제공처 번호 순으로 돌려준다 */
	public List<AdminProviderView> listProviders() {
		List<TmdbProvider> providers = tmdbProviderRepository.findAllForAdmin();
		List<AdminProviderView> views = new ArrayList<>();
		for (TmdbProvider provider : providers) {
			views.add(AdminProviderView.from(provider));
		}
		return views;
	}

	/**
	 * 제공처 한 곳의 매핑을 저장하고 바뀐 제공처를 돌려준다.
	 * MAPPED면 존재하는 서비스가 필수이고, UNMAPPED·IGNORED면 서비스는 null이어야 한다(tier는 STANDARD로 저장).
	 */
	@Transactional
	public AdminProviderView updateProvider(
			long providerId, MappingState mappingState, Long ottServiceId, ProviderTier tier) {
		// 1. 상태와 서비스 연결이 맞는지 확인한다
		if (mappingState == MappingState.MAPPED && ottServiceId == null) {
			throw new FieldValidationException("ottServiceId", "MAPPED이면 서비스를 선택해야 합니다.");
		}
		if (mappingState != MappingState.MAPPED && ottServiceId != null) {
			throw new FieldValidationException("ottServiceId", "MAPPED가 아니면 서비스를 연결할 수 없습니다.");
		}

		// 2. 제공처를 찾는다(PK가 int 범위라 그보다 큰 값은 없는 제공처다)
		TmdbProvider provider = null;
		if (providerId <= Integer.MAX_VALUE) {
			Optional<TmdbProvider> found = tmdbProviderRepository.findById((int) providerId);
			if (found.isPresent()) {
				provider = found.get();
			}
		}
		if (provider == null) {
			throw new ResourceNotFoundException("해당 제공처를 찾을 수 없습니다.");
		}

		// 3. 연결할 서비스를 찾는다
		OttService ottService = null;
		if (ottServiceId != null) {
			Optional<OttService> found = ottServiceRepository.findById(ottServiceId);
			if (found.isEmpty()) {
				throw new FieldValidationException("ottServiceId", "존재하지 않는 서비스입니다.");
			}
			ottService = found.get();
		}

		// 4. 매핑을 바꾼다(영속 상태라 더티 체킹으로 커밋 때 UPDATE 된다)
		provider.changeMapping(mappingState, ottService, tier);
		return AdminProviderView.from(provider);
	}
}
