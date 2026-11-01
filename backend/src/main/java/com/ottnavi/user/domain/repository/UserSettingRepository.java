package com.ottnavi.user.domain.repository;

import com.ottnavi.user.domain.entity.UserSetting;
import com.ottnavi.user.domain.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSettingRepository extends JpaRepository<UserSetting, Long> {

	/** 해당 권한의 회원 중 알림을 켠 수. 설정 행이 없는 회원은 켠 것으로 세지 않는다(users와 같은 도메인 안의 조인) */
	@Query("select count(s) from UserSetting s join User u on u.id = s.userId "
			+ "where s.notificationEnabled = true and u.role = :role")
	long countNotificationEnabledByRole(@Param("role") UserRole role);
}
