package com.ottnavi.wishlist.service;

import com.ottnavi.catalog.domain.enums.AvailabilityStatus;
import com.ottnavi.catalog.domain.enums.MediaType;
import com.ottnavi.catalog.service.WatchUnitQueryService;
import com.ottnavi.catalog.service.model.ServiceStatus;
import com.ottnavi.catalog.service.model.WatchUnitSummaries;
import com.ottnavi.catalog.service.model.WatchUnitSummary;
import com.ottnavi.global.error.FieldValidationException;
import com.ottnavi.user.service.UserQueryService;
import com.ottnavi.wishlist.domain.entity.WishlistItem;
import com.ottnavi.wishlist.domain.enums.WishlistPriority;
import com.ottnavi.wishlist.domain.repository.WishlistItemRepository;
import com.ottnavi.wishlist.exception.WishlistItemNotFoundException;
import com.ottnavi.wishlist.exception.WishlistLimitExceededException;
import com.ottnavi.wishlist.service.model.ActiveWishlistEntry;
import com.ottnavi.wishlist.service.model.AddWishlistResult;
import com.ottnavi.wishlist.service.model.UpdatedWishlistItemView;
import com.ottnavi.wishlist.service.model.WishlistItemView;
import com.ottnavi.wishlist.service.model.WishlistView;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 찜 목록 조회·추가·변경·삭제(Task 056). 사용자당 "다 봤음"이 아닌 찜은 최대 {@value #MAX_ACTIVE_ITEM_COUNT}개다(PRD 11절 36번).
 * 개수 상한은 DB 제약이 아니라 서비스 규칙이라, 개수를 세고 저장하는 두 동작(새 찜 추가, "다 봤음" 되돌리기)은
 * 사용자 행을 FOR UPDATE로 잠가 같은 사용자의 요청을 한 줄로 세운 뒤 판정한다.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class WishlistService {

	/** 사용자당 "다 봤음"이 아닌 찜의 최대 개수. 계산 엔진의 상품 수 상한(MAX_PRODUCT_COUNT)과는 별개다 */
	public static final int MAX_ACTIVE_ITEM_COUNT = 20;

	private final WishlistItemRepository wishlistItemRepository;
	private final WatchUnitQueryService watchUnitQueryService;
	private final UserQueryService userQueryService;

	/**
	 * 내 찜 전부를 최근에 찜한 순으로 돌려준다. "다 봤음" 항목도 포함하되 activeItemCount에는 세지 않는다.
	 * 시청 단위·제공 상태는 한 번에 읽어 N+1을 피한다.
	 */
	public WishlistView list(Long userId) {
		// 1. 내 찜을 최근 순으로 읽는다
		List<WishlistItem> items = wishlistItemRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId);

		// 2. 찜한 시청 단위의 요약·제공 상태·기준일을 일괄 조회
		List<Long> unitIds = new ArrayList<>();
		for (WishlistItem item : items) {
			unitIds.add(item.getWatchUnitId());
		}
		WatchUnitSummaries summaries = watchUnitQueryService.loadSummaries(unitIds);

		// 3. 지금 이용할 수 있는 서비스(구독 중·무료 이용). 찜이 없으면 조회하지 않는다
		Set<Long> usableServiceIds = Set.of();
		if (!items.isEmpty()) {
			usableServiceIds = userQueryService.listUsableServiceIds(userId);
		}

		// 4. 찜마다 응답 한 줄을 만들고 활성 찜 개수를 센다
		List<WishlistItemView> views = new ArrayList<>();
		int activeItemCount = 0;
		for (WishlistItem item : items) {
			if (item.isActive()) {
				activeItemCount++;
			}
			WatchUnitSummary summary = summaries.byUnitId().get(item.getWatchUnitId());
			if (summary == null) {
				// FK가 있어 일어나지 않아야 한다. 일어나면 그 줄만 건너뛰고 알린다
				log.warn("찜의 시청 단위를 찾지 못해 목록에서 뺀다: wishlistItemId={}, watchUnitId={}",
						item.getId(), item.getWatchUnitId());
				continue;
			}
			views.add(toView(item, summary, usableServiceIds));
		}
		return new WishlistView(views, activeItemCount, MAX_ACTIVE_ITEM_COUNT, summaries.dataAsOf());
	}

	/** 플랜 계산 입력용으로 "다 봤음"이 아닌 찜을 ID 순으로 돌려준다. 다른 도메인이 찜 리포지토리를 직접 쓰지 않게 하는 창구다 */
	public List<ActiveWishlistEntry> listActiveEntries(Long userId) {
		List<ActiveWishlistEntry> result = new ArrayList<>();
		for (WishlistItem item : wishlistItemRepository.findAllByUserIdAndWatchedAtIsNullOrderByIdAsc(userId)) {
			result.add(new ActiveWishlistEntry(item.getId(), item.getWatchUnitId(), item.getPriority()));
		}
		return result;
	}

	/**
	 * 작품(드라마는 시즌)을 찜한다. 이미 찜한 단위면 기존 항목을 그대로 돌려주고(상한과 무관, 우선순위·"다 봤음" 불변),
	 * 새로 만들 때만 활성 찜이 상한이면 막는다.
	 */
	@Transactional
	public AddWishlistResult add(Long userId, MediaType mediaType, int tmdbId, Integer seasonNumber,
			WishlistPriority priority) {
		// 1. 영화는 시즌 번호가 없고 드라마는 시즌 번호가 필수다
		if (mediaType == MediaType.MOVIE && seasonNumber != null) {
			throw new FieldValidationException("seasonNumber", "영화는 시즌 번호를 보낼 수 없습니다.");
		}
		if (mediaType == MediaType.TV && seasonNumber == null) {
			throw new FieldValidationException("seasonNumber", "드라마는 시즌 번호가 필요합니다.");
		}

		// 2. 작품·시즌이 DB에 있는지 확인(TMDB는 호출하지 않는다). 없으면 TITLE_NOT_FOUND
		long watchUnitId = watchUnitQueryService.getWatchUnitId(mediaType, tmdbId, seasonNumber);

		// 3. 사용자 행을 잠가 같은 사용자의 추가·되돌리기를 한 줄로 세운다
		userQueryService.lockUser(userId);

		// 4. 이미 찜한 단위면 새로 만들지 않고 기존 항목을 돌려준다(중복 추가 무시)
		Optional<WishlistItem> existing = wishlistItemRepository.findByUserIdAndWatchUnitId(userId, watchUnitId);
		if (existing.isPresent()) {
			return new AddWishlistResult(existing.get().getId(), false);
		}

		// 5. 활성 찜이 상한이면 막는다
		long activeCount = wishlistItemRepository.countByUserIdAndWatchedAtIsNull(userId);
		if (activeCount >= MAX_ACTIVE_ITEM_COUNT) {
			throw new WishlistLimitExceededException("찜은 " + MAX_ACTIVE_ITEM_COUNT + "개까지 담을 수 있습니다.");
		}

		// 6. 새 찜을 저장한다
		WishlistItem saved = wishlistItemRepository.save(WishlistItem.create(userId, watchUnitId, priority));
		log.info("찜 추가: userId={}, wishlistItemId={}, watchUnitId={}", userId, saved.getId(), watchUnitId);
		return new AddWishlistResult(saved.getId(), true);
	}

	/**
	 * 찜의 우선순위·"다 봤음"을 바꾼다. 보낸 값만 바꾸고 둘 다 없으면 400이다.
	 * "다 봤음"을 되돌릴 때만 개수 상한이 걸리므로 그때만 사용자 행을 잠근다.
	 */
	@Transactional
	public UpdatedWishlistItemView update(Long userId, Long wishlistItemId, WishlistPriority priority,
			Boolean isWatched) {
		// 1. 바꿀 값이 하나도 없으면 400
		if (priority == null && isWatched == null) {
			throw new FieldValidationException("isWatched", "priority 또는 isWatched 중 하나는 보내야 합니다.");
		}

		// 2. 되돌리기는 개수를 세고 저장하므로 먼저 사용자 행을 잠가 직렬화한다
		boolean isRestore = Boolean.FALSE.equals(isWatched);
		if (isRestore) {
			userQueryService.lockUser(userId);
		}

		// 3. 본인의 찜만 찾는다. 없거나 타인의 것이면 404
		Optional<WishlistItem> found = wishlistItemRepository.findByIdAndUserId(wishlistItemId, userId);
		if (found.isEmpty()) {
			throw new WishlistItemNotFoundException("찜 항목을 찾을 수 없습니다: " + wishlistItemId);
		}
		WishlistItem item = found.get();

		// 4. 우선순위 변경(더티 체킹으로 트랜잭션이 끝날 때 UPDATE된다)
		if (priority != null) {
			item.changePriority(priority);
		}

		// 5. "다 봤음" 표시 또는 되돌리기
		if (Boolean.TRUE.equals(isWatched)) {
			item.markWatched(Instant.now()); // 이미 표시돼 있으면 처음 시각을 유지한다
		} else if (isRestore && !item.isActive()) {
			long activeCount = wishlistItemRepository.countByUserIdAndWatchedAtIsNull(userId);
			if (activeCount >= MAX_ACTIVE_ITEM_COUNT) {
				throw new WishlistLimitExceededException("찜은 " + MAX_ACTIVE_ITEM_COUNT + "개까지 담을 수 있습니다.");
			}
			item.unmarkWatched();
		}
		return new UpdatedWishlistItemView(item.getId(), item.getPriority(), item.getWatchedAt());
	}

	/** 내 찜을 지운다. 없거나 타인의 것이어도 오류 없이 끝난다(여러 번 호출해도 결과가 같다) */
	@Transactional
	public void remove(Long userId, Long wishlistItemId) {
		int deleted = wishlistItemRepository.deleteByIdAndUserId(wishlistItemId, userId);
		log.info("찜 삭제: userId={}, wishlistItemId={}, 삭제 건수={}", userId, wishlistItemId, deleted);
	}

	/** 시청 단위 요약에 찜 정보와 "지금 볼 수 있는지"를 더해 응답 한 줄로 만든다 */
	private WishlistItemView toView(WishlistItem item, WatchUnitSummary summary, Set<Long> usableServiceIds) {
		// 내가 이용 중인 서비스 중 하나라도 AVAILABLE이면 지금 볼 수 있다(UNKNOWN 구독은 usable에서 이미 빠져 있다)
		boolean isWatchableNow = false;
		for (ServiceStatus status : summary.availabilities()) {
			if (status.status() == AvailabilityStatus.AVAILABLE && usableServiceIds.contains(status.ottServiceId())) {
				isWatchableNow = true;
				break;
			}
		}
		return new WishlistItemView(
				item.getId(),
				summary.mediaType(),
				summary.tmdbId(),
				summary.title(),
				summary.posterPath(),
				summary.seasonNumber(),
				summary.seasonName(),
				item.getPriority(),
				summary.requiredMin(),
				summary.isRuntimeEstimated(),
				summary.availabilities(),
				isWatchableNow,
				item.getWatchedAt());
	}
}
