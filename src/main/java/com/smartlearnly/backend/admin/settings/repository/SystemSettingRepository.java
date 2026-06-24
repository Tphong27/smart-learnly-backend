package com.smartlearnly.backend.admin.settings.repository;

import com.smartlearnly.backend.admin.settings.entity.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, String> {
}
