package com.ottnavi.wishlist.domain.entity;

import com.ottnavi.global.common.BaseTimeEntity;
import com.ottnavi.wishlist.domain.enums.WishlistPriority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 찜 한 줄(ERD wishlist_item). 영화 1편 또는 드라마 시즌 1개(watch_unit) 단위다.
 * 사용자·시청 단위는 다른 도메인의 엔티티라 연관관계 대신 ID만 들고, 필요한 정보는 서비스를 통해 읽는다.
 */
@Getter
@Entity
@Table(name = "wishlist_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WishlistItem extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long userId; // 찜한 사용자

	@Column(nullable = false)
	private Long watchUnitId; // 시청 단위: 영화 1편 또는 시즌 1개

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private WishlistPriority priority; // MUST / WANT / MAYBE

	@JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
	private Instant watchedAt; // "다 봤음" 표시 시각. 있으면 계산과 개수 상한에서 빠진다

	/** 안 본 상태의 찜을 만든다 */
	public static WishlistItem create(Long userId, Long watchUnitId, WishlistPriority priority) {
		WishlistItem item = new WishlistItem();
		item.userId = userId;
		item.watchUnitId = watchUnitId;
		item.priority = priority;
		return item;
	}

	/** 우선순위를 바꾼다 */
	public void changePriority(WishlistPriority priority) {
		this.priority = priority;
	}

	/** "다 봤음"으로 표시한다. 이미 표시돼 있으면 처음 시각을 유지한다 */
	public void markWatched(Instant now) {
		if (this.watchedAt == null) {
			this.watchedAt = now;
		}
	}

	/** "다 봤음"을 되돌려 다시 계산 대상으로 만든다 */
	public void unmarkWatched() {
		this.watchedAt = null;
	}

	/** "다 봤음"이 아닌, 개수 상한에 세는 찜인지 */
	public boolean isActive() {
		return watchedAt == null;
	}
}
