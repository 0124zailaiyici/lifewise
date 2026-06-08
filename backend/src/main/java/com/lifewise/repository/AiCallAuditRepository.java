package com.lifewise.repository;

import com.lifewise.entity.AiCallAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.LocalDateTime;

public interface AiCallAuditRepository extends JpaRepository<AiCallAudit, Long> {
    List<AiCallAudit> findTop30ByOrderByCreatedAtDesc();
    List<AiCallAudit> findTop30ByUserIdOrderByCreatedAtDesc(Long userId);
    List<AiCallAudit> findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(Long userId, LocalDateTime createdAt);
    void deleteByUserId(Long userId);
}
