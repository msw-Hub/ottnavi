package com.ottnavi.product.domain.repository;

import com.ottnavi.product.domain.entity.UserPriceOverride;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserPriceOverrideRepository extends JpaRepository<UserPriceOverride, Long> {

	/** 사용자가 이 상품에 고친 금액을 찾는다 */
	Optional<UserPriceOverride> findByUserIdAndProductId(Long userId, Long productId);

	/** 사용자가 여러 상품에 고친 금액을 한 번에 조회한다(목록 화면의 N+1 방지) */
	List<UserPriceOverride> findByUserIdAndProductIdIn(Long userId, Collection<Long> productIds);

	/** 본인이 고친 금액을 지운다. 없으면 0건이며 오류가 아니다(삭제 API는 멱등) */
	@Modifying
	@Query("delete from UserPriceOverride o where o.userId = :userId and o.productId = :productId")
	int deleteByUserIdAndProductId(@Param("userId") Long userId, @Param("productId") Long productId);
}
