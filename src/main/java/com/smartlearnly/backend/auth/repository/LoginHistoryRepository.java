package com.smartlearnly.backend.auth.repository;

import com.smartlearnly.backend.auth.entity.LoginHistory;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginHistoryRepository extends JpaRepository<LoginHistory, UUID> {
}
