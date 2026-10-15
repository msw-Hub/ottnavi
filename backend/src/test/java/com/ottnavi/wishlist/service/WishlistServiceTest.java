package com.ottnavi.wishlist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ottnavi.catalog.domain.enums.MediaType;
import com.ottnavi.catalog.service.WatchUnitQueryService;
import com.ottnavi.global.error.FieldValidationException;
import com.ottnavi.user.service.UserQueryService;
import com.ottnavi.wishlist.domain.entity.WishlistItem;
import com.ottnavi.wishlist.domain.enums.WishlistPriority;
import com.ottnavi.wishlist.domain.repository.WishlistItemRepository;
import com.ottnavi.wishlist.exception.WishlistItemNotFoundException;
import com.ottnavi.wishlist.exception.WishlistLimitExceededException;
import com.ottnavi.wishlist.service.model.AddWishlistResult;
import com.ottnavi.wishlist.service.model.UpdatedWishlistItemView;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 찜 상한·중복 판정을 DB 없이 확인한다.
 * 막으려는 사고: 상한(20개)을 넘겨 찜이 저장되는 일, 이미 찜한 작품 추가가 상한 때문에 409가 되는 일,
 * "다 봤음"을 되돌릴 때 상한을 넘기는 일, 이미 "다 봤음"인 항목의 시각이 덮어써지는 일.
 */
class WishlistServiceTest {

	private static final Long USER_ID = 7L;
	private static final long WATCH_UNIT_ID = 100L;

	private WishlistItemRepository wishlistItemRepository;
	private WatchUnitQueryService watchUnitQueryService;
	private UserQueryService userQueryService;
	private WishlistService service;

	@BeforeEach
	void setUp() {
		wishlistItemRepository = mock(WishlistItemRepository.class);
		watchUnitQueryService = mock(WatchUnitQueryService.class);
		userQueryService = mock(UserQueryService.class);
		service = new WishlistService(wishlistItemRepository, watchUnitQueryService, userQueryService);

		when(watchUnitQueryService.getWatchUnitId(MediaType.MOVIE, 496243, null)).thenReturn(WATCH_UNIT_ID);
		when(wishlistItemRepository.findByUserIdAndWatchUnitId(USER_ID, WATCH_UNIT_ID)).thenReturn(Optional.empty());
		when(wishlistItemRepository.save(any(WishlistItem.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
	}

	@Test
	void 활성_찜이_19개면_새로_추가된다() {
		// given
		when(wishlistItemRepository.countByUserIdAndWatchedAtIsNull(USER_ID)).thenReturn(19L);

		// when
		AddWishlistResult result = service.add(USER_ID, MediaType.MOVIE, 496243, null, WishlistPriority.MUST);

		// then
		assertThat(result.isCreated()).isTrue();
		verify(userQueryService).lockUser(USER_ID);
		verify(wishlistItemRepository).save(any(WishlistItem.class));
	}

	@Test
	void 활성_찜이_20개면_새_추가는_409다() {
		// given
		when(wishlistItemRepository.countByUserIdAndWatchedAtIsNull(USER_ID)).thenReturn(20L);

		// when & then
		assertThatThrownBy(() -> service.add(USER_ID, MediaType.MOVIE, 496243, null, WishlistPriority.MUST))
				.isInstanceOf(WishlistLimitExceededException.class);
		verify(wishlistItemRepository, never()).save(any(WishlistItem.class));
	}

	@Test
	void 이미_찜한_단위는_상한이어도_기존_항목으로_돌려준다() {
		// given
		WishlistItem existing = withId(WishlistItem.create(USER_ID, WATCH_UNIT_ID, WishlistPriority.WANT));
		when(wishlistItemRepository.findByUserIdAndWatchUnitId(USER_ID, WATCH_UNIT_ID)).thenReturn(Optional.of(existing));
		when(wishlistItemRepository.countByUserIdAndWatchedAtIsNull(USER_ID)).thenReturn(20L);

		// when
		AddWishlistResult result = service.add(USER_ID, MediaType.MOVIE, 496243, null, WishlistPriority.MUST);

		// then
		assertThat(result.isCreated()).isFalse();
		assertThat(existing.getPriority()).isEqualTo(WishlistPriority.WANT);
		verify(wishlistItemRepository, never()).save(any(WishlistItem.class));
	}

	@Test
	void 드라마인데_시즌_번호가_없으면_400이다() {
		// when & then
		assertThatThrownBy(() -> service.add(USER_ID, MediaType.TV, 93405, null, WishlistPriority.MUST))
				.isInstanceOf(FieldValidationException.class);
	}

	@Test
	void 영화인데_시즌_번호가_있으면_400이다() {
		// when & then
		assertThatThrownBy(() -> service.add(USER_ID, MediaType.MOVIE, 496243, 1, WishlistPriority.MUST))
				.isInstanceOf(FieldValidationException.class);
	}

	@Test
	void 다_봤음_되돌리기는_활성_찜이_20개면_409다() {
		// given
		WishlistItem watched = watchedItem();
		when(wishlistItemRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(watched));
		when(wishlistItemRepository.countByUserIdAndWatchedAtIsNull(USER_ID)).thenReturn(20L);

		// when & then
		assertThatThrownBy(() -> service.update(USER_ID, 5L, null, false))
				.isInstanceOf(WishlistLimitExceededException.class);
		assertThat(watched.getWatchedAt()).isNotNull();
	}

	@Test
	void 이미_안_본_항목을_되돌려도_상한을_검사하지_않는다() {
		// given
		WishlistItem active = withId(WishlistItem.create(USER_ID, WATCH_UNIT_ID, WishlistPriority.MUST));
		when(wishlistItemRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(active));

		// when
		UpdatedWishlistItemView result = service.update(USER_ID, 5L, null, false);

		// then
		assertThat(result.watchedAt()).isNull();
		verify(wishlistItemRepository, never()).countByUserIdAndWatchedAtIsNull(any());
	}

	@Test
	void 이미_다_봤음인_항목에_true를_보내도_처음_시각을_유지한다() {
		// given
		WishlistItem watched = watchedItem();
		Instant first = watched.getWatchedAt();
		when(wishlistItemRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(watched));

		// when
		UpdatedWishlistItemView result = service.update(USER_ID, 5L, null, true);

		// then
		assertThat(result.watchedAt()).isEqualTo(first);
	}

	@Test
	void 본인_것이_아니거나_없는_항목_변경은_404다() {
		// given
		when(wishlistItemRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> service.update(USER_ID, 5L, WishlistPriority.WANT, null))
				.isInstanceOf(WishlistItemNotFoundException.class);
	}

	@Test
	void 바꿀_값이_하나도_없으면_400이다() {
		// when & then
		assertThatThrownBy(() -> service.update(USER_ID, 5L, null, null))
				.isInstanceOf(FieldValidationException.class);
	}

	private WishlistItem watchedItem() {
		WishlistItem item = withId(WishlistItem.create(USER_ID, WATCH_UNIT_ID, WishlistPriority.MUST));
		item.markWatched(Instant.parse("2026-10-10T12:30:00Z"));
		return item;
	}

	/** DB가 채우는 ID를 단위 테스트에서 흉내 낸다 */
	private WishlistItem withId(WishlistItem item) {
		ReflectionTestUtils.setField(item, "id", 5L);
		return item;
	}
}
