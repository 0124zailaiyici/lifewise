package com.lifewise.service;

import com.lifewise.entity.AiCallAudit;
import com.lifewise.repository.AiCallAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiCallAuditService {

    private final AiCallAuditRepository aiCallAuditRepository;

    public void record(Long userId, String provider, String model, String scene, String status, String detail) {
        AiCallAudit audit = new AiCallAudit();
        audit.setUserId(userId != null ? userId : 0);
        audit.setProvider(safe(provider));
        audit.setModel(safe(model));
        audit.setScene(scene != null && !scene.isBlank() ? scene : "other");
        audit.setStatus(safe(status));
        audit.setDetail(safe(detail));
        aiCallAuditRepository.save(audit);
    }

    public List<Map<String, Object>> recent(Long userId) {
        return aiCallAuditRepository.findTop30ByUserIdOrderByCreatedAtDesc(userId != null ? userId : 0).stream()
            .map(audit -> Map.<String, Object>of(
                "time", audit.getCreatedAt() != null ? audit.getCreatedAt().toString() : "",
                "userId", audit.getUserId() != null ? audit.getUserId() : 0,
                "provider", safe(audit.getProvider()),
                "model", safe(audit.getModel()),
                "scene", safe(audit.getScene()),
                "status", safe(audit.getStatus()),
                "detail", safe(audit.getDetail())
            ))
            .collect(Collectors.toList());
    }

    public List<Map<String, Object>> recent() {
        return aiCallAuditRepository.findTop30ByOrderByCreatedAtDesc().stream()
            .map(audit -> Map.<String, Object>of(
                "time", audit.getCreatedAt() != null ? audit.getCreatedAt().toString() : "",
                "userId", audit.getUserId() != null ? audit.getUserId() : 0,
                "provider", safe(audit.getProvider()),
                "model", safe(audit.getModel()),
                "scene", safe(audit.getScene()),
                "status", safe(audit.getStatus()),
                "detail", safe(audit.getDetail())
            ))
            .collect(Collectors.toList());
    }

    @Transactional
    public void clear(Long userId) {
        aiCallAuditRepository.deleteByUserId(userId != null ? userId : 0);
    }

    private String safe(String value) {
        return value != null ? value : "";
    }
}
