package com.lifewise.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Service
public class AiCallAuditService {

    private static final int MAX_SIZE = 30;
    private final LinkedList<Map<String, Object>> records = new LinkedList<>();

    public synchronized void record(Long userId, String provider, String model, String scene, String status, String detail) {
        records.addFirst(Map.of(
            "time", LocalDateTime.now().toString(),
            "userId", userId != null ? userId : 0,
            "provider", provider != null ? provider : "",
            "model", model != null ? model : "",
            "scene", scene != null ? scene : "other",
            "status", status != null ? status : "",
            "detail", detail != null ? detail : ""
        ));
        while (records.size() > MAX_SIZE) {
            records.removeLast();
        }
    }

    public synchronized List<Map<String, Object>> recent() {
        return new ArrayList<>(records);
    }
}
