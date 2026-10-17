package com.ottnavi.wishlist.domain.repository;

import com.ottnavi.wishlist.domain.entity.WishlistItem;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

	/** 사용자의 찜 전부를 최근에 찜한 순으로 조회한다(같은 시각이면 ID가 큰 것이 먼저) */
	List<WishlistItem> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId);

	/** "다 봤음"이 아닌 찜만 ID 순으로 조회한다(플랜 계산 입력) */
	List<WishlistItem> findAllByUserIdAndWatchedAtIsNullOrderByIdAsc(Long userId);

	/** since 이후(초과)에 "다 봤음"으로 표시된 찜(매월 재계산의 시청 체크 이유 판정) */
	List<WishlistItem> findAllByUserIdAndWatchedAtAfter(Long userId, Instant since);

	/** 사용자가 이미 찜한 시청 단위인지 찾는다(중복 추가 판정) */
	Optional<WishlistItem> findByUserIdAndWatchUnitId(Long userId, Long watchUnitId);

	/** 본인의 찜만 ID로 찾는다. 타인의 찜은 없는 것과 같다 */
	Optional<WishlistItem> findByIdAndUserId(Long id, Long userId);

	/** "다 봤음"이 아닌 찜의 개수(상한 20 판정). 호출 전에 사용자 행을 잠가 직렬화해야 한다 */
	long countByUserIdAndWatchedAtIsNull(Long userId);

	/** 본인의 찜을 지운다. 없거나 타인의 것이면 0건이며 오류가 아니다(삭제 API는 멱등) */
	@Modifying
	@Query("delete from WishlistItem w where w.id = :id and w.userId = :userId")
	int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

	/** 시청 단위들을 가리키는 모든 사용자의 찜을 지운다("다 봤음" 행 포함). TMDB에서 사라진 작품 정리가 쓴다 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from WishlistItem w where w.watchUnitId in :watchUnitIds")
	int deleteByWatchUnitIdIn(@Param("watchUnitIds") Collection<Long> watchUnitIds);
}
