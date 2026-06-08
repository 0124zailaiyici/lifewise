package com.lifewise.repository;

import com.lifewise.entity.AiCallAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiCallAuditRepository extends JpaRepository<AiCallAudit, Long> {
    List<AiCallAudit> findTop30ByOrderByCreatedAtDesc();
}
