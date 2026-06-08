package com.lifewise.service;

import com.lifewise.entity.AiCallAudit;
import com.lifewise.repository.AiCallAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.time.LocalDateTime;
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

    public Map<String, Object> todayStats(Long userId) {
        Long uid = userId != null ? userId : 0;
        LocalDateTime start = LocalDateTime.now().toLocalDate().atStartOfDay();
        List<AiCallAudit> today = aiCallAuditRepository.findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(uid, start);

        long cacheHits = today.stream()
            .filter(a -> "cache".equals(safe(a.getProvider())) || "hit".equals(safe(a.getStatus())))
            .count();
        long blocked = today.stream()
            .filter(a -> "blocked".equals(safe(a.getStatus())))
            .count();
        long externalCalls = today.stream()
            .filter(this::isExternalCall)
            .count();
        String latestExternalAt = today.stream()
            .filter(this::isExternalCall)
            .map(AiCallAudit::getCreatedAt)
            .filter(t -> t != null)
            .findFirst()
            .map(LocalDateTime::toString)
            .orElse("");

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("date", start.toLocalDate().toString());
        stats.put("total", today.size());
        stats.put("externalCalls", externalCalls);
        stats.put("cacheHits", cacheHits);
        stats.put("blocked", blocked);
        stats.put("latestExternalAt", latestExternalAt);
        return stats;
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

    private boolean isExternalCall(AiCallAudit audit) {
        String status = safe(audit.getStatus());
        String provider = safe(audit.getProvider());
        return "calling".equals(status) && !("cache".equals(provider) || "ollama".equals(provider));
    }
}
