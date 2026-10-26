package com.ottnavi.user.domain.repository;

import com.ottnavi.user.domain.entity.User;
import com.ottnavi.user.domain.enums.OAuthProvider;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

	/** 소셜 계정의 고유 번호로 사용자를 찾는다(로그인 때 기존 가입자인지 판정) */
	Optional<User> findByOauthProviderAndOauthSubject(OAuthProvider oauthProvider, String oauthSubject);

	/** 이메일을 쓰는 사용자가 있는지 확인한다(이메일 유니크 충돌을 미리 피하려는 용도) */
	boolean existsByEmail(String email);

	/**
	 * 사용자 행을 SELECT ... FOR UPDATE로 잠그고 읽는다. 같은 사용자의 쓰기 요청을 직렬화하는 용도다
	 * (설정 전체 교체 Task 055, 찜 최대 20개 검사 Task 056). 트랜잭션 안에서 쓰기 전에 가장 먼저 호출한다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from User u where u.id = :id")
	Optional<User> findByIdForUpdate(@Param("id") Long id);

	/**
	 * 사용자 행을 벌크로 지운다(탈퇴). 하위 행은 DB의 ON DELETE CASCADE가 지우므로 JPA cascade에 기대지 않는다(TECH T-1).
	 * 영속성 컨텍스트에 남은 User를 비워 지워진 행이 다시 보이지 않게 한다.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from User u where u.id = :id")
	int deleteByUserId(@Param("id") Long id);
}
