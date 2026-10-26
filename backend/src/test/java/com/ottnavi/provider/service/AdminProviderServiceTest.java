package com.ottnavi.provider.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ottnavi.global.error.FieldValidationException;
import com.ottnavi.global.error.ResourceNotFoundException;
import com.ottnavi.provider.domain.entity.OttService;
import com.ottnavi.provider.domain.entity.TmdbProvider;
import com.ottnavi.provider.domain.enums.MappingState;
import com.ottnavi.provider.domain.enums.ProviderTier;
import com.ottnavi.provider.domain.repository.OttServiceRepository;
import com.ottnavi.provider.domain.repository.TmdbProviderRepository;
import com.ottnavi.provider.service.model.AdminProviderView;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdminProviderServiceTest {

	private final TmdbProviderRepository providerRepository = mock(TmdbProviderRepository.class);
	private final OttServiceRepository ottServiceRepository = mock(OttServiceRepository.class);
	private final AdminProviderService service = new AdminProviderService(providerRepository, ottServiceRepository);

	private TmdbProvider unmapped() {
		return TmdbProvider.discover(1881, "Coupang Play", "/logo.png", Instant.parse("2026-03-26T00:00:00Z"));
	}

	@Test
	void MAPPED인데_서비스가_없으면_ottServiceId_필드_오류다() {
		// given
		TmdbProvider provider = unmapped();
		when(providerRepository.findById(1881)).thenReturn(Optional.of(provider));

		// when
		// then
		assertThatThrownBy(() -> service.updateProvider(1881, MappingState.MAPPED, null, ProviderTier.STANDARD))
				.isInstanceOf(FieldValidationException.class)
				.extracting("field").isEqualTo("ottServiceId");
		assertThat(provider.getMappingState()).isEqualTo(MappingState.UNMAPPED);
	}

	@Test
	void IGNORED인데_서비스를_주면_ottServiceId_필드_오류다() {
		// given
		// when
		// then
		assertThatThrownBy(() -> service.updateProvider(1881, MappingState.IGNORED, 7L, ProviderTier.STANDARD))
				.isInstanceOf(FieldValidationException.class)
				.extracting("field").isEqualTo("ottServiceId");
	}

	@Test
	void 없는_서비스를_연결하면_ottServiceId_필드_오류다() {
		// given
		when(providerRepository.findById(1881)).thenReturn(Optional.of(unmapped()));
		when(ottServiceRepository.findById(99L)).thenReturn(Optional.empty());

		// when
		// then
		assertThatThrownBy(() -> service.updateProvider(1881, MappingState.MAPPED, 99L, ProviderTier.STANDARD))
				.isInstanceOf(FieldValidationException.class)
				.extracting("field").isEqualTo("ottServiceId");
	}

	@Test
	void 없는_제공처면_ResourceNotFoundException이다() {
		// given
		when(providerRepository.findById(4040)).thenReturn(Optional.empty());

		// when
		// then
		assertThatThrownBy(() -> service.updateProvider(4040, MappingState.UNMAPPED, null, ProviderTier.STANDARD))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void MAPPED에서_IGNORED로_바꾸면_서비스를_끊고_tier를_STANDARD로_돌린다() {
		// given
		TmdbProvider provider = unmapped();
		OttService ottService = mock(OttService.class);
		when(ottService.getId()).thenReturn(7L);
		provider.mapTo(ottService, ProviderTier.AD);
		when(providerRepository.findById(1881)).thenReturn(Optional.of(provider));

		// when
		AdminProviderView response = service.updateProvider(1881, MappingState.IGNORED, null, ProviderTier.AD);

		// then
		assertThat(response.mappingState()).isEqualTo(MappingState.IGNORED);
		assertThat(response.ottServiceId()).isNull();
		assertThat(response.tier()).isEqualTo(ProviderTier.STANDARD);
	}
}
