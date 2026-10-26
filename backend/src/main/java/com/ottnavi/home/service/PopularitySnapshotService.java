package com.ottnavi.home.service;

import com.ottnavi.catalog.domain.enums.MediaType;
import com.ottnavi.home.domain.entity.PopularitySnapshot;
import com.ottnavi.home.domain.repository.PopularitySnapshotRepository;
import com.ottnavi.home.service.model.PopularityItem;
import com.ottnavi.provider.domain.entity.OttService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인기 스냅샷 저장·삭제(Task 107). DB 반영만 트랜잭션으로 묶는다. TMDB 호출은 PopularityCollectService가 이 서비스를 부르기 전에 끝낸다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PopularitySnapshotService {

	private final PopularitySnapshotRepository snapshotRepository;

	/**
	 * 한 날짜·서비스·매체의 스냅샷을 새 목록으로 통째로 바꾼다. 같은 날 다시 실행해도 같은 결과가 된다(멱등).
	 * 지우기와 넣기를 한 트랜잭션에 묶어, 중간에 실패하면 이전 스냅샷이 그대로 남는다.
	 */
	@Transactional
	public int replace(LocalDate snapshotDate, OttService ottService, MediaType mediaType, List<PopularityItem> items) {
		// 1. 같은 날짜·서비스·매체의 기존 행을 먼저 지운다(벌크 삭제 뒤 영속성 컨텍스트도 비워진다)
		snapshotRepository.deleteBySlot(snapshotDate, ottService.getId(), mediaType);

		// 2. 새 목록을 넣는다
		for (PopularityItem item : items) {
			snapshotRepository.save(PopularitySnapshot.create(snapshotDate, ottService, item.mediaType(), item.tmdbId(),
					item.title(), item.posterPath(), item.releaseDate(), item.popularity()));
		}
		return items.size();
	}

	/** 기준 날짜보다 이른 스냅샷을 지우고 지운 행 수를 돌려준다 */
	@Transactional
	public int purgeBefore(LocalDate cutoffDate) {
		return snapshotRepository.deleteBySnapshotDateBefore(cutoffDate);
	}
}
