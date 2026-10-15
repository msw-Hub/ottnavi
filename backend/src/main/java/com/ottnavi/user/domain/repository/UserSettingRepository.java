package com.ottnavi.user.domain.repository;

import com.ottnavi.user.domain.entity.UserSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSettingRepository extends JpaRepository<UserSetting, Long> {
}
